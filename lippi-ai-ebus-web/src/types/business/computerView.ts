export type ComputerNoteTone = 'mute' | 'default'

/** Generic tag emphasis — domain mapping belongs in projectors, not the renderer. */
export type ComputerTagTone = 'neutral' | 'positive' | 'caution' | 'danger' | 'info' | 'safe'

/** Semantic line kinds from projectors; FE maps to locale labels. */
export type ComputerListLineKind =
  | 'priceBand'
  | 'painPoint'
  | 'angle'
  | 'diff'
  | 'niche'

/** Semantic tag kinds from picklist projector; FE maps to locale prefixes. */
export type ComputerTagKind = 'demand' | 'competition' | 'margin' | 'risk'

/** Optional note kinds; FE may prepend locale copy (e.g. assumptions → 假设：). */
export type ComputerNoteKind = 'assumptions'

export interface ComputerTag {
  text: string
  kind?: ComputerTagKind | string
  tone?: ComputerTagTone
}

export interface ComputerMarkdownBlock {
  type: 'markdown'
  text: string
}

export interface ComputerNoteBlock {
  type: 'note'
  text: string
  tone?: ComputerNoteTone
  kind?: ComputerNoteKind | string
}

export interface ComputerListLine {
  text: string
  /** Semantic field key from projector. Prefer over label for new payloads. */
  kind?: ComputerListLineKind | string
  /** Locale/display label; legacy payloads or FE-resolved. */
  label?: string
  /** Projector-driven emphasis; renderer does not infer from text. */
  emphasis?: 'default' | 'price'
}

export interface ComputerListItem {
  /** Semantic badge key (e.g. priority) or legacy display text. */
  badge?: string
  title: string
  lines?: ComputerListLine[]
  tags?: ComputerTag[]
  /** https only; omit if invalid */
  href?: string
}

export interface ComputerListBlock {
  type: 'list'
  ordered?: boolean
  items: ComputerListItem[]
}

export interface ComputerMediaBlock {
  type: 'media'
  role?: 'hero'
  alt?: string
  /** 可读 URL；真路径优先用 mediaObjectId 由 FE/网关解析 */
  src?: string
  mediaObjectId?: string
  placeholder?: string
}

export interface ComputerSectionBlock {
  type: 'section'
  heading: string
  body: string
  tone?: ComputerNoteTone
}

export type ComputerBlock =
  | ComputerMarkdownBlock
  | ComputerNoteBlock
  | ComputerListBlock
  | ComputerMediaBlock
  | ComputerSectionBlock

export interface ComputerDocument {
  version: 1
  /** Semantic key (picklist) or legacy/display title. */
  title: string
  /** Semantic key (settled) or legacy/display status. */
  status?: string
  blocks: ComputerBlock[]
}

/** Locale map for document title keys emitted by projectors. */
export const COMPUTER_TITLE_LABELS: Record<string, string> = {
  picklist: '选品清单',
  listingPreview: '上架素材预览',
}

/** Locale map for document status keys. */
export const COMPUTER_STATUS_LABELS: Record<string, string> = {
  settled: '已结算',
  demo: '演示',
}

/** Locale map for list item badge keys. */
export const COMPUTER_BADGE_LABELS: Record<string, string> = {
  priority: '优先试',
}

/** Locale map for list line kinds. */
export const COMPUTER_LINE_LABELS: Record<string, string> = {
  priceBand: '价格带',
  painPoint: '痛点',
  angle: '切入',
  diff: '差异',
  niche: '细分',
}

/** Locale map for tag kinds (prefix before raw value). */
export const COMPUTER_TAG_LABELS: Record<string, string> = {
  demand: '需求',
  competition: '竞争',
  margin: '利润',
  risk: '风险',
}

export function resolveComputerTitle(title: string): string {
  return COMPUTER_TITLE_LABELS[title] ?? title
}

export function resolveComputerStatus(status: string): string {
  return COMPUTER_STATUS_LABELS[status] ?? status
}

export function resolveComputerBadge(badge: string): string {
  return COMPUTER_BADGE_LABELS[badge] ?? badge
}

export function resolveLineLabel(line: ComputerListLine): string | undefined {
  if (line.kind && COMPUTER_LINE_LABELS[line.kind]) {
    return COMPUTER_LINE_LABELS[line.kind]
  }
  return line.label
}

export function resolveTagDisplay(tag: ComputerTag): string {
  if (tag.kind && COMPUTER_TAG_LABELS[tag.kind]) {
    return `${COMPUTER_TAG_LABELS[tag.kind]} ${tag.text}`
  }
  return tag.text
}

