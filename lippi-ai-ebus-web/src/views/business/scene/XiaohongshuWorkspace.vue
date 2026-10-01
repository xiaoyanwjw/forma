<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import { ApiError } from '@/api/client'
import { getFeedbackByArtifact, submitFeedback } from '@/api/business/feedback/feedback'
import { getScenes } from '@/api/business/scene/scene'
import {
  getLatestSessionArtifact,
  getSessionMessages,
  listSessions,
} from '@/api/business/session/session'
import type { Page, SessionMessage, SessionSummary } from '@/types/business/session'
import {
  buildFailureDetail,
  canExpandProcessEvent,
  formatEventTime,
  formatStreamBodyForDisplay,
  processEventDisplayLabel,
  type ProcessEvent,
} from '@/composables/agent/agentProgress'
import { useAgentSkillRun } from '@/composables/agent/useAgentSkillRun'
import type { GenerationArtifactPayload } from '@/types/business/agent'
import { parseComputerDocument } from '@/types/business/computerView'
import { FEEDBACK_TAG_GOOD_QUALITY, FEEDBACK_TAG_POOR_QUALITY } from '@/types/business/feedback'
import { toReplayBubbles } from '@/utils/sessionReplay'
import { buildXhsBreakNoteHandoffText } from '@/utils/xhsNoteHandoff'
import type { HistoryArtifactDetail } from '@/types/business/history'
import '@/views/business/scene/ecommerceWorkspaceSession.css'

const SCENE_CODE = 'xiaohongshu' as const
const SCENE_BREADCRUMB = '小红书种草'
const ATTACH_SOON = '近端暂不支持附件'
const DEMO_SESSION_TITLE = '新任务'

const TOPIC_TEMPLATE = '请帮我生成「厨房收纳」类小红书种草选题清单，面向租房党。'
const NOTE_TEMPLATE = '请为商品「硅胶沥水垫」写一篇小红书种草笔记，语气像真人分享。'
const BREAK_TEMPLATE =
  '请拆解下面这篇笔记（分享链接或正文），并改写成我的商品「硅胶沥水垫」：…'

const TEMPLATE_SLOT_MARK = /【占位】/

type ComputerKind = 'topiclist' | 'note' | 'break' | null

const SKILL_BY_KIND: Record<Exclude<ComputerKind, null>, string> = {
  topiclist: 'xhs-topiclist',
  note: 'xhs-note',
  break: 'xhs-break',
}

interface ChatMessage {
  id: string
  role: 'user' | 'agent'
  text: string
  processEvents?: ProcessEvent[]
  statusDetail?: string
  failed?: boolean
  presentation?: 'bubble' | 'console'
  at?: number
}

const sessionPrompt = ref('')
const sceneBizId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const sessions = ref<SessionSummary[]>([])
const sessionsError = ref('')
const selectedSessionId = ref<string | null>(null)
const sessionTitle = ref(DEMO_SESSION_TITLE)
const sessionRawRows = ref<SessionMessage[]>([])
const sessionHasMore = ref(false)
const sessionNextToken = ref<string | null>(null)
const sessionHistoryLoading = ref(false)
const sessionReplayKind = ref<ComputerKind>(null)
const computerKind = ref<ComputerKind>(null)
const liveTopiclist = ref<GenerationArtifactPayload | null>(null)
const liveNote = ref<GenerationArtifactPayload | null>(null)
const liveBreak = ref<GenerationArtifactPayload | null>(null)
const chatScrollEl = ref<HTMLElement | null>(null)
const computerEl = ref<HTMLElement | null>(null)
const thinkingMessageId = ref<string | null>(null)
const expandedStreamIds = ref(new Set<string>())
const expandedStatusIds = ref(new Set<string>())

const {
  running: generationRunning,
  error: skillError,
  artifact: skillArtifact,
  sessionId,
  processEvents,
  startSkillRun,
  reset: resetSkillRun,
} = useAgentSkillRun()

const lastBilledPrompt = ref('')
const lastBilledKind = ref<ComputerKind>(null)
const pendingBilledPrompt = ref('')
/** Kind of the billed run currently streaming; do not attribute live artifacts via stale computerKind. */
const inFlightKind = ref<Exclude<ComputerKind, null> | null>(null)
const feedbackNote = ref('')
const feedbackBusy = ref(false)
const feedbackHint = ref('')
const feedbackTag = ref<string | null>(null)
const dislikeDrawerOpen = ref(false)
let feedbackOpSeq = 0
const localFeedbackSubmitSeq = new Map<string, number>()
let workspaceSwitchSeq = 0

const sessionBusy = computed(() => generationRunning.value)
const computerOpen = computed(() => computerKind.value != null)
const sessionSendEnabled = computed(
  () => sessionPrompt.value.trim().length > 0 && !sessionBusy.value,
)

const liveByKind = computed(() => ({
  topiclist: liveTopiclist.value,
  note: liveNote.value,
  break: liveBreak.value,
}))

const activeLiveArtifact = computed(() => {
  const kind = computerKind.value
  if (kind && liveByKind.value[kind]?.artifactRef) {
    return liveByKind.value[kind]
  }
  return liveNote.value || liveTopiclist.value || liveBreak.value
})

const canOneClickRetry = computed(
  () =>
    Boolean(lastBilledPrompt.value.trim()) &&
    Boolean(lastBilledKind.value) &&
    !sessionBusy.value,
)

