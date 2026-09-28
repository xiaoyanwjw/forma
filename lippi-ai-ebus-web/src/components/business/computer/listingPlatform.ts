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
  /** 「生图 Prompt」section 正文 */
  framePromptsSummary: string
}
