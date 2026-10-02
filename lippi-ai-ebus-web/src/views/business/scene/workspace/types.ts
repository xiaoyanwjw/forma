import type { ComputerListItem } from '@/types/business/computerView'

export type SceneWorkspaceItemHandoff = {
  whenPane: string
  actionLabel: string
  targetSkillId: string
  buildText: (item: ComputerListItem, index: number) => string | null
}

export type SceneWorkspaceToolbarHandoff = {
  whenPane: string
  actionLabel: string
  targetSkillId: string
  buildText: () => string | null
}

export type SceneWorkspaceSpec = {
  sceneCode: string
  breadcrumb: string
  artifactTypes: string[]
  paneBySkillId: Record<string, string>
  paneByArtifactType: Record<string, string>
  itemHandoffs?: SceneWorkspaceItemHandoff[]
  toolbarHandoffs?: SceneWorkspaceToolbarHandoff[]
}

export type SceneWorkspaceToolbarMeta = {
  whenPane: string
  actionLabel: string
  targetSkillId: string
}