const canSubmitFeedback = computed(
  () =>
    Boolean(activeLiveArtifact.value?.artifactRef) &&
    !sessionBusy.value &&
    !feedbackBusy.value,
)

const activeComputerDoc = computed(() => {
  const kind = computerKind.value
  if (!kind) return null
  return liveByKind.value[kind]?.view ?? null
})

let msgSeq = 0
function nextMsgId() {
  msgSeq += 1
  return `msg-${msgSeq}`
}

function scrollChatToBottom() {
  void nextTick(() => {
    const el = chatScrollEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function fillTopicSession() {
  sessionPrompt.value = TOPIC_TEMPLATE
}

function fillNoteSession() {
  sessionPrompt.value = NOTE_TEMPLATE
}

function fillBreakSession() {
  sessionPrompt.value = BREAK_TEMPLATE
}

function detectKind(text: string): ComputerKind {
  const t = text.trim()
  if (!t) return null
  if (/写一篇|笔记种草稿|种草笔记/.test(t) && !/选题清单/.test(t)) {
    return 'note'
  }
  if (/拆解|爆文/.test(t)) {
    return 'break'
  }
  if (/选题/.test(t)) {
    return 'topiclist'
  }
  return null
}

function applySoftCreditHint(reason: string): string {
  return /积分不足|额度不足|不足/.test(reason)
    ? `${reason}。可前往套餐页升级后再试。`
    : reason
}

function thinkingLabel(kind: Exclude<ComputerKind, null>): string {
  if (kind === 'note') return '正在生成笔记草稿…'
  if (kind === 'break') return '正在拆解爆文…'
  return '正在生成选题清单…'
}

function successFallback(kind: Exclude<ComputerKind, null>): string {
  if (kind === 'note') return '已生成笔记草稿，右侧 Computer 可查看。'
  if (kind === 'break') return '已生成爆文拆解，右侧 Computer 可查看。'
  return '已生成选题清单，右侧 Computer 可查看。'
}

function emptyFallback(kind: Exclude<ComputerKind, null>): string {
  if (kind === 'note') return '笔记生成已结束，但未收到可用草稿，请重试。'
  if (kind === 'break') return '拆解已结束，但未收到可用成果，请重试。'
  return '选题已结束，但未收到可用清单，请重试。'
}

function setLiveArtifact(kind: Exclude<ComputerKind, null>, payload: GenerationArtifactPayload) {
  if (kind === 'topiclist') liveTopiclist.value = payload
  else if (kind === 'note') liveNote.value = payload
  else liveBreak.value = payload
}

async function finishGenerationMessage(opts: {
  thinkingId: string
  error: string
  artifact: GenerationArtifactPayload | null
  kind: Exclude<ComputerKind, null>
  processSnapshot: ProcessEvent[] | undefined
}) {
  const idx = messages.value.findIndex((m) => m.id === opts.thinkingId)
  if (opts.error) {
    const soft = applySoftCreditHint(opts.error)
    const statusDetail = buildFailureDetail(soft, opts.processSnapshot || [])
    const failedMsg = {
      id: opts.thinkingId,
      role: 'agent' as const,
      text: soft,
      processEvents: opts.processSnapshot,
      failed: true,
      statusDetail,
    }
    if (idx >= 0) {
      messages.value[idx] = failedMsg
    } else {
      messages.value.push({ ...failedMsg, id: nextMsgId() })
    }
    thinkingMessageId.value = null
    scrollChatToBottom()
    return
  }

  if (opts.artifact?.view) {
    setLiveArtifact(opts.kind, opts.artifact)
    computerKind.value = opts.kind
    if (pendingBilledPrompt.value.trim()) {
      lastBilledPrompt.value = pendingBilledPrompt.value.trim()
      lastBilledKind.value = opts.kind
    }
    feedbackHint.value = ''
    revealComputer()
    void loadSessions()
    const reply = successFallback(opts.kind)
    if (idx >= 0) {
      messages.value[idx] = {
        id: opts.thinkingId,
        role: 'agent',
        text: reply,
        processEvents: opts.processSnapshot,
      }
    } else {
      messages.value.push({
        id: nextMsgId(),
        role: 'agent',
        text: reply,
        processEvents: opts.processSnapshot,
      })
    }
  } else {
    const statusDetail = buildFailureDetail(emptyFallback(opts.kind), opts.processSnapshot || [])
    const failedMsg = {
      id: opts.thinkingId,
      role: 'agent' as const,
      text: emptyFallback(opts.kind),
      processEvents: opts.processSnapshot,
      failed: true,
      statusDetail,
    }
    if (idx >= 0) {
      messages.value[idx] = failedMsg
    } else {
      messages.value.push({ ...failedMsg, id: nextMsgId() })
    }
  }
  thinkingMessageId.value = null
  scrollChatToBottom()
}

async function sendFromSession() {
  const text = sessionPrompt.value.trim()
  if (!text || sessionBusy.value) return

  if (TEMPLATE_SLOT_MARK.test(text)) {
    messages.value.push({ id: nextMsgId(), role: 'user', text })
    sessionPrompt.value = ''
    messages.value.push({
      id: nextMsgId(),
      role: 'agent',
      text: '请先把【占位】改成真实品类或商品，或点上方胶囊使用示例后再发送。',
    })
    scrollChatToBottom()
    return
  }

  messages.value.push({ id: nextMsgId(), role: 'user', text })
  sessionPrompt.value = ''
  scrollChatToBottom()

  const kind = detectKind(text)
  if (!kind) {
    messages.value.push({
      id: nextMsgId(),
      role: 'agent',
      text: '近端拿手是「选题清单」「笔记种草稿」「爆文拆解」。请用上方胶囊，或直接描述品类、商品或要拆解的笔记。',
    })
    scrollChatToBottom()
    return
  }

  await runBilledGeneration(text, kind)
}

async function onNoteHandoff(payload: { text: string }) {
  const text = payload.text?.trim()
  if (!text || sessionBusy.value) return
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  scrollChatToBottom()
  await runBilledGeneration(text, 'note')
}

function onBreakHandoff() {
  if (sessionBusy.value) return
  void onNoteHandoff({ text: buildXhsBreakNoteHandoffText() })
}

async function runBilledGeneration(text: string, kind: Exclude<ComputerKind, null>) {
  pendingBilledPrompt.value = text
  inFlightKind.value = kind
  computerKind.value = kind
  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: thinkingLabel(kind),
  })
  scrollChatToBottom()

  try {
    await startSkillRun({
      text,
      skillId: SKILL_BY_KIND[kind],
      sceneCode: SCENE_CODE,
      sceneId: sceneBizId.value ?? undefined,
      sessionId: sessionId.value ?? undefined,
    })
    await finishGenerationMessage({
      thinkingId,
      error: skillError.value,
      artifact: skillArtifact.value,
      kind,
      processSnapshot: processEvents.value.length ? [...processEvents.value] : undefined,
    })
  } finally {
    inFlightKind.value = null
  }
}

async function oneClickRetry() {
  if (!canOneClickRetry.value || !lastBilledKind.value) return
  const text = lastBilledPrompt.value.trim()
  const kind = lastBilledKind.value
  messages.value.push({ id: nextMsgId(), role: 'user', text: `重试：${text}` })
  scrollChatToBottom()
  await runBilledGeneration(text, kind)
}

async function submitLikeFeedback() {
  const artifactRef = activeLiveArtifact.value?.artifactRef?.trim()
  if (!artifactRef || !canSubmitFeedback.value) return
  feedbackBusy.value = true
  feedbackHint.value = ''
  try {
    await submitFeedback({
      artifactId: artifactRef,
      tag: FEEDBACK_TAG_GOOD_QUALITY,
    })
    markLocalFeedbackSubmit(artifactRef)
    feedbackTag.value = FEEDBACK_TAG_GOOD_QUALITY
    feedbackHint.value = '已记录「质量好」反馈，不影响积分。'
  } catch (e) {
    feedbackHint.value = e instanceof ApiError ? e.message : '反馈提交失败'
  } finally {
    feedbackBusy.value = false
  }
}

function openDislikeDrawer() {
  if (!activeLiveArtifact.value?.artifactRef || sessionBusy.value) return
  dislikeDrawerOpen.value = true
}

function closeDislikeDrawer() {
  dislikeDrawerOpen.value = false
}

async function submitPoorQualityFeedback() {
  const artifactRef = activeLiveArtifact.value?.artifactRef?.trim()
  if (!artifactRef || !canSubmitFeedback.value) return
  feedbackBusy.value = true
  feedbackHint.value = ''
  try {
    await submitFeedback({
      artifactId: artifactRef,
      tag: FEEDBACK_TAG_POOR_QUALITY,
      commentText: feedbackNote.value.trim() || undefined,
    })
    markLocalFeedbackSubmit(artifactRef)
    feedbackTag.value = FEEDBACK_TAG_POOR_QUALITY
    feedbackNote.value = ''
    dislikeDrawerOpen.value = false
    feedbackHint.value = '已记录「质量差」反馈，不影响积分。'
  } catch (e) {
    feedbackHint.value = e instanceof ApiError ? e.message : '反馈提交失败'
  } finally {
    feedbackBusy.value = false
  }
}

function revealComputer() {
  void nextTick(() => {
    computerEl.value?.scrollIntoView({ block: 'nearest' })
  })
}

function openComputer(kind: Exclude<ComputerKind, null>) {
  computerKind.value = kind
  revealComputer()
}

function previewKindFromStatus(m: ChatMessage): ComputerKind {
  if (/笔记草稿/.test(m.text)) return 'note'
  if (/爆文拆解/.test(m.text)) return 'break'
  if (/选题/.test(m.text)) return 'topiclist'
  return null
}

function canPreviewFromStatus(m: ChatMessage): boolean {
  if (m.failed) return false
  if (!/已生成/.test(m.text)) return false
  const kind = previewKindFromStatus(m)
  if (kind) return Boolean(liveByKind.value[kind]?.view)
  return Boolean(liveTopiclist.value?.view || liveNote.value?.view || liveBreak.value?.view)
}

function currentFeedbackArtifactId() {
  return activeLiveArtifact.value?.artifactRef?.trim() || ''
}

function markLocalFeedbackSubmit(artifactId: string) {
  const seq = ++feedbackOpSeq
  localFeedbackSubmitSeq.set(artifactId, seq)
}

function applyFeedbackRestore(seq: number, artifactId: string, tag: string | null) {
  if (currentFeedbackArtifactId() !== artifactId) return
  const localSeq = localFeedbackSubmitSeq.get(artifactId) ?? 0
  if (localSeq > seq) return
  feedbackTag.value = tag
}

function isLatestPreviewableStatus(m: ChatMessage): boolean {
  if (!canPreviewFromStatus(m)) return false
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const cur = messages.value[i]
    if (cur && canPreviewFromStatus(cur)) {
      return cur.id === m.id
    }
  }
  return false
}

