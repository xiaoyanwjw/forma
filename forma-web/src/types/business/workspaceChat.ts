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
  /** Computer 窗格；有则点 STATUS 打开对应预览 */
  previewPane?: string
  /** epoch ms（回放来自 entry.created_at） */
  at?: number
}
