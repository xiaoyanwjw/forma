/** 上架预览示意皮肤（非真实平台模板） */
export type ListingPlatformSkin = 'adam' | 'taobao' | 'xianyu' | 'douyin'

export const LISTING_PLATFORM_SKINS: ReadonlyArray<{
  id: ListingPlatformSkin
  label: string
  mark: string
}> = [
  { id: 'adam', label: 'Adam', mark: 'A' },
  { id: 'taobao', label: '淘宝', mark: '淘' },
  { id: 'xianyu', label: '闲鱼', mark: '闲' },
  { id: 'douyin', label: '抖音', mark: '抖' },
]

/** 从 Computer view 抽出的上架素材字段 */
export interface ListingPreviewContent {
  heroPlan: string
  heroMounted: boolean
  detailTitle: string
  detailBody: string
  displayNotes: string
  /** 主图分镜短句（ordered list） */
  frames: string[]
  /** 「生图 Prompt」section 正文（FE 会并入分镜展示） */
  framePromptsSummary: string
}

/** 分镜短句 + 对应生图 Prompt（一条分镜一条 prompt） */
export type ListingStoryboardBeat = {
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

  return chunks.map((chunk) => {
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
  }).filter((e) => Boolean(e.prompt))
}

/** Zip 分镜标题与 Prompt section；缺一侧时仍保留另一侧。 */
export function buildStoryboardBeats(
  frames: string[],
  summary: string,
): ListingStoryboardBeat[] {
  const prompts = parseFramePromptEntries(summary)
  const n = Math.max(frames.length, prompts.length)
  if (n === 0) return []
  const beats: ListingStoryboardBeat[] = []
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
