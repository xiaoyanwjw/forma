export type ComputerNoteTone = 'mute' | 'default'

export interface ComputerNoteBlock {
  type: 'note'
  text: string
  tone?: ComputerNoteTone
}

export interface ComputerListItem {
  badge?: string
  title: string
  lines?: string[]
  tags?: string[]
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
  | ComputerNoteBlock
  | ComputerListBlock
  | ComputerMediaBlock
  | ComputerSectionBlock

export interface ComputerDocument {
  version: 1
  title: string
  status?: string
  blocks: ComputerBlock[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function asStringList(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  return value.filter((item): item is string => typeof item === 'string')
}

function parseListItem(raw: unknown): ComputerListItem | null {
  if (!isRecord(raw) || typeof raw.title !== 'string') {
    return null
  }
  const item: ComputerListItem = { title: raw.title }
  if (typeof raw.badge === 'string') {
    item.badge = raw.badge
  }
  const lines = asStringList(raw.lines)
  if (lines) {
    item.lines = lines
  }
  const tags = asStringList(raw.tags)
  if (tags) {
    item.tags = tags
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
  if (raw.type === 'note') {
    if (typeof raw.text !== 'string') {
      warnSkipBlock('malformed', 'note')
      return null
    }
    const note: ComputerNoteBlock = { type: 'note', text: raw.text }
    if (raw.tone === 'mute' || raw.tone === 'default') {
      note.tone = raw.tone
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
