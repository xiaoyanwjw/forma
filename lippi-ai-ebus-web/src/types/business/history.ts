import type { ComputerDocView } from '@/types/business/computerView'

/** 历史列表条目 */
export interface HistoryArtifactSummary {
  id: string
  artifactType: 'picklist' | 'sku' | string
  sceneCode: string
  title: string
  createdAt: string
}

/** 历史详情（含 Computer view；sessionId 可空） */
export interface HistoryArtifactDetail extends HistoryArtifactSummary {
  view: ComputerDocView | Record<string, unknown> | null
  sessionId?: string | null
}
