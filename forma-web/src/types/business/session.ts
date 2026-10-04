/** 侧栏会话列表条目 */
export interface SessionSummary {
  sessionId: string
  title: string
  sceneCode: string
  updatedAt: string
}

/** 回放 toolCall 摘要（无 arguments） */
export interface SessionToolCall {
  id?: string | null
  toolName?: string | null
}

/** R1 回放行（user / assistant / tool） */
export interface SessionMessage {
  role: 'user' | 'assistant' | 'tool' | string
  content: string
  createdAt?: string | null
  /** pi_session_entry.seq */
  seq?: number | null
  toolCallId?: string | null
  toolCalls?: SessionToolCall[] | null
  /**
   * 落库 append 键；HITL 可能为 `runId:suspend` / `runId:resume`。
   * 回放按去后缀后的逻辑 runId 聚类。
   */
  runId?: string | null
}

/** 按逻辑 runId 聚合的一轮任务（listing 策划+执行同轮） */
export interface SessionTurn {
  runId?: string | null
  at?: string | null
  userPrompt?: string | null
  /**
   * 本轮已落库且可展示的成果 id。
   * 没有这个字段时，回放仍按助手原文里的成果/指针判断（兼容旧会话）。
   */
  artifactRef?: string | null
  /** 成果类型码，用来选 Computer 窗格 */
  persistAs?: string | null
  messages: SessionMessage[]
}

/** 通用分页（nextToken 非空表示还有更早一页） */
export interface Page<T> {
  items: T[]
  nextToken?: string | null
}
