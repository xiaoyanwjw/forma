import { request } from '@/api/client'
import type { Scene, SceneSkillCapsule } from '@/types/business/scene'

export function getScenes() {
  return request<Scene[]>('/api/v1/scenes')
}

export function getSceneSkillCapsules(sceneCode: string) {
  return request<SceneSkillCapsule>(`/api/v1/scenes/${encodeURIComponent(sceneCode)}/skills`)
}
