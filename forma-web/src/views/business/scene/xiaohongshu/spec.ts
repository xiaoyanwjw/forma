import type { SceneWorkspaceSpec } from '@/views/business/scene/workspace/types'

export const xhsSpec: SceneWorkspaceSpec = {
  sceneCode: 'xiaohongshu',
  breadcrumb: '小红书种草',
  artifactTypes: ['xhs_topiclist', 'xhs_note', 'xhs_break'],
  paneBySkillId: {
    'xhs-topiclist': 'topiclist',
    'xhs-note': 'note',
    'xhs-break': 'break',
  },
  paneByArtifactType: {
    xhs_topiclist: 'topiclist',
    topiclist: 'topiclist',
    xhs_note: 'note',
    note: 'note',
    xhs_break: 'break',
    break: 'break',
  },
}
