import { ecommerceSpec } from '@/views/business/scene/ecommerce/spec'
import { xhsSpec } from '@/views/business/scene/xiaohongshu/spec'
import type { SceneWorkspaceSpec } from '@/views/business/scene/workspace/types'

const SPECS: Record<string, SceneWorkspaceSpec> = {
  [ecommerceSpec.sceneCode]: ecommerceSpec,
  [xhsSpec.sceneCode]: xhsSpec,
}

export function getSceneWorkspaceSpec(sceneCode: string): SceneWorkspaceSpec | null {
  const code = (sceneCode || '').trim()
  if (!code) return null
  return SPECS[code] ?? null
}
