export const MESSAGE_FOLD_THRESHOLD = 120

export type ProcessEventKind = 'agent' | 'llm' | 'tool'

/** Ordered chat-process row from AD-4 progress events. */
export type ProcessEvent = {
  id: string
  kind: ProcessEventKind
  /** agent.start | message | raw tool id | agent.end */
  title: string
  at: number
  done?: boolean
  body?: string
}

/** @deprecated Prefer ProcessEvent; kept for older call sites. */
export type ProgressStep = { id: string; label: string; done: boolean; at: number }

const TOOL_DISPLAY_NAMES: Record<string, string> = {
  read_skill: '读取技能说明',
  echo: '执行辅助工具',
  tool: '执行工具',
}

/** Wire / English process titles → Chinese UI copy. */
const PROCESS_DISPLAY_NAMES: Record<string, string> = {
  'agent.start': '开始执行',
  'agent.end': '执行结束',
  message: '模型输出',
}

export function toolEventLabel(data: Record<string, unknown>): string {
  for (const key of ['toolName', 'name', 'tool', 'payload'] as const) {
    const v = data[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return 'tool'
}

/** Human-readable step label; unknown tool ids stay as-is (not scene copy). */
export function toolDisplayLabel(raw: string): string {
  const key = raw.trim()
  return TOOL_DISPLAY_NAMES[key] ?? key
}

/** Display title for AGENT / LLM / TOOL process rows. */
export function processEventDisplayLabel(raw: string): string {
  const key = raw.trim()
  return PROCESS_DISPLAY_NAMES[key] ?? toolDisplayLabel(key)
}

/** HH:mm:ss for event-log rows (local time). */
export function formatEventTime(at: number): string {
  const d = new Date(at)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function agentLabel(data: Record<string, unknown>, fallback: string): string {
  const label = data.label
  return typeof label === 'string' && label.trim() ? label.trim() : fallback
}

export function applyAgentStarted(events: ProcessEvent[], data: Record<string, unknown> = {}): ProcessEvent[] {
  return [
    ...events,
    {
      id: `agent-start-${events.length}`,
      kind: 'agent',
      title: agentLabel(data, 'agent.start'),
      at: Date.now(),
    },
  ]
}

export function applyAgentEnded(events: ProcessEvent[], data: Record<string, unknown> = {}): ProcessEvent[] {
  return [
    ...events,
    {
      id: `agent-end-${events.length}`,
      kind: 'agent',
      title: agentLabel(data, 'agent.end'),
      at: Date.now(),
    },
  ]
}

export function applyToolStarted(events: ProcessEvent[], data: Record<string, unknown>): ProcessEvent[] {
  const label = toolEventLabel(data)
  const id =
    typeof data.toolCallId === 'string' && data.toolCallId.trim()
      ? data.toolCallId.trim()
      : `tool-${events.length}-${label}`
  return [
    ...events,
    {
      id,
      kind: 'tool',
      title: label,
      done: false,
      at: Date.now(),
    },
  ]
}

export function applyToolFinished(events: ProcessEvent[], data: Record<string, unknown>): ProcessEvent[] {
  const label = toolEventLabel(data)
  const callId = typeof data.toolCallId === 'string' ? data.toolCallId.trim() : ''
  let matched = false
  return events.map((e) => {
    if (matched || e.kind !== 'tool') return e
    if ((callId && e.id === callId) || (!callId && e.title === label && !e.done)) {
      matched = true
      return { ...e, done: true }
    }
    return e
  })
}

export function applyMessageDelta(events: ProcessEvent[], data: Record<string, unknown>): ProcessEvent[] {
  const chunk =
    (typeof data.text === 'string' && data.text) ||
    (typeof data.delta === 'string' && data.delta) ||
    (typeof data.payload === 'string' && data.payload) ||
    ''
  if (!chunk) return events

  const idx = events.findIndex((e) => e.kind === 'llm')
  if (idx < 0) {
    return [
      ...events,
      {
        id: `message-${events.length}`,
        kind: 'llm',
        title: 'message',
        at: Date.now(),
        body: chunk,
      },
    ]
  }
  return events.map((e, i) => (i === idx ? { ...e, body: (e.body || '') + chunk } : e))
}

export function appendMessageDelta(prev: string, data: Record<string, unknown>): string {
  const chunk =
    (typeof data.text === 'string' && data.text) ||
    (typeof data.delta === 'string' && data.delta) ||
    (typeof data.payload === 'string' && data.payload) ||
    ''
  return prev + chunk
}

export function foldPreview(text: string, threshold = MESSAGE_FOLD_THRESHOLD): { needsFold: boolean; preview: string } {
  if (text.length <= threshold) return { needsFold: false, preview: text }
  return { needsFold: true, preview: text.slice(0, threshold) + '…' }
}

function stripTrailingJsonObject(text: string): string {
  for (let i = text.lastIndexOf('{'); i >= 0; i = text.lastIndexOf('{', i - 1)) {
    const suffix = text.slice(i).trim()
    try {
      JSON.parse(suffix)
      return text.slice(0, i)
    } catch {
      if (i === 0) break
    }
  }
  return text
}

/**
 * Process-area copy: drop fenced / trailing artifact JSON so the chat does not dump deliverables.
 */
export function processStreamText(raw: string): string {
  let text = raw
    .replace(/```json[\s\S]*?```/gi, '')
    .replace(/```[\s\S]*?```/g, '')
    // Model often opens ```json without a closing fence
    .replace(/```json[\s\S]*$/gi, '')
    .replace(/```[\s\S]*$/g, '')
  text = stripTrailingJsonObject(text).trim()
  // Last resort: leftover artifact markers (unclosed / non-trailing)
  const marker = text.search(/"templateId"\s*:|"items"\s*:\s*\[/)
  if (marker >= 0) {
    const before = text.slice(0, marker)
    const brace = before.lastIndexOf('{')
    text = (brace >= 0 ? before.slice(0, brace) : before).trim()
  }
  return text
}
