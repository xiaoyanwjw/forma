import type { SceneWorkspaceSpec } from '@/views/business/scene/workspace/types'

export const techDigestSpec: SceneWorkspaceSpec = {
  sceneCode: 'tech_digest',
  breadcrumb: '科技前沿',
  artifactTypes: ['tech_digest'],
  paneBySkillId: { 'tech-digest': 'digest' },
  paneByArtifactType: { tech_digest: 'digest', digest: 'digest' },
}

export const techProductSpec: SceneWorkspaceSpec = {
  sceneCode: 'tech_product',
  breadcrumb: '产品雷达',
  artifactTypes: ['tech_competitor'],
  paneBySkillId: { 'tech-competitor': 'competitor' },
  paneByArtifactType: { tech_competitor: 'competitor', competitor: 'competitor' },
}
