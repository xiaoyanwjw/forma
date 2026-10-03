import type { ProcessEvent } from '@/composables/agent/agentProgress'

/** R1 回放行（来自 pi_session_entry 的 user/assistant/tool）。 */
export type SessionReplayRow = {
  role?: string
  content?: string
  createdAt?: string | null
  seq?: number | null
  toolCallId?: string | null
  toolCalls?: { id?: string | null; toolName?: string | null }[] | null
  /** 落库 append 键；HITL 可能为 runId:suspend / runId:resume */
  runId?: string | null
}

/** 成果 JSON 已由 Computer 展示，对话里不再铺原文。 */
export function isArtifactDumpContent(content: string): boolean {
  const t = content.trim()
  if (!t) return false
  if (/^```(?:json)?/i.test(t) && /"view"\s*:/.test(t)) return true
  if (t.startsWith('{')) {
    const head = t.slice(0, 500)
    return (
      /"view"\s*:/.test(head) &&
      (/"blocks"\s*:/.test(head) ||
        /"version"\s*:/.test(head) ||
        /"format"\s*:/.test(head) ||
        /"content"\s*:/.test(head))
    )
  }
  return false
}

function unwrapJsonCandidate(content: string): string {
  const t = content.trim()
  if (!t) return ''
  if (/^```(?:json)?/i.test(t)) {
    return t.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim()
  }
  return t
}

/**
 * Dual-track 终稿指针（对话里只有 `{"output":"final.json"}`，view 在盘上）。
 * 回放时与 dump 同等视为本轮成功标记。
 */
export function isOutputPointerContent(content: string): boolean {
  return Boolean(extractOutputPointerPath(content))
}

/** 解析 `{"output":"..."}` 指针路径；不是指针则 null。 */
export function extractOutputPointerPath(content: string): string | null {
  const body = unwrapJsonCandidate(content)
  if (!body.startsWith('{')) return null
  try {
    const obj = JSON.parse(body) as { output?: unknown }
    if (typeof obj.output === 'string' && obj.output.trim()) {
      return obj.output.trim()
    }
  } catch {
    const m = body.slice(0, 240).match(/"output"\s*:\s*"([^"]+)"/)
    if (m?.[1]?.trim()) return m[1].trim()
  }
  return null
}

/**
 * 对话里可展示的成功收尾：整包 dump，或非策划阶段的 output 指针。
 * listing 的 `plan/final.json` 之后还有 ask_human，不应落「已生成上架素材」。
 */
export function isDisplayableTurnSuccess(content: string): boolean {
  if (isArtifactDumpContent(content)) return true
  const path = extractOutputPointerPath(content)
  if (!path) return false
  if (/(^|\/)plan(\/|$)/i.test(path)) return false
  return true
}

/** ask_human 续跑写入会话的选项回执，不应作为用户气泡展示。 */
export function isHitlOptionUserContent(content: string): boolean {
  const body = unwrapJsonCandidate(content)
  if (!body.startsWith('{')) return false
  try {
    const obj = JSON.parse(body) as { optionId?: unknown }
    return typeof obj.optionId === 'string' && Boolean(obj.optionId.trim())
  } catch {
    return /"optionId"\s*:\s*"[^"]+"/.test(body.slice(0, 200))
  }
}

/** @deprecated 用 {@link isDisplayableTurnSuccess}；保留别名避免旧引用。 */
export function isTurnSuccessContent(content: string): boolean {
  return isDisplayableTurnSuccess(content) || Boolean(extractOutputPointerPath(content))
}

export type ReplayBubble = {
  role: 'user' | 'assistant'
  content: string
  /** artifact：渲染为成功 STATUS；text：普通气泡 */
  kind: 'text' | 'artifact'
  /** epoch ms；来自 entry.created_at */
  at?: number
  /** 本轮还原的 AGENT/LLM/TOOL，供 STATUS「过程」展开 */
  processEvents?: ProcessEvent[]
}

export type ReplayArtifactKind = 'picks' | 'listing' | 'topiclist' | 'note' | 'break'

type TurnRow = {
  role: 'assistant' | 'tool'
  content: string
  at?: number
  toolCallId?: string
  toolCalls?: { id?: string; toolName?: string }[]
}

export function keepReplayRow(row: SessionReplayRow | null | undefined): boolean {
  if (!row) return false
  const role = (row.role || '').toLowerCase()
  const text = typeof row.content === 'string' ? row.content.trim() : ''
  const hasToolMeta =
    Boolean(row.toolCallId?.trim()) ||
    (Array.isArray(row.toolCalls) && row.toolCalls.some((t) => t?.toolName?.trim()))
  if (role === 'tool') return true
  if (role === 'user') return Boolean(text)
  if (role === 'assistant' || role === 'agent') return Boolean(text) || hasToolMeta
  return false
}

export function parseReplayTime(createdAt?: string | null): number | undefined {
  if (!createdAt || typeof createdAt !== 'string') return undefined
  const ms = Date.parse(createdAt)
  return Number.isNaN(ms) ? undefined : ms
}

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

