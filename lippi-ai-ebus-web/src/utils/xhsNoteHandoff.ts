export type XhsNoteHandoffInput = {
  title: string
  id?: string
  href?: string
  hook?: string
  angle?: string
}

export type XhsBreakNoteHandoffInput = {
  skeleton?: string
  rewrite?: string
  targetProduct?: string
  structure?: string
}

type BreakViewLike = {
  title?: string
  blocks?: Array<{
    type?: string
    text?: string
    heading?: string
    body?: string
  }>
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

export function extractTargetProductFromPrompt(prompt?: string | null): string {
  const m = (prompt || '').match(/商品「([^」]+)」/)
  return m?.[1]?.trim() || ''
}

export function extractXhsBreakHandoffFromView(
  view?: BreakViewLike | null,
): XhsBreakNoteHandoffInput {
  const sections: Record<string, string> = {}
  for (const block of view?.blocks || []) {
    if (block.type === 'markdown' && block.text) {
      Object.assign(sections, parseMarkdownSections(block.text))
    }
    if (block.type === 'section' && block.heading && block.body) {
      sections[normalizeHeading(block.heading)] = block.body.trim()
    }
  }
  const title = (view?.title || '').trim()
  const fromTitle = title.includes('·') ? title.split('·')[0]!.trim() : ''
  return {
    structure: sections.structure || '',
    skeleton: sections.skeleton || '',
    rewrite: sections.rewrite || '',
    targetProduct: fromTitle,
  }
}

export function buildXhsBreakNoteHandoffText(input?: XhsBreakNoteHandoffInput | null): string {
  const skeleton = input?.skeleton?.trim() || ''
  const rewrite = input?.rewrite?.trim() || ''
  const structure = input?.structure?.trim() || ''
  const targetProduct = input?.targetProduct?.trim() || ''
  const lead = targetProduct
    ? `请按这次爆文拆解的骨架，写一篇关于「${targetProduct}」的小红书种草笔记，语气像真人分享。`
    : '请按这次爆文拆解的骨架写一篇小红书种草笔记，语气像真人分享。'
  const lines = [
    lead,
    structure && `结构要点：${truncate(structure, 240)}`,
    skeleton && `骨架：${truncate(skeleton, 400)}`,
    rewrite && `改写参考：${truncate(rewrite, 400)}`,
  ].filter(Boolean)
  return lines.join('\n')
}

function parseMarkdownSections(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  const parts = text.split(/^##\s+/m)
  for (const part of parts) {
    const trimmed = part.trim()
    if (!trimmed) continue
    const nl = trimmed.indexOf('\n')
    const heading = (nl >= 0 ? trimmed.slice(0, nl) : trimmed).trim()
    const body = (nl >= 0 ? trimmed.slice(nl + 1) : '').trim()
    const key = normalizeHeading(heading)
    if (key && body) out[key] = body
  }
  return out
}

function normalizeHeading(heading: string): string {
  const h = heading.replace(/[#\s]/g, '')
  if (h.includes('骨架')) return 'skeleton'
  if (h.includes('改写')) return 'rewrite'
  if (h.includes('拆解') || h.includes('结构')) return 'structure'
  return ''
}

function truncate(text: string, max: number): string {
  if (text.length <= max) return text
  return `${text.slice(0, max).trim()}…`
}
