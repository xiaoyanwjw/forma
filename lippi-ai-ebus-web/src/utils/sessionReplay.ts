/** R1 回放行（来自 pi_session_entry 的 user/assistant）。 */
export type SessionReplayRow = {
  role?: string
  content?: string
  createdAt?: string | null
  seq?: number | null
}

export type ReplayBubble = {
  role: 'user' | 'assistant'
  content: string
  /** artifact：渲染为成功 STATUS；text：普通气泡 */
  kind: 'text' | 'artifact'
  /** epoch ms；来自 entry.created_at */
  at?: number
}

/** 成果 JSON 已由 Computer 展示，对话里不再铺原文。 */
export function isArtifactDumpContent(content: string): boolean {
  const t = content.trim()
  if (!t) return false
  if (/^```(?:json)?/i.test(t) && /"view"\s*:/.test(t)) return true
  if (t.startsWith('{')) {
    const head = t.slice(0, 500)
    return /"view"\s*:/.test(head) && (/"blocks"\s*:/.test(head) || /"version"\s*:/.test(head))
  }
  return false
}

export function keepReplayRow(row: SessionReplayRow | null | undefined): boolean {
  if (!row) return false
  const text = typeof row.content === 'string' ? row.content.trim() : ''
  if (!text) return false
  const role = (row.role || '').toLowerCase()
  return role === 'user' || role === 'assistant' || role === 'agent'
}

export function parseReplayTime(createdAt?: string | null): number | undefined {
  if (!createdAt || typeof createdAt !== 'string') return undefined
  const ms = Date.parse(createdAt)
  return Number.isNaN(ms) ? undefined : ms
}

export type ReplayArtifactKind = 'picks' | 'listing' | 'topiclist' | 'note' | 'break'

function statusTextFor(artifactKind: ReplayArtifactKind | null | undefined): string {
  if (artifactKind === 'listing') {
    return '已生成上架素材，右侧 Computer 可查看主图位与文案。'
  }
  if (artifactKind === 'note') {
    return '已生成笔记草稿，右侧 Computer 可查看。'
  }
  if (artifactKind === 'break') {
    return '已生成爆文拆解，右侧 Computer 可查看。'
  }
  if (artifactKind === 'topiclist') {
    return '已生成选题清单，右侧 Computer 可查看。'
  }
  return '已生成选品成果，右侧 Computer 可查看。'
}

function inferXhsKindFromDump(content: string): 'topiclist' | 'note' | 'break' | null {
  const t = content || ''
  if (/"artifactType"\s*:\s*"xhs_note"/.test(t) || /"persistAs"\s*:\s*"xhs_note"/.test(t)) {
    return 'note'
  }
  if (/"artifactType"\s*:\s*"xhs_break"/.test(t) || /"persistAs"\s*:\s*"xhs_break"/.test(t)) {
    return 'break'
  }
  if (/"artifactType"\s*:\s*"xhs_topiclist"/.test(t) || /"persistAs"\s*:\s*"xhs_topiclist"/.test(t)) {
    return 'topiclist'
  }
  if (/"titleOptions"\s*:/.test(t) || /"imageHints"\s*:/.test(t)) {
    return 'note'
  }
  if (/"skeleton"\s*:/.test(t) || /"sourceBody"\s*:/.test(t) || /"rewrite"\s*:/.test(t)) {
    return 'break'
  }
  if (/"id"\s*:\s*"tp-\d+"/.test(t) || /"sourceNoteUrl"\s*:/.test(t)) {
    return 'topiclist'
  }
  return null
}

/**
 * 从成果 dump 猜类型，避免整段回放共用「最新 Computer kind」。
 * 小红书 artifactType 优先；再 listing / 选品信号；最后回退传入的 artifactKind。
 */
export function inferArtifactKindFromDump(
  content: string,
  fallback?: ReplayArtifactKind | null,
): ReplayArtifactKind | null {
  const t = content || ''
  const xhs = inferXhsKindFromDump(t)
  if (xhs) return xhs
  if (
    /"heroPlan"\s*:/.test(t) ||
    /"detailTitle"\s*:/.test(t) ||
    /"framePrompts"\s*:/.test(t) ||
    /"displayNotes"\s*:/.test(t) ||
    /"type"\s*:\s*"media"/.test(t) ||
    /"type"\s*:\s*"section"/.test(t)
  ) {
    return 'listing'
  }
  if (/"niche"\s*:/.test(t) || /"sourceUrl"\s*:/.test(t) || /"pl-\d+"/.test(t)) {
    return 'picks'
  }
  if (/"type"\s*:\s*"list"/.test(t)) {
    return 'picks'
  }
  return fallback ?? null
}

/**
 * 把 pi_session 的 user/assistant/tool 循环收成「用户 ↔ 一条 agent」交错列表。
 * - 丢掉 tool / system / 空 content
 * - 每一轮（一条 user + 其后 assistant*）只产出一条 agent：有成果 JSON → STATUS；否则取最后一条非 JSON 助手文案
 * - STATUS 文案按该轮 dump 推断类型，不单靠最新 Computer kind
 */
export function toReplayBubbles(
  rows: SessionReplayRow[] | null | undefined,
  artifactKind?: ReplayArtifactKind | null,
): ReplayBubble[] {
  const cleaned: { role: 'user' | 'assistant'; content: string; at?: number }[] = []
  if (!rows) return []
  for (const row of rows) {
    if (!keepReplayRow(row)) continue
    const content = String(row.content).trim()
    const role = (row.role || '').toLowerCase() === 'user' ? 'user' : 'assistant'
    cleaned.push({ role, content, at: parseReplayTime(row.createdAt) })
  }

  const out: ReplayBubble[] = []
  let i = 0
  while (i < cleaned.length) {
    const cur = cleaned[i]
    if (cur.role === 'user') {
      out.push({ role: 'user', content: cur.content, kind: 'text', at: cur.at })
      i += 1
      const assistants: { content: string; at?: number }[] = []
      while (i < cleaned.length && cleaned[i].role === 'assistant') {
        assistants.push({ content: cleaned[i].content, at: cleaned[i].at })
        i += 1
      }
      const agent = collapseAssistants(assistants, artifactKind)
      if (agent) out.push(agent)
      continue
    }

    const assistants: { content: string; at?: number }[] = []
    while (i < cleaned.length && cleaned[i].role === 'assistant') {
      assistants.push({ content: cleaned[i].content, at: cleaned[i].at })
      i += 1
    }
    const agent = collapseAssistants(assistants, artifactKind)
    if (agent) out.push(agent)
  }
  return out
}

function collapseAssistants(
  assistants: { content: string; at?: number }[],
  artifactKind: ReplayArtifactKind | null | undefined,
): ReplayBubble | null {
  if (!assistants.length) return null
  // HITL：同一轮可能先有策划 dump、后有执行 dump → 取最后一条成果 JSON
  let dump: { content: string; at?: number } | undefined
  for (let i = assistants.length - 1; i >= 0; i--) {
    if (isArtifactDumpContent(assistants[i].content)) {
      dump = assistants[i]
      break
    }
  }
  if (dump) {
    const kind = inferArtifactKindFromDump(dump.content, artifactKind)
    return {
      role: 'assistant',
      content: statusTextFor(kind),
      kind: 'artifact',
      at: dump.at,
    }
  }
  const last = assistants[assistants.length - 1]
  return { role: 'assistant', content: last.content, kind: 'text', at: last.at }
}
