import type { ComputerDocument } from '@/types/business/computerView'

/** AD-4 闭合 SSE 事件名（禁止同义别名） */
export const AD4_EVENT_NAMES = [
  'run_started',
  'message_delta',
  'tool_started',
  'tool_finished',
  'artifact_ready',
  'run_failed',
  'run_settled',
] as const

export type Ad4EventName = (typeof AD4_EVENT_NAMES)[number]

export interface Ad4SseEvent {
  name: Ad4EventName
  data: Record<string, unknown>
}

/** 空跑请求：sceneId / sceneCode 至少一项 */
export interface StreamEmptyRunOptions {
  sceneId?: string
  sceneCode?: string
  sessionId?: string
  signal?: AbortSignal
}

/** 计费选品请求 */
export interface StreamPicklistRunOptions {
  text: string
  sceneId?: string
  sceneCode?: string
  sessionId?: string
  signal?: AbortSignal
}

/** artifact_ready 选品成果（Computer 展示） */
export interface PicklistArtifactItem {
  title: string
  priceBand: string
  reason: string
  differentiation: string
  demand: string
  competition: string
  margin: string
  risk: string
}

export interface PicklistArtifactPayload {
  picklistId: string
  runId: string
  templateId: string
  disclaimer: string
  assumptions?: string
  items: PicklistArtifactItem[]
  view?: ComputerDocument
}

export function isAd4EventName(value: string): value is Ad4EventName {
  return (AD4_EVENT_NAMES as readonly string[]).includes(value)
}
