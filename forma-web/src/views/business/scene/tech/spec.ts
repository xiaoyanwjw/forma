import type { SceneWorkspaceSpec } from '@/views/business/scene/workspace/types'

export const techDigestSpec: SceneWorkspaceSpec = {
  sceneCode: 'tech_digest',
  breadcrumb: '科技速读',
  artifactTypes: ['tech_digest'],
  paneBySkillId: { 'tech-digest': 'digest' },
  paneByArtifactType: { tech_digest: 'digest', digest: 'digest' },
}
