import {
  buildXhsBreakNoteHandoffText,
  extractTargetProductFromPrompt,
  extractXhsBreakHandoffFromView,
  noteHandoffTextForItem,
} from '@/utils/xhsNoteHandoff'
import type {
  SceneWorkspaceSpec,
  SceneWorkspaceToolbarMeta,
} from '@/views/business/scene/workspace/types'

export const XHS_BREAK_TOOLBAR: SceneWorkspaceToolbarMeta = {
  whenPane: 'break',
  actionLabel: '按骨架写笔记',
  targetSkillId: 'xhs-note',
}

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
  itemHandoffs: [
    {
      whenPane: 'topiclist',
      actionLabel: '写成笔记',
      targetSkillId: 'xhs-note',
      buildText: noteHandoffTextForItem,
    },
  ],
  toolbarHandoffs: undefined,
}

export function buildXhsBreakToolbarText(ctx: {
  view?: Parameters<typeof extractXhsBreakHandoffFromView>[0]
  lastPrompt?: string | null
}): string | null {
  const fromView = extractXhsBreakHandoffFromView(ctx.view)
  const targetProduct =
    fromView.targetProduct || extractTargetProductFromPrompt(ctx.lastPrompt)
  return buildXhsBreakNoteHandoffText({
    ...fromView,
    targetProduct,
  })
}