watch(
  () => activeLiveArtifact.value?.artifactRef?.trim() || '',
  async (artifactId) => {
    if (!artifactId) {
      feedbackOpSeq += 1
      feedbackTag.value = null
      return
    }
    const seq = ++feedbackOpSeq
    try {
      const existing = await getFeedbackByArtifact(artifactId)
      applyFeedbackRestore(seq, artifactId, existing?.tag?.trim() || null)
    } catch {
      applyFeedbackRestore(seq, artifactId, null)
    }
  },
)

function isPreviewOpenFromStatus(m: ChatMessage): boolean {
  const kind = previewKindFromStatus(m) || computerKind.value
  return Boolean(kind && canPreviewFromStatus(m) && computerKind.value === kind)
}

function canExpandStatus(m: ChatMessage): boolean {
  return Boolean(m.failed && (m.statusDetail || m.text))
}

function onStatusCardClick(m: ChatMessage) {
  if (canPreviewFromStatus(m)) {
    if (isPreviewOpenFromStatus(m)) {
      closeComputer()
    } else {
      const kind = previewKindFromStatus(m)
      if (kind) openComputer(kind)
    }
    return
  }
  if (canExpandStatus(m)) {
    toggleStatusExpand(m.id)
  }
}

function onProcessEventClick(e: ProcessEvent) {
  if (canExpandProcessEvent(e)) {
    toggleStreamExpand(e.id)
  }
}

