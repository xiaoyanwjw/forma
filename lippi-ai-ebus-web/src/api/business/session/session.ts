import { request } from '@/api/client'
import type { HistoryArtifactDetail } from '@/types/business/history'
import type { SessionMessage, SessionSummary } from '@/types/business/session'

export function listSessions(sceneCode?: string) {
  const q = sceneCode?.trim()
    ? `?sceneCode=${encodeURIComponent(sceneCode.trim())}`
    : ''
  return request<SessionSummary[]>(`/api/v1/sessions${q}`)
}

export function getSessionMessages(sessionId: string) {
  return request<SessionMessage[]>(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/messages`,
  )
}

export function getLatestSessionArtifact(sessionId: string) {
  return request<HistoryArtifactDetail | null>(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/latest-artifact`,
  )
}