function toolNamesInTurn(turn: TurnRow[]): string[] {
  const names: string[] = []
  for (const row of turn) {
    for (const tc of row.toolCalls || []) {
      const name = tc.toolName?.trim()
      if (name) names.push(name)
    }
  }
  return names
}

function inferKindFromPrompt(prompt: string | undefined): ReplayArtifactKind | null {
  const t = (prompt || '').trim()
  if (!t) return null
  if (/上架素材|主图位|生图|详情标题|执行稿/.test(t)) return 'listing'
  if (/选品清单|测款|候选清单|帮我选/.test(t)) return 'picks'
  if (/爆文拆解|拆解/.test(t)) return 'break'
  if (/种草笔记|写一篇.*笔记|笔记草稿/.test(t)) return 'note'
  if (/选题清单|种草选题/.test(t)) return 'topiclist'
  return null
}

/** 按本轮证据推断类型；避免指针轮次误用会话「最新成果」kind。 */
export function inferArtifactKindForTurn(
  turn: TurnRow[],
  successContent: string,
  userPrompt?: string,
  fallback?: ReplayArtifactKind | null,
): ReplayArtifactKind | null {
  if (isArtifactDumpContent(successContent)) {
    return inferArtifactKindFromDump(successContent, fallback)
  }
  const path = extractOutputPointerPath(successContent) || ''
  if (/(^|\/)(plan|exec)(\/|$)/i.test(path)) {
    return 'listing'
  }
  const tools = toolNamesInTurn(turn)
  if (tools.includes('search_sku')) return 'picks'
  if (tools.includes('search_xhs_note')) return 'topiclist'
  if (tools.includes('ask_human')) return 'listing'
  const fromPrompt = inferKindFromPrompt(userPrompt)
  if (fromPrompt) return fromPrompt
  return fallback ?? null
}

/** 从一轮 assistant/tool 行还原过程事件（供历史「过程」入口）。 */
export function buildProcessEventsFromTurn(turn: TurnRow[]): ProcessEvent[] {
  if (!turn.length) return []
  const events: ProcessEvent[] = []
  let n = 0
  const startAt = turn[0]?.at ?? Date.now()
  events.push({
    id: `replay-agent-start-${n++}`,
    kind: 'agent',
    title: 'agent.start',
    at: startAt,
  })
  const nameByCallId = new Map<string, string>()

  for (const row of turn) {
    const at = row.at ?? startAt
    if (row.role === 'assistant') {
      for (const tc of row.toolCalls || []) {
        const id = tc.id?.trim()
        const name = tc.toolName?.trim()
        if (id && name) nameByCallId.set(id, name)
      }
      if (isDisplayableTurnSuccess(row.content) || extractOutputPointerPath(row.content)) {
        continue
      }
      const text = row.content?.trim()
      if (text) {
        events.push({
          id: `replay-llm-${n++}`,
          kind: 'llm',
          title: 'message',
          at,
          done: true,
          body: text,
        })
      }
      for (const tc of row.toolCalls || []) {
        const id = tc.id?.trim() || `replay-tool-${n++}`
        const name = tc.toolName?.trim() || 'tool'
        events.push({
          id,
          kind: 'tool',
          title: name,
          at,
          done: false,
        })
      }
      continue
    }
    if (row.role === 'tool') {
      const id = row.toolCallId?.trim() || `replay-tool-${n++}`
      const name = nameByCallId.get(id) || 'tool'
      const existing = events.find((e) => e.kind === 'tool' && e.id === id)
      if (existing) {
        existing.done = true
        existing.body = row.content
        existing.success = true
        existing.at = at
      } else {
        events.push({
          id,
          kind: 'tool',
          title: name,
          at,
          done: true,
          body: row.content,
          success: true,
        })
      }
    }
  }

  const endAt = turn[turn.length - 1]?.at ?? startAt
  events.push({
    id: `replay-agent-end-${n++}`,
    kind: 'agent',
    title: 'agent.end',
    at: endAt,
  })
  // 仅 start/end 不算有过程
  return events.length > 2 ? events : []
}

/** 去掉 HITL append 后缀，得到可聚类的逻辑 runId。 */
export function logicalRunId(runId?: string | null): string | null {
  const raw = typeof runId === 'string' ? runId.trim() : ''
  if (!raw) return null
  return raw.replace(/:(suspend|resume)$/i, '')
}

type CleanRow = {
  role: 'user' | 'assistant' | 'tool'
  content: string
  at?: number
  toolCallId?: string
  toolCalls?: { id?: string; toolName?: string }[]
  runId?: string
  clusterKey: string
}

function collectTurnFromRows(rows: CleanRow[]): TurnRow[] {
  const turn: TurnRow[] = []
  for (const r of rows) {
    if (r.role === 'assistant' || r.role === 'tool') {
      turn.push({
        role: r.role,
        content: r.content,
        at: r.at,
        toolCallId: r.toolCallId,
        toolCalls: r.toolCalls,
      })
    }
  }
  return turn
}

