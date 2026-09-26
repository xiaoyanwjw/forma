import { request } from '@/api/client'
import type { Scene } from '@/types/business/scene'

export function getScenes() {
  return request<Scene[]>('/api/v1/scenes')
}
