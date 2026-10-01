import { request } from '@/api/client'
import type { HistoryArtifactDetail } from '@/types/business/history'
import type { Page, SessionMessage, SessionSummary } from '@/types/business/session'

export const SESSION_MESSAGE_PAGE_SIZE = 100

export function listSessions(sceneCode?: string) {
  const q = sceneCode?.trim()
    ? `?sceneCode=${encodeURIComponent(sceneCode.trim())}`
    : ''
  return request<SessionSummary[]>(`/api/v1/sessions${q}`)
}

export function getSessionMessages(
  sessionId: string,
  opts?: { nextToken?: string; limit?: number },
) {
  const params = new URLSearchParams()
  const token = opts?.nextToken?.trim()
  if (token) {
    params.set('nextToken', token)
  }
  const limit = opts?.limit ?? SESSION_MESSAGE_PAGE_SIZE
  params.set('limit', String(limit))
  const q = params.toString()
  return request<Page<SessionMessage>>(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/messages${q ? `?${q}` : ''}`,
  )
}

export function getLatestSessionArtifact(
  sessionId: string,
  artifactType?: 'picklist' | 'sku' | 'xhs_topiclist' | 'xhs_note' | 'xhs_break',
) {
  const params = new URLSearchParams()
  if (artifactType) {
    params.set('artifactType', artifactType)
  }
  const q = params.toString()
  return request<HistoryArtifactDetail | null>(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/latest-artifact${q ? `?${q}` : ''}`,
  )
}
