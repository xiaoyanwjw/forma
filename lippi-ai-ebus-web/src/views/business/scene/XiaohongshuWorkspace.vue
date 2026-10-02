<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import { ApiError } from '@/api/client'
import { getScenes, getSceneSkillCapsules } from '@/api/business/scene/scene'
import type { SceneSkillCapsuleItem } from '@/types/business/scene'
import SlotAwarePromptEditor from '@/components/business/scene/SlotAwarePromptEditor.vue'
import WorkspaceChatConsole from '@/components/business/workspace/WorkspaceChatConsole.vue'
import WorkspaceDislikeDrawer from '@/components/business/workspace/WorkspaceDislikeDrawer.vue'
import WorkspaceResultActions from '@/components/business/workspace/WorkspaceResultActions.vue'
import WorkspaceSessionSidebar from '@/components/business/workspace/WorkspaceSessionSidebar.vue'
import {
  getLatestSessionArtifact,
  getSessionMessages,
  listSessions,
} from '@/api/business/session/session'
import type { Page, SessionSummary, SessionTurn } from '@/types/business/session'
import {
  buildFailureDetail,
  canExpandProcessEvent,
  formatEventTime,
  type ProcessEvent,
} from '@/composables/agent/agentProgress'
import { useAgentSkillRun } from '@/composables/agent/useAgentSkillRun'
import { useChatConsoleExpand } from '@/composables/workspace/useChatConsoleExpand'
import { useWorkspaceFeedback } from '@/composables/workspace/useWorkspaceFeedback'
import type { GenerationArtifactPayload } from '@/types/business/agent'
import { parseComputerDocument, type ComputerListItem } from '@/types/business/computerView'
import { toReplayBubblesFromTurns } from '@/utils/sessionReplay'
import {
  buildXhsBreakNoteHandoffText,
  buildXhsNoteHandoffText,
  extractTargetProductFromPrompt,
  extractXhsBreakHandoffFromView,
} from '@/utils/xhsNoteHandoff'
import type { HistoryArtifactDetail } from '@/types/business/history'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'
import MarkdownView from '@/components/common/MarkdownView.vue'
import {
  type XhsComputerKind,
  XHS_SKILL_BY_KIND,
  xhsEmptyFallback,
  xhsKindFromArtifactType as kindFromArtifactType,
  xhsKindFromSkillId,
  xhsSuccessFallback,
  xhsThinkingLabel,
} from '@/views/business/scene/xiaohongshu/workspaceKinds'
import '@/views/business/scene/workspaceSession.css'

const SCENE_CODE = 'xiaohongshu' as const
const SCENE_BREADCRUMB = '小红书种草'
const ATTACH_SOON = '近端暂不支持附件'
const DEMO_SESSION_TITLE = '新任务'

const TEMPLATE_SLOT_MARK = /【占位】/

type ComputerKind = XhsComputerKind
type ChatMessage = WorkspaceChatMessage

const sessionPrompt = ref('')
/** 快捷栏选中的 skillId；null = 空选发送不带 skillId */
const selectedSkillId = ref<string | null>(null)
const skillCapsules = ref<SceneSkillCapsuleItem[]>([])
const sceneBizId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const sessions = ref<SessionSummary[]>([])
const sessionsError = ref('')
const selectedSessionId = ref<string | null>(null)
const sessionTitle = ref(DEMO_SESSION_TITLE)
const sessionRawTurns = ref<SessionTurn[]>([])
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

const {
  feedbackNote,
  feedbackBusy,
  feedbackHint,
  feedbackTag,
  dislikeDrawerOpen,
  canSubmitFeedback,
  submitLikeFeedback,
  openDislikeDrawer,
  closeDislikeDrawer,
  submitPoorQualityFeedback,
  resetFeedbackUi,
} = useWorkspaceFeedback({
  getArtifactId: () => activeLiveArtifact.value?.artifactRef?.trim() || '',
  sessionBusy,
})