function closeComputer() {
  computerKind.value = null
}

function replayMessagesFromApi(
  rows: SessionMessage[] | null | undefined,
  artifactKind: ComputerKind,
): ChatMessage[] {
  return toReplayBubbles(rows, artifactKind).map((bubble) => {
    if (bubble.role === 'user') {
      return {
        id: nextMsgId(),
        role: 'user' as const,
        text: bubble.content,
        at: bubble.at,
      }
    }
    if (bubble.kind === 'artifact') {
      return {
        id: nextMsgId(),
        role: 'agent' as const,
        text: bubble.content,
        presentation: 'console' as const,
        at: bubble.at,
      }
    }
    return {
      id: nextMsgId(),
      role: 'agent' as const,
      text: bubble.content,
      presentation: 'bubble' as const,
      at: bubble.at,
    }
  })
}

function applyMessagePage(page: Page<SessionMessage> | null | undefined, mode: 'replace' | 'prepend') {
  const items = Array.isArray(page?.items) ? page!.items : []
  if (mode === 'replace') {
    sessionRawRows.value = items
  } else {
    sessionRawRows.value = [...items, ...sessionRawRows.value]
  }
  const token = page?.nextToken?.trim() || null
  sessionNextToken.value = token
  sessionHasMore.value = Boolean(token)
}

function paintSessionReplay(kind: ComputerKind) {
  sessionReplayKind.value = kind
  const replayed = replayMessagesFromApi(sessionRawRows.value, kind)
  lastBilledKind.value = kind
  lastBilledPrompt.value = kind
    ? lastUserPromptFromReplay(replayed, sessionTitle.value || DEMO_SESSION_TITLE)
    : ''
  const hasArtifactStatus = replayed.some(
    (m) => m.role === 'agent' && m.presentation === 'console' && /已生成/.test(m.text),
  )
  const status = !hasArtifactStatus ? synthesizePreviewableStatus(kind) : null
  messages.value = status ? [...replayed, status] : replayed
}

function isConsoleMessage(m: ChatMessage): boolean {
  if (m.role !== 'agent') return false
  if (m.presentation === 'bubble') return false
  if (m.presentation === 'console') return true
  return Boolean(
    m.processEvents?.length ||
      canPreviewFromStatus(m) ||
      canExpandStatus(m) ||
      (generationRunning.value && m.id === thinkingMessageId.value),
  )
}

function kindFromArtifactType(artifactType?: string | null): ComputerKind {
  if (artifactType === 'xhs_note') return 'note'
  if (artifactType === 'xhs_break') return 'break'
  if (artifactType === 'xhs_topiclist') return 'topiclist'
  return null
}

function lastUserPromptFromReplay(rows: ChatMessage[], titleFallback: string): string {
  for (let i = rows.length - 1; i >= 0; i--) {
    const cur = rows[i]
    if (cur?.role === 'user' && cur.text.trim()) {
      return cur.text.trim()
    }
  }
  return titleFallback.trim()
}

function synthesizePreviewableStatus(kind: ComputerKind): ChatMessage | null {
  if (!kind) return null
  return {
    id: nextMsgId(),
    role: 'agent',
    presentation: 'console',
    text: successFallback(kind),
  }
}

function mergeSessionArtifact(
  detail: HistoryArtifactDetail | null | undefined,
  openComputer = false,
): ComputerKind {
  if (!detail?.id || !detail.view) {
    return null
  }
  const view = parseComputerDocument(detail.view)
  if (!view) {
    return null
  }
  const kind = kindFromArtifactType(detail.artifactType) || 'topiclist'
  const payload: GenerationArtifactPayload = { artifactRef: detail.id, view }
  setLiveArtifact(kind, payload)
  if (openComputer) {
    computerKind.value = kind
    revealComputer()
  }
  return kind
}

