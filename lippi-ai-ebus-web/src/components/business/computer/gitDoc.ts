import type { ComputerBlock } from '@/types/business/computerView'

/** Dual-track storyboard section headings (Chinese protocol contract). */
export const STORYBOARD_SECTION_HEADINGS = {
  detailTitle: '详情标题',
  detailBody: '详情正文',
  displayNotes: '展示说明',
  framePrompts: '生图 Prompt',
} as const

/** Storyboard + detail copy fields projected from Computer blocks. */
export interface StoryboardDoc {
  detailTitle: string
  detailBody: string
  displayNotes: string
  /** Storyboard captions (ordered list) */
  frames: string[]
  /** 「生图 Prompt」section body (zipped into beats on the FE) */
  framePromptsSummary: string
}

/** Caption + paired image prompt */
export type StoryboardBeat = {
  caption: string
  prompt?: string
  negative?: string
}

export function parseFramePromptEntries(
  summary: string,
): Array<{ prompt: string; negative?: string }> {
  const text = (summary || '').trim()
  if (!text) return []

  const chunks = text
    .split(/(?:^|\n)\s*\d+\.\s+/)
    .map((s) => s.trim())
    .filter(Boolean)

  return chunks
    .map((chunk) => {
      const lines = chunk.split('\n')
      const promptLines: string[] = []
      let negative: string | undefined
      for (const line of lines) {
        const neg = line.match(/^\s*negative:\s*(.*)$/i)
        if (neg) {
          negative = (neg[1] || '').trim() || undefined
          continue
        }
        promptLines.push(line.trimEnd())
      }
      return {
        prompt: promptLines.join('\n').trim(),
        negative,
      }
    })
    .filter((e) => Boolean(e.prompt))
}

/** Zip frame captions with Prompt section; keep a side if the other is missing. */
export function buildStoryboardBeats(
  frames: string[],
  summary: string,
): StoryboardBeat[] {
  const prompts = parseFramePromptEntries(summary)
  const n = Math.max(frames.length, prompts.length)
  if (n === 0) return []
  const beats: StoryboardBeat[] = []
  for (let i = 0; i < n; i++) {
    const caption = (frames[i] || '').trim() || `图${i + 1}`
    const entry = prompts[i]
    beats.push({
      caption,
      prompt: entry?.prompt,
      negative: entry?.negative,
    })
  }
  return beats
}

function isOrderedList(block: Extract<ComputerBlock, { type: 'list' }>): boolean {
  return block.ordered !== false
}

/** Project dual-track blocks into a storyboard document model. */
export function toStoryboardDoc(blocks: ComputerBlock[]): StoryboardDoc {
  let detailTitle = ''
  let detailBody = ''
  let displayNotes = ''
  let framePromptsSummary = ''
  const frames: string[] = []
  const H = STORYBOARD_SECTION_HEADINGS
  for (const block of blocks) {
    if (block.type === 'list' && isOrderedList(block)) {
      for (const item of block.items) {
        const title = (item.title || '').trim()
        if (title) frames.push(title)
      }
      continue
    }
    if (block.type !== 'section') continue
    if (block.heading === H.detailTitle) detailTitle = block.body
    else if (block.heading === H.detailBody) detailBody = block.body
    else if (block.heading === H.displayNotes) displayNotes = block.body
    else if (block.heading === H.framePrompts) framePromptsSummary = block.body
  }
  return {
    detailTitle,
    detailBody,
    displayNotes,
    frames,
    framePromptsSummary,
  }
}

/**
 * Whether this Computer document should use storyboard GitView layout
 * (exec dual-track). Plan-phase markdown stays on generic blocks mapping.
 */
export function hasStoryboardDoc(doc: {
  title?: string
  blocks: ComputerBlock[]
} | null | undefined): boolean {
  if (!doc) return false
  const key = doc.title?.trim() || ''
  if (key === 'picklist' || key === 'report') return false
  if (key === 'listingPreview') return true
  if (/上架|listing/i.test(key)) return true
  if (doc.blocks.some((b) => b.type === 'list')) return false
  const H = STORYBOARD_SECTION_HEADINGS
  return doc.blocks.some(
    (b) =>
      b.type === 'section' &&
      (b.heading === H.detailTitle || b.heading === H.detailBody),
  )
}

/** Section emphasis classes for generic (non-storyboard) block path. */
export function storyboardSectionClass(
  heading: string,
  tone?: 'mute' | 'default',
): Record<string, boolean> {
  const H = STORYBOARD_SECTION_HEADINGS
  return {
    'is-notes': tone === 'mute' || heading === H.displayNotes,
    'is-title': heading === H.detailTitle,
    'is-body':
      heading === H.detailBody || (heading !== H.detailTitle && tone !== 'mute'),
  }
}

/** Slug document title into a README file name. */
export function defaultGitFileName(title: string | undefined): string {
  const raw = (title || 'document').trim()
  const slug = raw
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
  return `${slug || 'document'}.md`
}
