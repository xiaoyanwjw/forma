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
  /** LLM stream text, or tool_finished output/error */
  body?: string
  /** tool_finished success flag when known */
  success?: boolean
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
  const body = toolResultBody(data)
  const success = typeof data.success === 'boolean' ? data.success : body ? !data.error : undefined
  let matched = false
  return events.map((e) => {
    if (matched || e.kind !== 'tool') return e
    if ((callId && e.id === callId) || (!callId && e.title === label && !e.done)) {
      matched = true
      return {
        ...e,
        done: true,
        ...(body ? { body } : {}),
        ...(success !== undefined ? { success } : {}),
      }
    }
    return e
  })
}

/** Prefer output; fall back to error / errorMessage for failed tools. */
export function toolResultBody(data: Record<string, unknown>): string | undefined {
  for (const key of ['output', 'error', 'errorMessage'] as const) {
    const v = data[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return undefined
}

/** Whether a process row can show an expandable detail panel. */
export function canExpandProcessEvent(e: ProcessEvent): boolean {
  return (e.kind === 'llm' || e.kind === 'tool') && Boolean(e.body?.trim())
}

const FAILURE_TOOL_BODY_MAX = 800
const FAILURE_LLM_BODY_MAX = 6000

function clipForFailureDetail(text: string, max: number): string {
  const t = text.trim()
  if (t.length <= max) return t
  return `${t.slice(0, max)}\n…(已截断，完整内容见上方对应卡片展开)`
}

/**
 * Failure STATUS expand panel: reason first, then model output, then truncated tool dumps.
 */
export function buildFailureDetail(reason: string, events: ProcessEvent[]): string {
  const why = reason.trim() || '未知错误'
  const parts: string[] = [`【失败原因】\n${why}`]

  const llm = [...events].reverse().find((e) => e.kind === 'llm' && e.body?.trim())
  if (llm?.body) {
    parts.push(
      `\n---\n【模型输出】（最可能含无效 JSON / 缺 view）\n${clipForFailureDetail(
        formatStreamBodyForDisplay(llm.body),
        FAILURE_LLM_BODY_MAX,
      )}`,
    )
  } else {
    parts.push('\n---\n【模型输出】\n（本轮未收到模型终态文本）')
  }

  const tools = events.filter((e) => e.kind === 'tool' && e.body?.trim())
  if (tools.length) {
    for (const e of tools) {
      const name = processEventDisplayLabel(e.title)
      const flag = e.success === false ? '失败' : e.success === true ? '成功' : '完成'
      parts.push(
        `\n---\n【工具 ${name} · ${flag}】\n${clipForFailureDetail(e.body!, FAILURE_TOOL_BODY_MAX)}`,
      )
    }
  }

  return parts.join('\n')
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
 * Expanded LLM process body: pretty-print JSON (whole body, fenced, or trailing object)
 * so mid-run dumps are readable without changing stored raw stream text.
 */
export function formatStreamBodyForDisplay(raw: string): string {
  if (!raw) return ''
  const trimmed = raw.trim()

  const tryPretty = (candidate: string): string | null => {
    try {
      return JSON.stringify(JSON.parse(candidate), null, 2)
    } catch {
      return null
    }
  }

  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    const pretty = tryPretty(trimmed)
    if (pretty) return pretty
  }

  const closedFence = trimmed.match(/^```(?:json)?\s*\r?\n?([\s\S]*?)```\s*$/i)
  if (closedFence) {
    const inner = closedFence[1].trim()
    return tryPretty(inner) ?? inner
  }

  for (let i = trimmed.lastIndexOf('{'); i >= 0; i = trimmed.lastIndexOf('{', i - 1)) {
    const suffix = trimmed.slice(i).trim()
    const pretty = tryPretty(suffix)
    if (pretty) {
      const prose = trimmed.slice(0, i).replace(/```(?:json)?\s*$/i, '').trim()
      return prose ? `${prose}\n\n${pretty}` : pretty
    }
    if (i === 0) break
  }

  return raw
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