const {
  expandedStreamIds,
  expandedStatusIds,
  expandedProcessLogIds,
  shouldShowProcessEvents,
  canToggleProcessLog,
  toggleProcessLog,
  toggleStreamExpand,
  toggleStatusExpand,
  canExpandStatus,
  resetConsoleExpand,
} = useChatConsoleExpand({ generationRunning, thinkingMessageId })

const canOneClickRetry = computed(
  () =>
    Boolean(lastBilledPrompt.value.trim()) &&
    Boolean(lastBilledKind.value) &&
    !sessionBusy.value,
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

function toggleCapsuleSkill(skill: SceneSkillCapsuleItem) {
  if (selectedSkillId.value === skill.skillId) {
    selectedSkillId.value = null
    return
  }
  selectedSkillId.value = skill.skillId
  sessionPrompt.value = skill.examplePrompt || ''
}

function inferXhsKindFromView(
  view: GenerationArtifactPayload['view'] | undefined,
): Exclude<ComputerKind, null> | null {
  if (!view) return null
  const title = view.title || ''
  if (/拆解|爆文/.test(title)) return 'break'
  if (/笔记/.test(title)) return 'note'
  if (/选题/.test(title)) return 'topiclist'
  if (view.blocks.some((b) => b.type === 'list')) return 'topiclist'
  return 'note'
}

function applySoftCreditHint(reason: string): string {
  return /积分不足|额度不足|不足/.test(reason)
    ? `${reason}。可前往套餐页升级后再试。`
    : reason
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
    const reply = xhsSuccessFallback(opts.kind)
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
    const statusDetail = buildFailureDetail(xhsEmptyFallback(opts.kind), opts.processSnapshot || [])
    const failedMsg = {
      id: opts.thinkingId,
      role: 'agent' as const,
      text: xhsEmptyFallback(opts.kind),
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

  const kind = xhsKindFromSkillId(selectedSkillId.value)
  if (kind) {
    await runBilledGeneration(text, kind)
    return
  }
  await runFreeTextGeneration(text)
}

async function onNoteHandoff(payload: { text: string }) {
  const text = payload.text?.trim()
  if (!text || sessionBusy.value) return
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  scrollChatToBottom()
  await runBilledGeneration(text, 'note')
}

function noteHandoffTextForItem(item: ComputerListItem, index: number): string | null {
  return buildXhsNoteHandoffText({
    title: item.title,
    href: item.href,
    id: item.id?.trim() || `tp-${index + 1}`,
  })
}

function isTopicItemActionEnabled(item: ComputerListItem, index: number): boolean {
  return Boolean(noteHandoffTextForItem(item, index))
}

function onTopicItemAction(payload: { item: ComputerListItem; index: number }) {
  const text = noteHandoffTextForItem(payload.item, payload.index)
  if (text) void onNoteHandoff({ text })
}

function onBreakHandoff() {
  if (sessionBusy.value) return
  const fromView = extractXhsBreakHandoffFromView(liveBreak.value?.view)
  const targetProduct =
    fromView.targetProduct || extractTargetProductFromPrompt(lastBilledPrompt.value)
  void onNoteHandoff({
    text: buildXhsBreakNoteHandoffText({
      ...fromView,
      targetProduct,
    }),
  })
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
    text: xhsThinkingLabel(kind),
  })
  scrollChatToBottom()

  try {
    await startSkillRun({
      text,
      skillId: XHS_SKILL_BY_KIND[kind],
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

async function runFreeTextGeneration(text: string) {
  pendingBilledPrompt.value = text
  inFlightKind.value = null
  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: '正在处理…',
  })
  scrollChatToBottom()

  try {
    await startSkillRun({
      text,
      sceneCode: SCENE_CODE,
      sceneId: sceneBizId.value ?? undefined,
      sessionId: sessionId.value ?? undefined,
    })
    const kind = inferXhsKindFromView(skillArtifact.value?.view)
    if (kind) {
      inFlightKind.value = kind
      if (skillArtifact.value?.view) {
        setLiveArtifact(kind, skillArtifact.value)
        computerKind.value = kind
      }
      await finishGenerationMessage({
        thinkingId,
        error: skillError.value,
        artifact: skillArtifact.value,
        kind,
        processSnapshot: processEvents.value.length ? [...processEvents.value] : undefined,
      })
      return
    }

    const idx = messages.value.findIndex((m) => m.id === thinkingId)
    if (skillError.value) {
      const soft = applySoftCreditHint(skillError.value)
      const failedMsg = {
        id: thinkingId,
        role: 'agent' as const,
        text: soft,
        processEvents: processEvents.value.length ? [...processEvents.value] : undefined,
        failed: true,
        statusDetail: buildFailureDetail(
          soft,
          processEvents.value.length ? [...processEvents.value] : [],
        ),
      }
      if (idx >= 0) messages.value[idx] = failedMsg
      else messages.value.push({ ...failedMsg, id: nextMsgId() })
    } else {
      const reply = skillArtifact.value?.view
        ? '已生成结果，右侧 Computer 可查看。'
        : '处理已结束，但未收到可用成果，请重试。'
      const failed = !skillArtifact.value?.view
      if (idx >= 0) {
        messages.value[idx] = {
          id: thinkingId,
          role: 'agent',
          text: reply,
          processEvents: processEvents.value.length ? [...processEvents.value] : undefined,
          failed: failed || undefined,
          statusDetail: failed
            ? buildFailureDetail(reply, processEvents.value.length ? [...processEvents.value] : [])
            : undefined,
        }
      }
    }
    thinkingMessageId.value = null
    scrollChatToBottom()
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

function isPreviewOpenFromStatus(m: ChatMessage): boolean {
  const kind = previewKindFromStatus(m) || computerKind.value
  return Boolean(kind && canPreviewFromStatus(m) && computerKind.value === kind)
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
  turns: SessionTurn[] | null | undefined,
  artifactKind: ComputerKind,
): ChatMessage[] {
  return toReplayBubblesFromTurns(turns, artifactKind).map((bubble) => {
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
        processEvents: bubble.processEvents,
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

function applyMessagePage(page: Page<SessionTurn> | null | undefined, mode: 'replace' | 'prepend') {
  const items = Array.isArray(page?.items) ? page!.items : []
  if (mode === 'replace') {
    sessionRawTurns.value = items
  } else {
    sessionRawTurns.value = [...items, ...sessionRawTurns.value]
  }
  const token = page?.nextToken?.trim() || null
  sessionNextToken.value = token
  sessionHasMore.value = Boolean(token)
}

function paintSessionReplay(kind: ComputerKind) {
  sessionReplayKind.value = kind
  const replayed = replayMessagesFromApi(sessionRawTurns.value, kind)
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
    text: xhsSuccessFallback(kind),
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
  resetConsoleExpand()
  sessionId.value = sid
  selectedSessionId.value = sid
  sessionTitle.value = item.title || DEMO_SESSION_TITLE
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayKind.value = null
  lastBilledPrompt.value = ''
  lastBilledKind.value = null
  pendingBilledPrompt.value = ''
  inFlightKind.value = null
  resetFeedbackUi()
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
    // 侧栏切入：挂上成果但不自动展开 Computer（点 STATUS「查看」再开）
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
  resetConsoleExpand()
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayKind.value = null
  computerKind.value = null
  liveTopiclist.value = null
  liveNote.value = null
  liveBreak.value = null
  sessionPrompt.value = ''
  selectedSkillId.value = null
  sessionTitle.value = DEMO_SESSION_TITLE
  selectedSessionId.value = null
  lastBilledPrompt.value = ''
  lastBilledKind.value = null
  pendingBilledPrompt.value = ''
  inFlightKind.value = null
  resetFeedbackUi()
  void loadSessions()
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
  try {
    const capsule = await getSceneSkillCapsules(SCENE_CODE)
    skillCapsules.value = Array.isArray(capsule?.skills) ? capsule.skills : []
  } catch {
    skillCapsules.value = []
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
      <WorkspaceSessionSidebar
        :sessions="sessions"
        :selected-session-id="selectedSessionId"
        :sessions-error="sessionsError"
        scene-label="小红书种草"
        @new-task="newTask"
        @select="selectSession"
      />

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
                <MarkdownView
                  v-if="m.role === 'user'"
                  class="msg-text"
                  :source="m.text"
                  :show-copy="false"
                  highlight-slots
                />
                <p v-else-if="!isConsoleMessage(m)" class="msg-text agent-text">
                  <span v-if="m.at" class="msg-time">{{ formatEventTime(m.at) }}</span>
                  {{ m.text }}
                </p>
                <WorkspaceChatConsole
                  v-else-if="m.processEvents?.length || m.text"
                  :message="m"
                  :is-running="generationRunning && m.id === thinkingMessageId"
                  :show-process-events="shouldShowProcessEvents(m)"
                  :can-toggle-process="canToggleProcessLog(m)"
                  :process-expanded="expandedProcessLogIds.has(m.id)"
                  :expanded-stream-ids="expandedStreamIds"
                  :expanded-status-ids="expandedStatusIds"
                  :can-preview="canPreviewFromStatus(m)"
                  :preview-open="isPreviewOpenFromStatus(m)"
                  :can-expand-fail="canExpandStatus(m)"
                  @toggle-process="toggleProcessLog(m)"
                  @process-event-click="onProcessEventClick"
                  @status-click="onStatusCardClick(m)"
                />
                <WorkspaceResultActions
                  v-if="isLatestPreviewableStatus(m)"
                  :can-retry="canOneClickRetry"
                  :can-submit-feedback="canSubmitFeedback"
                  :can-dislike="canSubmitFeedback"
                  :feedback-tag="feedbackTag"
                  :feedback-hint="feedbackHint"
                  @retry="oneClickRetry"
                  @like="submitLikeFeedback"
                  @dislike="openDislikeDrawer"
                />
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
                <button
                  v-for="skill in skillCapsules"
                  :key="skill.skillId"
                  type="button"
                  class="pill"
                  :class="{ active: selectedSkillId === skill.skillId }"
                  :aria-pressed="selectedSkillId === skill.skillId"
                  :disabled="sessionBusy"
                  @click="toggleCapsuleSkill(skill)"
                >
                  {{ skill.label }}
                </button>
              </div>
              <div class="prompt-box">
                <SlotAwarePromptEditor
                  v-model="sessionPrompt"
                  :disabled="sessionBusy"
                  placeholder="分配一个任务或提问任何问题"
                  aria-label="继续提问"
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
              :file-name="computerKind === 'topiclist' ? 'topiclist.md' : computerKind === 'note' ? 'note.md' : computerKind === 'break' ? 'break.md' : undefined"
              :item-action-label="computerKind === 'topiclist' ? '写成笔记' : ''"
              priority-badge-label="优先发"
              :is-item-action-enabled="isTopicItemActionEnabled"
              @item-action="onTopicItemAction"
            />
          </div>
        </aside>
      </div>
    </div>
  </div>
  <WorkspaceDislikeDrawer
    :open="dislikeDrawerOpen"
    :note="feedbackNote"
    :busy="feedbackBusy"
    :can-submit="canSubmitFeedback"
    @update:note="feedbackNote = $event"
    @cancel="closeDislikeDrawer"
    @submit="submitPoorQualityFeedback"
  />
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


.prompt-editor {
  border: 0;
  outline: none;
  resize: none;
  min-height: 56px;
  background: transparent;
  color: var(--ink);
  font-size: 0.8125rem;
  font-weight: 400;
  line-height: 1.6;
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
  margin-top: 4px;
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
