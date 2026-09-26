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