/**
 * 把 pi_session 的 user/assistant/tool 收成「用户 ↔ artifact STATUS」列表。
 * - 有 runId 时按逻辑 runId 聚类（`uuid:suspend` + `uuid:resume` 同一次 listing）
 * - 无 runId 时仍按 user 边界切分；HITL option 回执不展示
 * - 策划指针不落 STATUS；执行稿 / dump 落一条，过程事件含整簇
 */
export function toReplayBubbles(
  rows: SessionReplayRow[] | null | undefined,
  artifactKind?: ReplayArtifactKind | null,
): ReplayBubble[] {
  if (!rows) return []
  const cleaned: CleanRow[] = []
  let anon = 0
  let lastCluster: string | null = null
  for (const row of rows) {
    if (!keepReplayRow(row)) continue
    const roleRaw = (row.role || '').toLowerCase()
    const role =
      roleRaw === 'user' ? 'user' : roleRaw === 'tool' ? 'tool' : 'assistant'
    const content = typeof row.content === 'string' ? row.content.trim() : ''
    const toolCalls = Array.isArray(row.toolCalls)
      ? row.toolCalls
          .filter((t) => t && (t.id || t.toolName))
          .map((t) => ({
            id: t.id?.trim() || undefined,
            toolName: t.toolName?.trim() || undefined,
          }))
      : undefined
    const logic = logicalRunId(row.runId)
    let clusterKey: string
    if (logic) {
      clusterKey = `run:${logic}`
      lastCluster = clusterKey
    } else if (role === 'user' && !isHitlOptionUserContent(content)) {
      anon += 1
      clusterKey = `user:${anon}`
      lastCluster = clusterKey
    } else if (lastCluster) {
      clusterKey = lastCluster
    } else {
      anon += 1
      clusterKey = `user:${anon}`
      lastCluster = clusterKey
    }
    cleaned.push({
      role,
      content,
      at: parseReplayTime(row.createdAt),
      toolCallId: row.toolCallId?.trim() || undefined,
      toolCalls,
      runId: typeof row.runId === 'string' ? row.runId.trim() : undefined,
      clusterKey,
    })
  }

  const out: ReplayBubble[] = []
  let i = 0
  while (i < cleaned.length) {
    const key = cleaned[i].clusterKey
    const cluster: CleanRow[] = []
    while (i < cleaned.length && cleaned[i].clusterKey === key) {
      cluster.push(cleaned[i])
      i += 1
    }
    const bubbles = collapseCluster(cluster, artifactKind)
    out.push(...bubbles)
  }
  return out
}

function collapseCluster(
  cluster: CleanRow[],
  artifactKind: ReplayArtifactKind | null | undefined,
): ReplayBubble[] {
  if (!cluster.length) return []
  let userPrompt: string | undefined
  let userAt: number | undefined
  for (const row of cluster) {
    if (row.role === 'user' && !isHitlOptionUserContent(row.content)) {
      userPrompt = row.content
      userAt = row.at
      break
    }
  }
  const turn = collectTurnFromRows(cluster)
  const agent = collapseTurn(turn, userPrompt, artifactKind)
  const out: ReplayBubble[] = []
  if (userPrompt) {
    out.push({ role: 'user', content: userPrompt, kind: 'text', at: userAt })
  }
  if (agent) out.push(agent)
  return out
}

function collapseTurn(
  turn: TurnRow[],
  userPrompt: string | undefined,
  artifactKind: ReplayArtifactKind | null | undefined,
): ReplayBubble | null {
  const assistants = turn.filter((r) => r.role === 'assistant')
  if (!assistants.length && !turn.some((r) => r.role === 'tool')) {
    return null
  }
  let success: TurnRow | undefined
  for (let i = assistants.length - 1; i >= 0; i--) {
    if (isDisplayableTurnSuccess(assistants[i].content)) {
      success = assistants[i]
      break
    }
  }
  if (!success) {
    return null
  }
  const kind = inferArtifactKindForTurn(turn, success.content, userPrompt, artifactKind)
  const processEvents = buildProcessEventsFromTurn(turn)
  return {
    role: 'assistant',
    content: statusTextFor(kind),
    kind: 'artifact',
    at: success.at,
    processEvents: processEvents.length ? processEvents : undefined,
  }
}

/**
 * 后端已按逻辑 runId 聚成 turns；这里只按轮映射气泡（同轮 messages 再走 toReplayBubbles）。
 */
export function toReplayBubblesFromTurns(
  turns: { messages?: SessionReplayRow[] | null }[] | null | undefined,
  artifactKind?: ReplayArtifactKind | null,
): ReplayBubble[] {
  if (!turns?.length) return []
  const out: ReplayBubble[] = []
  for (const turn of turns) {
    const messages = Array.isArray(turn?.messages) ? turn.messages : []
    if (!messages.length) continue
    out.push(...toReplayBubbles(messages, artifactKind))
  }
  return out
}
