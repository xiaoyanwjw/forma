/** 场景开放状态（与 SceneCatalog API `status` 对齐） */
export type SceneStatus = 'AVAILABLE' | 'COMING_SOON'

/** 画廊一级分类码（与 SceneCatalog API `category` 对齐） */
export type SceneCategory = 'tech' | 'ecommerce' | 'content' | 'sports' | 'life'

/** 画廊分类筛选（含「全部」） */
export type SceneCategoryFilter = 'all' | SceneCategory

export const SCENE_CATEGORY_TABS: ReadonlyArray<{ code: SceneCategoryFilter; label: string }> = [
  { code: 'all', label: '全部' },
  { code: 'tech', label: '科技' },
  { code: 'ecommerce', label: '电商' },
  { code: 'content', label: '内容' },
  { code: 'sports', label: '体育' },
  { code: 'life', label: '生活' },
]

const CATEGORY_TAB_RANK: Record<string, number> = {
  tech: 0,
  ecommerce: 1,
  content: 2,
  sports: 3,
  life: 4,
}

/** 「全部」按分类 Tab 顺序，同分类再按 sortOrder */
export function compareScenesByGalleryOrder(
  a: { category: string; sortOrder: number },
  b: { category: string; sortOrder: number },
): number {
  const rankA = CATEGORY_TAB_RANK[a.category] ?? 99
  const rankB = CATEGORY_TAB_RANK[b.category] ?? 99
  if (rankA !== rankB) return rankA - rankB
  return a.sortOrder - b.sortOrder
}

/** GET /api/v1/scenes 列表项（画廊元数据；无提示词/tool/skill 正文） */
export interface Scene {
  bizId: string
  sceneCode: string
  displayName: string
  category: SceneCategory | string
  status: SceneStatus | string
  sortOrder: number
  summary: string
}

/** GET /api/v1/scenes/{sceneCode}/skills 胶囊栏一项 */
export interface SceneSkillCapsuleItem {
  skillId: string
  label: string
  examplePrompt: string
  sortOrder: number
}

/** GET /api/v1/scenes/{sceneCode}/skills 胶囊栏 */
export interface SceneSkillCapsule {
  sceneCode: string
  skills: SceneSkillCapsuleItem[]
}
