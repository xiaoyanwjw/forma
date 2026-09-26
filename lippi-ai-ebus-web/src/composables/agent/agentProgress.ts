export const MESSAGE_FOLD_THRESHOLD = 120

export type ProgressStep = { id: string; label: string; done: boolean }

export function toolEventLabel(data: Record<string, unknown>): string {
  for (const key of ['toolName', 'name', 'tool', 'payload'] as const) {
    const v = data[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return 'tool'
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
