/** 全角书名号「…」整段视为可编辑示例槽位（含书名号本身）。 */
const SLOT_RE = /「[^」]*」/g

export type PromptSlotSegment = {
  text: string
  /** true = 整段「…」槽位 */
  slot: boolean
}

/**
 * 把提示词拆成普通文本 / 「…」槽位片段，供对话气泡与输入高亮复用。
 */
export function splitPromptSlots(raw: string): PromptSlotSegment[] {
  const text = raw ?? ''
  if (!text) {
    return []
  }
  const out: PromptSlotSegment[] = []
  let last = 0
  SLOT_RE.lastIndex = 0
  let m: RegExpExecArray | null
  while ((m = SLOT_RE.exec(text)) !== null) {
    if (m.index > last) {
      out.push({ text: text.slice(last, m.index), slot: false })
    }
    out.push({ text: m[0], slot: true })
    last = m.index + m[0].length
  }
  if (last < text.length) {
    out.push({ text: text.slice(last), slot: false })
  }
  return out.length ? out : [{ text, slot: false }]
}

export function hasPromptSlots(raw: string): boolean {
  return /「[^」]*」/.test(raw ?? '')
}
