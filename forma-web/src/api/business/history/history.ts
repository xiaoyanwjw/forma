import { request } from '@/api/client'
import type { HistoryArtifactDetail, HistoryArtifactSummary } from '@/types/business/history'

export function getHistoryArtifacts(sceneCode?: string) {
  const q = sceneCode?.trim()
    ? `?sceneCode=${encodeURIComponent(sceneCode.trim())}`
    : ''
  return request<HistoryArtifactSummary[]>(`/api/v1/history/artifacts${q}`)
}

export function getHistoryArtifact(id: string) {
  return request<HistoryArtifactDetail>(
    `/api/v1/history/artifacts/${encodeURIComponent(id)}`,
  )
}
