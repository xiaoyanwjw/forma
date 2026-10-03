import type { ComputerDocView } from '@/types/business/computerView'

/** AD-4 闭合 SSE 事件名（禁止同义别名） */
export const AD4_EVENT_NAMES = [
  'run_started',
  'agent_started',
  'message_delta',
  'tool_started',
  'tool_finished',
  'agent_ended',
  'artifact_ready',
  'human_input_required',
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

/** 计费 Listing 请求 */
export interface StreamListingRunOptions {
  text: string
  sceneId?: string
  sceneCode?: string
  sessionId?: string
  signal?: AbortSignal
}

/** 通用 Generation Run（FE 主入口） */
export interface StreamAgentRunOptions {
  text?: string
  sceneId?: string
  sceneCode?: string
  sessionId?: string
  skillId?: string
  dryRun?: boolean
  signal?: AbortSignal
}

/** artifact_ready：仅 view + artifactRef */
export interface GenerationArtifactPayload {
  artifactRef: string
  view: ComputerDocView
}

export type AskHumanOptionId = 'confirm_execute' | 'supplement'

export interface AskHumanOption {
  id: string
  label: string
}

/** ask_human 挂起：聊天选项条 */
export interface HumanInputRequiredPayload {
  question: string
  options: AskHumanOption[]
  allowFreeText: boolean
  toolCallId: string
  runId?: string
}

/** Listing HITL 续跑（新 SSE） */
export interface ResumeGenerationRunOptions {
  runId: string
  toolCallId: string
  optionId?: AskHumanOptionId | string
  freeText?: string
  confirmId?: string
  signal?: AbortSignal
}

export function isAd4EventName(value: string): value is Ad4EventName {
  return (AD4_EVENT_NAMES as readonly string[]).includes(value)
}