export function resolveNoteText(block: ComputerNoteBlock): string {
  if (block.kind === 'assumptions') {
    return `假设：${block.text}`
  }
  return block.text
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

const TAG_TONES: readonly ComputerTagTone[] = [
  'neutral',
  'positive',
  'caution',
  'danger',
  'info',
  'safe',
]

function parseTag(raw: unknown): ComputerTag | null {
  if (typeof raw === 'string' && raw.trim()) {
    return { text: raw, tone: 'neutral' }
  }
  if (!isRecord(raw) || typeof raw.text !== 'string' || !raw.text.trim()) {
    return null
  }
  const tag: ComputerTag = { text: raw.text }
  if (typeof raw.kind === 'string' && raw.kind.trim()) {
    tag.kind = raw.kind.trim()
  }
  if (typeof raw.tone === 'string' && (TAG_TONES as readonly string[]).includes(raw.tone)) {
    tag.tone = raw.tone as ComputerTagTone
  }
  return tag
}

function asTagList(value: unknown): ComputerTag[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const tags = value.map(parseTag).filter((t): t is ComputerTag => t !== null)
  return tags.length ? tags : undefined
}

const LINE_EMPHASIS: readonly NonNullable<ComputerListLine['emphasis']>[] = ['default', 'price']

function parseLine(raw: unknown): ComputerListLine | null {
  if (typeof raw === 'string' && raw.trim()) {
    return { text: raw }
  }
  if (!isRecord(raw) || typeof raw.text !== 'string' || !raw.text.trim()) {
    return null
  }
  const line: ComputerListLine = { text: raw.text }
  if (typeof raw.kind === 'string' && raw.kind.trim()) {
    line.kind = raw.kind.trim()
  }
  if (typeof raw.label === 'string' && raw.label.trim()) {
    line.label = raw.label.trim()
  }
  if (typeof raw.emphasis === 'string' && (LINE_EMPHASIS as readonly string[]).includes(raw.emphasis)) {
    line.emphasis = raw.emphasis as ComputerListLine['emphasis']
  }
  return line
}

function asLineList(value: unknown): ComputerListLine[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const lines = value.map(parseLine).filter((l): l is ComputerListLine => l !== null)
  return lines.length ? lines : undefined
}

export function sanitizeHttpsHref(raw: unknown): string | undefined {
  if (typeof raw !== 'string') return undefined
  const t = raw.trim()
  if (!t.startsWith('https://')) return undefined
  return t
}

function parseListItem(raw: unknown): ComputerListItem | null {
  if (!isRecord(raw) || typeof raw.title !== 'string') {
    return null
  }
  const item: ComputerListItem = { title: raw.title }
  if (typeof raw.badge === 'string') {
    item.badge = raw.badge
  }
  const lines = asLineList(raw.lines)
  if (lines) {
    item.lines = lines
  }
  const tags = asTagList(raw.tags)
  if (tags) {
    item.tags = tags
  }
  const href = sanitizeHttpsHref(raw.href)
  if (href) {
    item.href = href
  }
  return item
}

function warnSkipBlock(reason: 'unknown' | 'malformed', detail: string): void {
  if (reason === 'unknown') {
    console.warn('[parseComputerDocument] skip unknown block type:', detail)
    return
  }
  console.warn('[parseComputerDocument] skip malformed block:', detail)
}

function parseBlock(raw: unknown): ComputerBlock | null {
  if (!isRecord(raw) || typeof raw.type !== 'string') {
    warnSkipBlock('malformed', 'missing or invalid type')
    return null
  }
  if (raw.type === 'markdown') {
    if (typeof raw.text !== 'string') {
      warnSkipBlock('malformed', 'markdown')
      return null
    }
    return { type: 'markdown', text: raw.text }
  }
  if (raw.type === 'note') {
    if (typeof raw.text !== 'string') {
      warnSkipBlock('malformed', 'note')
      return null
    }
    const note: ComputerNoteBlock = { type: 'note', text: raw.text }
    if (raw.tone === 'mute' || raw.tone === 'default') {
      note.tone = raw.tone
    }
    if (typeof raw.kind === 'string' && raw.kind.trim()) {
      note.kind = raw.kind.trim()
    }
    return note
  }
  if (raw.type === 'list') {
    if (!Array.isArray(raw.items)) {
      warnSkipBlock('malformed', 'list')
      return null
    }
    const items = raw.items
      .map(parseListItem)
      .filter((item): item is ComputerListItem => item !== null)
    const list: ComputerListBlock = { type: 'list', items }
    if (typeof raw.ordered === 'boolean') {
      list.ordered = raw.ordered
    }
    return list
  }
  if (raw.type === 'media') {
    const media: ComputerMediaBlock = { type: 'media' }
    if (raw.role === 'hero') {
      media.role = 'hero'
    }
    if (typeof raw.alt === 'string') {
      media.alt = raw.alt
    }
    if (typeof raw.src === 'string') {
      media.src = raw.src
    }
    if (typeof raw.mediaObjectId === 'string') {
      media.mediaObjectId = raw.mediaObjectId
    }
    if (typeof raw.placeholder === 'string') {
      media.placeholder = raw.placeholder
    }
    return media
  }
  if (raw.type === 'section') {
    if (typeof raw.heading !== 'string' || typeof raw.body !== 'string') {
      warnSkipBlock('malformed', 'section')
      return null
    }
    const section: ComputerSectionBlock = {
      type: 'section',
      heading: raw.heading,
      body: raw.body,
    }
    if (raw.tone === 'mute' || raw.tone === 'default') {
      section.tone = raw.tone
    }
    return section
  }
  warnSkipBlock('unknown', raw.type)
  return null
}

/** 校验 version===1 与 title/blocks；未知 type 丢弃 */
export function parseComputerDocument(raw: unknown): ComputerDocument | null {
  if (!isRecord(raw)) {
    return null
  }
  if (raw.version !== 1 || typeof raw.title !== 'string' || !Array.isArray(raw.blocks)) {
    return null
  }
  const blocks: ComputerBlock[] = []
  for (const item of raw.blocks) {
    const block = parseBlock(item)
    if (block) {
      blocks.push(block)
    }
  }
  const document: ComputerDocument = {
    version: 1,
    title: raw.title,
    blocks,
  }
  if (typeof raw.status === 'string') {
    document.status = raw.status
  }
  return document
}
