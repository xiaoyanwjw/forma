import type { ComputerDocument } from '@/types/business/computerView'

/** 历史列表条目 */
export interface HistoryArtifactSummary {
  id: string
  artifactType: 'picklist' | 'sku' | string
  sceneCode: string
  title: string
  createdAt: string
}

/** 历史详情（含 Computer view） */
export interface HistoryArtifactDetail extends HistoryArtifactSummary {
  view: ComputerDocument | Record<string, unknown> | null
}
