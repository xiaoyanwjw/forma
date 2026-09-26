export const MESSAGE_FOLD_THRESHOLD = 120

export type ProgressStep = { id: string; label: string; done: boolean }

const TOOL_DISPLAY_NAMES: Record<string, string> = {
  read_skill: '读取技能说明',
  echo: '执行辅助工具',
  tool: '执行工具',
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

export function applyToolStarted(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[] {
  const label = toolEventLabel(data)
  const id = typeof data.toolCallId === 'string' && data.toolCallId.trim()
    ? data.toolCallId.trim()
    : `tool-${steps.length}-${label}`
  return [...steps, { id, label, done: false }]
}

export function applyToolFinished(steps: ProgressStep[], data: Record<string, unknown>): ProgressStep[] {
  const label = toolEventLabel(data)
  const callId = typeof data.toolCallId === 'string' ? data.toolCallId.trim() : ''
  let matched = false
  return steps.map((s) => {
    if (matched) return s
    if ((callId && s.id === callId) || (!callId && s.label === label && !s.done)) {
      matched = true
      return { ...s, done: true }
    }
    return s
  })
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
  const text = raw.replace(/```json[\s\S]*?```/gi, '').replace(/```[\s\S]*?```/g, '')
  return stripTrailingJsonObject(text).trim()
}
