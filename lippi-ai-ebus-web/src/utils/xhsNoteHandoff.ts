export type XhsNoteHandoffInput = {
  title: string
  id?: string
  href?: string
  hook?: string
  angle?: string
}

/** 缺 title 或条目 id → null。链接可选（fallback 选题可能无真链）。 */
export function buildXhsNoteHandoffText(input: XhsNoteHandoffInput): string | null {
  const title = (input.title || '').replace(/^【优先发】/, '').trim()
  const id = (input.id || '').trim()
  if (!title || !id) return null
  const href = (input.href || '').trim()
  const lines = [
    `请根据选题「${title}」（条目 ${id}）写一篇小红书种草笔记，语气像真人分享。`,
    input.angle?.trim() && `角度：${input.angle.trim()}`,
    input.hook?.trim() && `钩子：${input.hook.trim()}`,
    href && /^https:\/\//i.test(href) ? `原笔记：${href}` : '',
  ].filter(Boolean)
  return lines.join('\n')
}

export function buildXhsBreakNoteHandoffText(): string {
  return '请按这次爆文拆解的骨架写一篇小红书种草笔记，语气像真人分享。'
}
