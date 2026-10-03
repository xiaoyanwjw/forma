import type { SceneWorkspaceSpec } from '@/views/business/scene/workspace/types'

export const ecommerceSpec: SceneWorkspaceSpec = {
  sceneCode: 'ecommerce',
  breadcrumb: '电商开店',
  artifactTypes: ['picklist', 'sku'],
  paneBySkillId: {
    'ecommerce-picklist': 'picks',
    'ecommerce-skulist': 'listing',
  },
  paneByArtifactType: {
    picklist: 'picks',
    picks: 'picks',
    sku: 'listing',
    listing: 'listing',
    skulist: 'listing',
  },
}
