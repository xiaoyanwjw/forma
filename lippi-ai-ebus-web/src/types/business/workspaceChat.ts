import type { ProcessEvent } from '@/composables/agent/agentProgress'

/** Shared chat bubble model for scene workspace shells (ecommerce / xhs). */
export interface WorkspaceChatMessage {
  id: string
  role: 'user' | 'agent'
  text: string
  /** Ordered AGENT / LLM / TOOL rows from SSE */
  processEvents?: ProcessEvent[]
  /** Failed STATUS expand panel (reason + tool/model dumps) */
  statusDetail?: string
  failed?: boolean
  /** Replay uses plain bubble; live/synthesized STATUS uses console */
  presentation?: 'bubble' | 'console'
  /** epoch ms（回放来自 entry.created_at） */
  at?: number
}