function clearSessionArtifacts() {
  liveTopiclist.value = null
  liveNote.value = null
  liveBreak.value = null
  computerKind.value = null
}

async function loadSessions() {
  try {
    const data = await listSessions(SCENE_CODE)
    sessions.value = Array.isArray(data) ? data : []
    sessionsError.value = ''
  } catch (e) {
    sessions.value = []
    sessionsError.value = e instanceof ApiError ? e.message : '会话加载失败'
  }
}

async function selectSession(item: SessionSummary) {
  const sid = item.sessionId?.trim()
  if (!sid) return
  const seq = ++workspaceSwitchSeq
  resetSkillRun()
  thinkingMessageId.value = null
  expandedStreamIds.value = new Set()
  expandedStatusIds.value = new Set()
  sessionId.value = sid
  selectedSessionId.value = sid
  sessionTitle.value = item.title || DEMO_SESSION_TITLE
  messages.value = []
  sessionRawRows.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayKind.value = null
  lastBilledPrompt.value = ''
  lastBilledKind.value = null
  pendingBilledPrompt.value = ''
  inFlightKind.value = null
  feedbackNote.value = ''
  feedbackHint.value = ''
  feedbackTag.value = null
  dislikeDrawerOpen.value = false
  feedbackBusy.value = false
  feedbackOpSeq += 1
  localFeedbackSubmitSeq.clear()
  clearSessionArtifacts()
  try {
    const [page, latest, topicArt, noteArt, breakArt] = await Promise.all([
      getSessionMessages(sid),
      getLatestSessionArtifact(sid),
      getLatestSessionArtifact(sid, 'xhs_topiclist'),
      getLatestSessionArtifact(sid, 'xhs_note'),
      getLatestSessionArtifact(sid, 'xhs_break'),
    ])
    if (seq !== workspaceSwitchSeq) return
    clearSessionArtifacts()
    mergeSessionArtifact(topicArt, false)
    mergeSessionArtifact(noteArt, false)
    mergeSessionArtifact(breakArt, false)
    const kind =
      kindFromArtifactType(latest?.artifactType) ||
      (noteArt?.view ? 'note' : breakArt?.view ? 'break' : topicArt?.view ? 'topiclist' : null)
    if (kind && liveByKind.value[kind]?.view) {
      computerKind.value = kind
      revealComputer()
    }
    applyMessagePage(page, 'replace')
    paintSessionReplay(kind)
    scrollChatToBottom()
  } catch (e) {
    if (seq !== workspaceSwitchSeq) return
    feedbackHint.value = e instanceof ApiError ? e.message : '会话加载失败'
  }
}

async function loadMoreSessionHistory() {
  const sid = selectedSessionId.value?.trim()
  const token = sessionNextToken.value
  if (
    !sid ||
    !sessionHasMore.value ||
    !token ||
    sessionHistoryLoading.value ||
    sessionBusy.value
  ) {
    return
  }
  const switchSeq = workspaceSwitchSeq
  sessionHistoryLoading.value = true
  const el = chatScrollEl.value
  const prevHeight = el?.scrollHeight ?? 0
  try {
    const page = await getSessionMessages(sid, { nextToken: token })
    if (switchSeq !== workspaceSwitchSeq) return
    applyMessagePage(page, 'prepend')
    paintSessionReplay(sessionReplayKind.value)
    await nextTick()
    if (el) {
      el.scrollTop = Math.max(0, el.scrollHeight - prevHeight)
    }
  } catch (e) {
    if (switchSeq !== workspaceSwitchSeq) return
    feedbackHint.value = e instanceof ApiError ? e.message : '加载更多失败'
  } finally {
    if (switchSeq === workspaceSwitchSeq) {
      sessionHistoryLoading.value = false
    }
  }
}

function newTask() {
  workspaceSwitchSeq += 1
  resetSkillRun()
  thinkingMessageId.value = null
  expandedStreamIds.value = new Set()
  expandedStatusIds.value = new Set()
  messages.value = []
  sessionRawRows.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayKind.value = null
  computerKind.value = null
  liveTopiclist.value = null
  liveNote.value = null
  liveBreak.value = null
  sessionPrompt.value = ''
  sessionTitle.value = DEMO_SESSION_TITLE
  selectedSessionId.value = null
  lastBilledPrompt.value = ''
  lastBilledKind.value = null
  pendingBilledPrompt.value = ''
  inFlightKind.value = null
  feedbackNote.value = ''
  feedbackHint.value = ''
  feedbackTag.value = null
  dislikeDrawerOpen.value = false
  feedbackBusy.value = false
  feedbackOpSeq += 1
  localFeedbackSubmitSeq.clear()
  void loadSessions()
}

