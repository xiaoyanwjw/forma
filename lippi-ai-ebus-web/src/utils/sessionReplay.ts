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

function statusTextFor(artifactKind: 'picks' | 'listing' | null | undefined): string {
  if (artifactKind === 'listing') {
    return '已生成上架素材，右侧 Computer 可查看主图位与文案。'
  }
  return '已生成选品成果，右侧 Computer 可查看。'
}

/**
 * 从成果 dump 猜类型，避免整段回放共用「最新 Computer kind」导致选品被标成上架。
 * listing 信号优先；否则有选品条目信号 → picks；再回退到传入的 artifactKind。
 */
export function inferArtifactKindFromDump(
  content: string,
  fallback?: 'picks' | 'listing' | null,
): 'picks' | 'listing' | null {
  const t = content || ''
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
 * - STATUS 文案按该轮 dump 推断 picks/listing，不单靠最新 Computer kind
 */
export function toReplayBubbles(
  rows: SessionReplayRow[] | null | undefined,
  artifactKind?: 'picks' | 'listing' | null,
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
  artifactKind: 'picks' | 'listing' | null | undefined,
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
