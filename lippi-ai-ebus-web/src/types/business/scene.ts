/** 场景开放状态（与 SceneCatalog API `status` 对齐） */
export type SceneStatus = 'AVAILABLE' | 'COMING_SOON'

/** GET /api/v1/scenes 列表项（画廊元数据；无提示词/tool/skill 正文） */
export interface Scene {
  bizId: string
  sceneCode: string
  displayName: string
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