function toggleStreamExpand(id: string) {
  const next = new Set(expandedStreamIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedStreamIds.value = next
}

function toggleStatusExpand(id: string) {
  const next = new Set(expandedStatusIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedStatusIds.value = next
}

function eventTag(kind: ProcessEvent['kind']): string {
  if (kind === 'agent') return 'AGENT'
  if (kind === 'llm') return 'LLM'
  return 'TOOL'
}

function eventTitle(e: ProcessEvent): string {
  return processEventDisplayLabel(e.title)
}

watch(skillArtifact, (value) => {
  const kind = inFlightKind.value
  if (value?.view && kind) {
    setLiveArtifact(kind, value)
    computerKind.value = kind
  }
})

watch(processEvents, () => {
  if (!generationRunning.value || !thinkingMessageId.value) return
  const idx = messages.value.findIndex((m) => m.id === thinkingMessageId.value)
  if (idx < 0) return
  const cur = messages.value[idx]
  if (!cur) return
  messages.value[idx] = {
    id: cur.id,
    role: cur.role,
    text: cur.text,
    processEvents: processEvents.value.length ? [...processEvents.value] : undefined,
  }
})

watch(sessionId, (id, prev) => {
  selectedSessionId.value = id
  if (id && id !== prev) {
    void loadSessions()
  }
})

onMounted(async () => {
  try {
    const data = await getScenes()
    const hit = Array.isArray(data)
      ? data.find((s) => s.sceneCode === SCENE_CODE && s.status === 'AVAILABLE')
      : undefined
    if (hit?.bizId) {
      sceneBizId.value = hit.bizId
    }
  } catch {
    // Empty UI still works with sceneCode alone
  }
  void loadSessions()
})
</script>

<template>
  <div
    class="shell"
    :data-scene-code="SCENE_CODE"
    :data-scene-biz-id="sceneBizId ?? undefined"
  >
    <AppHeader :scene-breadcrumb="SCENE_BREADCRUMB" :hide-secondary-nav="true" />

    <div class="session" data-testid="session-shell">
      <aside class="sidebar" aria-label="会话侧栏">
        <button type="button" class="side-new" @click="newTask">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          新任务
        </button>
        <div class="side-section">会话</div>
        <p v-if="sessionsError" class="sessions-error" data-testid="sessions-error">{{ sessionsError }}</p>
        <div class="session-list" data-testid="session-list">
          <button
            v-for="s in sessions"
            :key="s.sessionId"
            type="button"
            class="side-item"
            :class="{ on: selectedSessionId === s.sessionId }"
            data-testid="session-item"
            @click="selectSession(s)"
          >
            {{ s.title }}
            <span class="sub">小红书种草</span>
          </button>
        </div>
      </aside>

      <div class="workspace" :class="{ split: computerOpen }">
        <section class="chat-pane">
          <div class="chat-toolbar">
            <button type="button" class="chat-new-task" @click="newTask">新任务</button>
          </div>
          <div ref="chatScrollEl" class="chat-scroll" role="log" aria-live="polite">
            <div v-if="sessionHasMore" class="chat-load-more">
              <button
                type="button"
                class="chat-load-more-btn"
                data-testid="session-load-more"
                :disabled="sessionHistoryLoading || sessionBusy"
                @click="loadMoreSessionHistory"
              >
                {{ sessionHistoryLoading ? '加载中…' : '加载更多' }}
              </button>
            </div>
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="m.role"
            >
              <div
                class="msg-avatar"
                :aria-label="m.role === 'user' ? '你' : 'Adam'"
                role="img"
              >
                <svg
                  v-if="m.role === 'user'"
                  class="msg-avatar-icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    fill="currentColor"
                    d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v1.2c0 .7.5 1.2 1.2 1.2h16.8c.7 0 1.2-.5 1.2-1.2v-1.2c0-3.2-6.4-4.8-9.6-4.8z"
                  />
                </svg>
                <span v-else class="msg-avatar-letter" aria-hidden="true">A</span>
              </div>
              <div class="body">
                <p v-if="m.role === 'user'" class="msg-text">
                  {{ m.text }}
                </p>
                <p v-else-if="!isConsoleMessage(m)" class="msg-text agent-text">
                  <span v-if="m.at" class="msg-time">{{ formatEventTime(m.at) }}</span>
                  {{ m.text }}
                </p>
                <div
                  v-else-if="m.processEvents?.length || m.text"
                  class="chat-console"
                  :class="{
                    'is-running': generationRunning && m.id === thinkingMessageId,
                  }"
                  aria-label="运行日志"
                  :aria-busy="generationRunning && m.id === thinkingMessageId ? 'true' : undefined"
                >
                  <div
                    v-if="m.processEvents?.length"
                    class="chat-events"
                    aria-label="过程事件"
                  >
                    <div
                      v-for="e in m.processEvents"
                      :key="e.id"
                      class="chat-event"
                      :class="{
                        active: e.kind === 'tool' && !e.done,
                        'chat-event-llm': e.kind === 'llm',
                        'chat-event-tool': e.kind === 'tool',
                        open: canExpandProcessEvent(e) && expandedStreamIds.has(e.id),
                        expandable: canExpandProcessEvent(e),
                      }"
                      role="button"
                      tabindex="0"
                      @click="onProcessEventClick(e)"
                      @keydown.enter.prevent="onProcessEventClick(e)"
                    >
                      <span class="chat-event-time">{{ formatEventTime(e.at) }}</span>
                      <span
                        class="chat-event-tag"
                        :class="{
                          'tag-agent': e.kind === 'agent',
                          'tag-llm': e.kind === 'llm',
                          'tag-tool': e.kind === 'tool',
                        }"
                      >{{ eventTag(e.kind) }}</span>
                      <div class="chat-event-main">
                        <div
                          class="chat-event-head"
                          :aria-expanded="
                            canExpandProcessEvent(e) ? expandedStreamIds.has(e.id) : undefined
                          "
                        >
                          <span class="chat-event-title">{{ eventTitle(e) }}</span>
                          <span v-if="canExpandProcessEvent(e)" class="chat-stream-toggle">{{
                            expandedStreamIds.has(e.id) ? '[-]' : '[+]'
                          }}</span>
                          <span
                            v-else-if="e.kind === 'tool' && !e.done"
                            class="chat-stream-toggle"
                          >…</span>
                        </div>
                      </div>
                      <pre
                        v-if="canExpandProcessEvent(e) && expandedStreamIds.has(e.id)"
                        class="chat-stream-body"
                        @click.stop
                      >{{ formatStreamBodyForDisplay(e.body || '') }}</pre>
                    </div>
                  </div>
                  <div
                    v-if="m.text && !(generationRunning && m.id === thinkingMessageId)"
                    class="chat-event chat-event-status"
                    :class="{
                      'is-preview': canPreviewFromStatus(m),
                      'is-failed': canExpandStatus(m),
                      open:
                        isPreviewOpenFromStatus(m) ||
                        (canExpandStatus(m) && expandedStatusIds.has(m.id)),
                      expandable: canPreviewFromStatus(m) || canExpandStatus(m),
                    }"
                    role="button"
                    tabindex="0"
                    :aria-disabled="!(canPreviewFromStatus(m) || canExpandStatus(m))"
                    :aria-expanded="
                      canPreviewFromStatus(m)
                        ? isPreviewOpenFromStatus(m)
                        : canExpandStatus(m)
                          ? expandedStatusIds.has(m.id)
                          : undefined
                    "
                    @click="onStatusCardClick(m)"
                    @keydown.enter.prevent="onStatusCardClick(m)"
                  >
                    <span class="chat-event-time">{{
                      formatEventTime(
                        m.at ??
                          m.processEvents?.[m.processEvents.length - 1]?.at ??
                          Date.now(),
                      )
                    }}</span>
                    <span class="chat-event-tag tag-status">{{
                      canExpandStatus(m) ? 'FAIL' : 'OK'
                    }}</span>
                    <div class="chat-event-main">
                      <div class="chat-event-head">
                        <span class="chat-event-title chat-result-text">{{ m.text }}</span>
                        <span v-if="canPreviewFromStatus(m)" class="chat-stream-toggle">{{
                          isPreviewOpenFromStatus(m) ? '关闭' : '查看'
                        }}</span>
                        <span v-else-if="canExpandStatus(m)" class="chat-stream-toggle">{{
                          expandedStatusIds.has(m.id) ? '[-]' : '[+]'
                        }}</span>
                      </div>
                    </div>
                    <pre
                      v-if="canExpandStatus(m) && expandedStatusIds.has(m.id)"
                      class="chat-stream-body"
                      @click.stop
                    >{{ formatStreamBodyForDisplay(m.statusDetail || m.text) }}</pre>
                  </div>
                </div>
                <div
                  v-if="isLatestPreviewableStatus(m)"
                  class="card-result-actions"
                  data-testid="card-result-actions"
                  @click.stop
                >
                  <button
                    type="button"
                    class="card-action-icon"
                    data-testid="one-click-retry"
                    aria-label="重试"
                    title="重试"
                    :disabled="!canOneClickRetry"
                    @click="oneClickRetry"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path
                        fill="currentColor"
                        d="M12 6V3L8 7l4 4V8c2.76 0 5 2.24 5 5a5 5 0 0 1-9.9 1h-2.02A7 7 0 0 0 12 20c3.87 0 7-3.13 7-7s-3.13-7-7-7z"
                      />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="card-action-icon"
                    data-testid="card-like"
                    aria-label="点赞"
                    title="点赞"
                    :class="{ 'is-on': feedbackTag === FEEDBACK_TAG_GOOD_QUALITY }"
                    :disabled="!canSubmitFeedback"
                    @click="submitLikeFeedback"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path
                        fill="currentColor"
                        d="M9 21h9a2 2 0 0 0 1.86-1.26l2.7-7.05A1.5 1.5 0 0 0 21.18 10H14V6a3 3 0 0 0-3-3l-4 9v9zm-6 0h4V12H3v9z"
                      />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="card-action-icon"
                    data-testid="card-dislike"
                    aria-label="点踩"
                    title="点踩"
                    :class="{ 'is-on': feedbackTag === FEEDBACK_TAG_POOR_QUALITY }"
                    :disabled="sessionBusy || feedbackBusy || !activeLiveArtifact?.artifactRef"
                    @click="openDislikeDrawer"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path
                        fill="currentColor"
                        d="M15 3H6a2 2 0 0 0-1.86 1.26l-2.7 7.05A1.5 1.5 0 0 0 2.82 14H10v4a3 3 0 0 0 3 3l4-9V3zm6 0h-4v9h4V3z"
                      />
                    </svg>
                  </button>
                  <p v-if="feedbackHint" class="feedback-hint" data-testid="feedback-hint">
                    {{ feedbackHint }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input-wrap">
            <div class="chat-composer">
              <div
                class="quick-row"
                role="group"
                aria-label="快捷任务"
                data-testid="session-quick-row"
              >
                <button type="button" class="pill" :disabled="sessionBusy" @click="fillTopicSession">
                  选题清单
                </button>
                <button type="button" class="pill" :disabled="sessionBusy" @click="fillNoteSession">
                  笔记种草稿
                </button>
                <button type="button" class="pill" :disabled="sessionBusy" @click="fillBreakSession">
                  爆文拆解
                </button>
              </div>
              <div class="prompt-box">
                <textarea
                  v-model="sessionPrompt"
                  class="prompt-editor"
                  rows="2"
                  placeholder="分配一个任务或提问任何问题"
                  aria-label="继续提问"
                  :disabled="sessionBusy"
                />
                <div class="prompt-toolbar">
                  <button
                    type="button"
                    class="icon-btn"
                    aria-label="附件"
                    aria-describedby="session-attach-soon-hint"
                    disabled
                  >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                      <path d="M12 5v14M5 12h14" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="send-btn"
                    :class="{ active: sessionSendEnabled }"
                    aria-label="发送"
                    :disabled="!sessionSendEnabled"
                    @click="sendFromSession"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                      <path d="M12 19V5M5 12l7-7 7 7" />
                    </svg>
                  </button>
                </div>
                <p id="session-attach-soon-hint" class="sr-only">{{ ATTACH_SOON }}</p>
              </div>
            </div>
          </div>
        </section>

        <aside ref="computerEl" class="computer" id="computer-xhs" aria-label="Adam's Computer">
          <div class="computer-bar">
            <div class="title">
              <span class="dots" aria-hidden="true"><i /><i /><i /></span>
              Adam's Computer
            </div>
            <div class="computer-bar-actions">
              <button
                v-if="computerKind === 'break' && liveBreak?.view"
                type="button"
                class="pill"
                data-testid="break-note-handoff"
                :disabled="sessionBusy"
                @click="onBreakHandoff"
              >
                按骨架写笔记
              </button>
              <button type="button" class="icon-btn" aria-label="关闭" @click="closeComputer">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
          <div class="computer-body">
            <ComputerRenderer
              v-if="activeComputerDoc"
              :document="activeComputerDoc"
              :enable-note-handoff="computerKind === 'topiclist'"
              @note-handoff="onNoteHandoff"
            />
          </div>
        </aside>
      </div>
    </div>
  </div>
  <div
    v-if="dislikeDrawerOpen"
    class="dislike-drawer"
    data-testid="dislike-drawer"
    @click.stop
  >
    <p class="dislike-drawer-title">这次成果哪里不好？</p>
    <textarea
      v-model="feedbackNote"
      class="dislike-comment"
      data-testid="dislike-comment"
      rows="4"
      maxlength="512"
      placeholder="可选短文说明"
      aria-label="质量差短文"
      :disabled="feedbackBusy"
    />
    <div class="dislike-drawer-actions">
      <button type="button" class="pill" data-testid="dislike-cancel" @click="closeDislikeDrawer">
        取消
      </button>
      <button
        type="button"
        class="pill"
        data-testid="dislike-submit"
        :disabled="!canSubmitFeedback"
        @click="submitPoorQualityFeedback"
      >
        提交
      </button>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
  margin: 0 0 10px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
  font-size: 0.8125rem;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.pill:hover:not(:disabled) {
  background: var(--line-2);
}

.pill:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.prompt-box {
  width: 100%;
  background: var(--surface);
  border: 1px solid color-mix(in srgb, var(--line) 85%, transparent);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow);
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.prompt-box:focus-within {
  border-color: color-mix(in srgb, #0f766e 35%, var(--line));
  box-shadow:
    var(--shadow),
    0 0 0 3px color-mix(in srgb, #0f766e 12%, transparent);
}

.prompt-editor {
  border: 0;
  outline: none;
  resize: none;
  min-height: 56px;
  background: transparent;
  color: var(--ink);
  font-size: 0.95rem;
  line-height: 1.7;
  width: 100%;
}

.prompt-editor:focus {
  outline: none;
}

.prompt-editor::placeholder {
  color: var(--mute-2);
}

.prompt-editor:disabled {
  opacity: 0.7;
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.icon-btn,
.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 0;
  display: grid;
  place-items: center;
  color: var(--mute);
}

.icon-btn {
  background: transparent;
  cursor: not-allowed;
}

.computer-bar .icon-btn {
  cursor: pointer;
}

.computer-bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-btn {
  background: var(--line-2);
  cursor: pointer;
}

.send-btn.active {
  background: var(--accent);
  color: #fff;
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.icon-btn:disabled {
  opacity: 0.7;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.card-result-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  padding: 0 2px;
}

.card-action-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--mute);
  cursor: pointer;
  transition:
    color 0.15s,
    background 0.15s;
}

.card-action-icon svg {
  width: 16px;
  height: 16px;
  display: block;
}

.card-action-icon:hover:not(:disabled) {
  color: var(--ink);
  background: color-mix(in srgb, var(--chip) 80%, transparent);
}

.card-action-icon.is-on {
  color: var(--accent);
}

.card-action-icon:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.feedback-hint {
  flex: 1 1 100%;
  margin: 0;
  font-size: 0.75rem;
  color: var(--mute);
}

.dislike-drawer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  padding: 16px 18px 20px;
  border-top: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.08);
}

.dislike-drawer-title {
  margin: 0 0 10px;
  font-size: 0.9rem;
  color: var(--ink);
}

.dislike-comment {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  background: transparent;
  color: var(--ink);
  font-size: 0.8125rem;
  resize: vertical;
}

.dislike-drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
