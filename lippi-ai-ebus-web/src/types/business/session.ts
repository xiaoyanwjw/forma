/** 侧栏会话列表条目 */
export interface SessionSummary {
  sessionId: string
  title: string
  sceneCode: string
  updatedAt: string
}

/** R1 回放气泡（user / assistant 文本） */
export interface SessionMessage {
  role: 'user' | 'assistant' | string
  content: string
  createdAt?: string | null
}
