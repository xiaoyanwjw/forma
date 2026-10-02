<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import SlotAwarePromptEditor from '@/components/business/scene/SlotAwarePromptEditor.vue'
import WorkspaceChatConsole from '@/components/business/workspace/WorkspaceChatConsole.vue'
import WorkspaceDislikeDrawer from '@/components/business/workspace/WorkspaceDislikeDrawer.vue'
import WorkspaceResultActions from '@/components/business/workspace/WorkspaceResultActions.vue'
import WorkspaceSessionSidebar from '@/components/business/workspace/WorkspaceSessionSidebar.vue'
import { ApiError } from '@/api/client'
import { getScenes, getSceneSkillCapsules } from '@/api/business/scene/scene'
import type { SceneSkillCapsuleItem } from '@/types/business/scene'
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
import {
  DEMO_LISTING_VIEW,
  DEMO_PICKS_VIEW,
  DEMO_SESSION_TITLE,
} from '@/views/business/scene/ecommerceDemoFixtures'
import type { GenerationArtifactPayload } from '@/types/business/agent'
import { parseComputerDocument, type ComputerListItem } from '@/types/business/computerView'
import { buildListingHandoffText } from '@/utils/listingHandoff'
import { toReplayBubblesFromTurns } from '@/utils/sessionReplay'
import type { HistoryArtifactDetail } from '@/types/business/history'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'
import MarkdownView from '@/components/common/MarkdownView.vue'
import {
  type EcommerceComputerKind,
  ECOM_SKILL_BY_KIND,
  ecommerceKindFromArtifactType as kindFromArtifactType,
  ecommerceKindFromSkillId,
  previewEcommerceKindFromStatus,
} from '@/views/business/scene/ecommerce/workspaceKinds'
import '@/views/business/scene/workspaceSession.css'

/** Stable scene binding for this workbench — Epic 3 session create must carry it */
const SCENE_CODE = 'ecommerce' as const
const SCENE_BREADCRUMB = '电商开店'
const ATTACH_SOON = '近端暂不支持附件'

const TEMPLATE_SLOT_MARK = /【品类】|【最低价】|【最高价】|【商品名称】|【淘宝\/拼多多\/闲鱼】/

type ComputerKind = EcommerceComputerKind
type ChatMessage = WorkspaceChatMessage

const sessionPrompt = ref('')
/** 快捷栏选中的 skillId；null = 空选发送不带 skillId */
const selectedSkillId = ref<string | null>(null)
/** 工作台快捷胶囊：来自 GET /scenes/{sceneCode}/skills（launch.json） */
const skillCapsules = ref<SceneSkillCapsuleItem[]>([])
/** Optional Catalog bizId when list is available; null if unresolved */
const sceneBizId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const sessions = ref<SessionSummary[]>([])
const sessionsError = ref('')
const selectedSessionId = ref<string | null>(null)
const sessionTitle = ref(DEMO_SESSION_TITLE)
/** 侧栏回放：已加载的原始 R1 行（分页累加） */
const sessionRawTurns = ref<SessionTurn[]>([])
const sessionHasMore = ref(false)
const sessionNextToken = ref<string | null>(null)
const sessionHistoryLoading = ref(false)
const sessionReplayKind = ref<ComputerKind>(null)
const computerKind = ref<ComputerKind>(null)
const livePicklist = ref<GenerationArtifactPayload | null>(null)
const liveListing = ref<GenerationArtifactPayload | null>(null)
const chatScrollEl = ref<HTMLElement | null>(null)
const computerEl = ref<HTMLElement | null>(null)
const thinkingMessageId = ref<string | null>(null)

const {
  running: generationRunning,
  error: skillError,
  artifact: skillArtifact,
  sessionId,
  processEvents,
  pendingHuman,
  startSkillRun,
  resumeSkillRun,
  reset: resetSkillRun,
} = useAgentSkillRun()

const listingSupplementOpen = ref(false)
const listingSupplementText = ref('')

/** 一键重试用：上次成功计费提示词与类型（勿走 newTask） */
const lastBilledPrompt = ref('')
const lastBilledKind = ref<ComputerKind>(null)
/** 当前计费轮次提示词（含 HITL 续跑成功后回填） */
const pendingBilledPrompt = ref('')
/** Ignore stale session-switch HTTP after 新任务 / 连点侧栏 */
let workspaceSwitchSeq = 0

const listingAwaitingHuman = computed(() => Boolean(pendingHuman.value))
const sessionBusy = computed(() => generationRunning.value || listingAwaitingHuman.value)

const computerOpen = computed(() => computerKind.value != null)
const sessionSendEnabled = computed(
  () => sessionPrompt.value.trim().length > 0 && !sessionBusy.value,
)

const picksIsLive = computed(() => Boolean(livePicklist.value?.view))
const listingIsLive = computed(() => Boolean(liveListing.value?.view))

const activeLiveArtifact = computed(() => {
  if (computerKind.value === 'listing' && liveListing.value?.artifactRef) {
    return liveListing.value
  }
  if (computerKind.value === 'picks' && livePicklist.value?.artifactRef) {
    return livePicklist.value
  }
  if (liveListing.value?.artifactRef) return liveListing.value
  if (livePicklist.value?.artifactRef) return livePicklist.value
  return null
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
  if (computerKind.value === 'listing') {
    if (liveListing.value?.view) return liveListing.value.view
    return DEMO_LISTING_VIEW
  }
  if (computerKind.value === 'picks') {
    if (livePicklist.value?.view) return livePicklist.value.view
    return DEMO_PICKS_VIEW
  }
  return null
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

function inferEcommerceKindFromView(
  view: GenerationArtifactPayload['view'] | undefined,
): Exclude<ComputerKind, null> | null {
  if (!view) return null
  if (view.blocks.some((b) => b.type === 'list' && b.ordered !== false && /上架|listing/i.test(view.title || ''))) {
    return 'listing'
  }
  if (
    view.blocks.some(
      (b) =>
        b.type === 'section' &&
        (b.heading === '详情标题' || b.heading === '详情正文'),
    ) &&
    !view.blocks.some((b) => b.type === 'list')
  ) {
    return 'listing'
  }
  if (view.blocks.some((b) => b.type === 'list')) return 'picks'
  if (/上架|listing/i.test(view.title || '')) return 'listing'
  if (/选品|清单/i.test(view.title || '')) return 'picks'
  return null
}

function applySoftCreditHint(reason: string): string {
  return /积分不足|额度不足|不足/.test(reason)
    ? `${reason}。可前往套餐页升级后再试。`
    : reason
}

async function finishGenerationMessage(opts: {
  thinkingId: string
  error: string
  artifact: GenerationArtifactPayload | null
  kind: ComputerKind
  successFallback: string
  emptyFallback: string
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
    if (opts.kind === 'picks') {
      livePicklist.value = opts.artifact
    } else if (opts.kind === 'listing') {
      liveListing.value = opts.artifact
    }
    computerKind.value = opts.kind
    if (opts.kind && pendingBilledPrompt.value.trim()) {
      lastBilledPrompt.value = pendingBilledPrompt.value.trim()
      lastBilledKind.value = opts.kind
    }
    feedbackHint.value = ''
    revealComputer()
    void loadSessions()
    const reply = opts.successFallback
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
    const statusDetail = buildFailureDetail(opts.emptyFallback, opts.processSnapshot || [])
    const failedMsg = {
      id: opts.thinkingId,
      role: 'agent' as const,
      text: opts.emptyFallback,
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

function applyListingArtifactToComputer() {
  if (skillArtifact.value?.view) {
    liveListing.value = skillArtifact.value
    computerKind.value = 'listing'
    revealComputer()
  }
}

function presentListingHumanInput(thinkingId: string) {
  listingSupplementOpen.value = false
  listingSupplementText.value = ''
  applyListingArtifactToComputer()
  const idx = messages.value.findIndex((m) => m.id === thinkingId)
  const question =
    pendingHuman.value?.question ||
    '策划分镜已出。请确认出执行稿，或补充需求。'
  const snapshot = processEvents.value.length
    ? [...processEvents.value]
    : undefined
  const next = {
    id: thinkingId,
    role: 'agent' as const,
    text: question,
    processEvents: snapshot,
  }
  if (idx >= 0) {
    messages.value[idx] = next
  } else {
    messages.value.push({ ...next, id: nextMsgId() })
  }
  thinkingMessageId.value = thinkingId
  scrollChatToBottom()
}

async function finishListingAfterStream(thinkingId: string) {
  if (pendingHuman.value) {
    presentListingHumanInput(thinkingId)
    return
  }
  await finishGenerationMessage({
    thinkingId,
    error: skillError.value,
    artifact: skillArtifact.value,
    kind: 'listing',
    successFallback: '已生成上架素材，右侧 Computer 可查看主图位与文案。',
    emptyFallback: '上架素材已结束，但未收到可用成果，请重试。',
    processSnapshot: processEvents.value.length
      ? [...processEvents.value]
      : undefined,
  })
}

async function confirmListingExecute() {
  if (!pendingHuman.value || generationRunning.value) return
  listingSupplementOpen.value = false
  const thinkingId = thinkingMessageId.value
  if (thinkingId) {
    const idx = messages.value.findIndex((m) => m.id === thinkingId)
    if (idx >= 0) {
      const cur = messages.value[idx]
      if (cur) {
        messages.value[idx] = {
          id: cur.id,
          role: 'agent',
          text: '正在根据确认生成执行稿…',
          processEvents: processEvents.value.length
            ? [...processEvents.value]
            : cur.processEvents,
        }
      }
    }
  }
  await resumeSkillRun({ optionId: 'confirm_execute' })
  if (thinkingId) {
    await finishListingAfterStream(thinkingId)
  }
}

function openListingSupplement() {
  listingSupplementOpen.value = !listingSupplementOpen.value
}

async function submitListingSupplement() {
  if (!pendingHuman.value || generationRunning.value) return
  const note = listingSupplementText.value.trim()
  await resumeSkillRun({ optionId: 'supplement', freeText: note || undefined })
  listingSupplementText.value = ''
  listingSupplementOpen.value = false
  const thinkingId = thinkingMessageId.value
  if (thinkingId) {
    await finishListingAfterStream(thinkingId)
  }
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
      text: '请先把【】里的占位改成真实品类和价格，或点「选品清单」胶囊使用示例后再发送。',
    })
    scrollChatToBottom()
    return
  }

  messages.value.push({ id: nextMsgId(), role: 'user', text })
  sessionPrompt.value = ''
  scrollChatToBottom()

  const kind = ecommerceKindFromSkillId(selectedSkillId.value)
  if (kind) {
    await runBilledGeneration(text, kind)
    return
  }
  await runFreeTextGeneration(text)
}

/** 计费生成（首次发送与一键重试共用；复用 sessionId，新 Run）。 */
async function onListingHandoff(payload: { text: string }) {
  const text = payload.text?.trim()
  if (!text || sessionBusy.value) return
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  scrollChatToBottom()
  await runBilledGeneration(text, 'listing')
}

function listingHandoffTextForItem(item: ComputerListItem, index: number): string | null {
  return buildListingHandoffText({
    title: item.title,
    href: item.href,
    id: item.id?.trim() || `pl-${index + 1}`,
  })
}

function isPickItemActionEnabled(item: ComputerListItem, index: number): boolean {
  return Boolean(listingHandoffTextForItem(item, index))
}

function onPickItemAction(payload: { item: ComputerListItem; index: number }) {
  const text = listingHandoffTextForItem(payload.item, payload.index)
  if (text) void onListingHandoff({ text })
}

async function runBilledGeneration(text: string, kind: 'picks' | 'listing') {
  pendingBilledPrompt.value = text
  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: kind === 'listing' ? '正在生成上架素材…' : '正在生成选品清单…',
  })
  scrollChatToBottom()

  const shared = {
    text,
    sceneCode: SCENE_CODE,
    sceneId: sceneBizId.value ?? undefined,
    sessionId: sessionId.value ?? undefined,
  }

  await startSkillRun({
    ...shared,
    skillId: ECOM_SKILL_BY_KIND[kind],
  })

  if (kind === 'listing') {
    await finishListingAfterStream(thinkingId)
    return
  }

  const n = skillArtifact.value?.view?.blocks?.length || 0
  await finishGenerationMessage({
    thinkingId,
    error: skillError.value,
    artifact: skillArtifact.value,
    kind: 'picks',
    successFallback:
      n > 0 ? '已生成选品候选，右侧 Computer 可查看详情。' : '已生成选品成果，右侧 Computer 可查看。',
    emptyFallback: '选品已结束，但未收到可用清单，请重试。',
    processSnapshot: processEvents.value.length
      ? [...processEvents.value]
      : undefined,
  })
}

/** 空选快捷栏：不带 skillId，Computer kind 等成果 view 再定。 */
async function runFreeTextGeneration(text: string) {
  pendingBilledPrompt.value = text
  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: '正在处理…',
  })
  scrollChatToBottom()

  await startSkillRun({
    text,
    sceneCode: SCENE_CODE,
    sceneId: sceneBizId.value ?? undefined,
    sessionId: sessionId.value ?? undefined,
  })

  const kind = inferEcommerceKindFromView(skillArtifact.value?.view)
  if (kind) {
    const n = skillArtifact.value?.view?.blocks?.length || 0
    await finishGenerationMessage({
      thinkingId,
      error: skillError.value,
      artifact: skillArtifact.value,
      kind,
      successFallback:
        kind === 'listing'
          ? '已生成上架素材，右侧 Computer 可查看。'
          : n > 0
            ? '已生成选品候选，右侧 Computer 可查看详情。'
            : '已生成选品成果，右侧 Computer 可查看。',
      emptyFallback:
        kind === 'listing'
          ? '上架已结束，但未收到可用素材，请重试。'
          : '选品已结束，但未收到可用清单，请重试。',
      processSnapshot: processEvents.value.length
        ? [...processEvents.value]
        : undefined,
    })
    return
  }

  const idx = messages.value.findIndex((m) => m.id === thinkingId)
  if (skillError.value) {
    const soft = applySoftCreditHint(skillError.value)
    const statusDetail = buildFailureDetail(
      soft,
      processEvents.value.length ? [...processEvents.value] : [],
    )
    const failedMsg = {
      id: thinkingId,
      role: 'agent' as const,
      text: soft,
      processEvents: processEvents.value.length
        ? [...processEvents.value]
        : undefined,
      failed: true,
      statusDetail,
    }
    if (idx >= 0) messages.value[idx] = failedMsg
    else messages.value.push({ ...failedMsg, id: nextMsgId() })
  } else if (skillArtifact.value?.view) {
    const reply = '已生成结果，右侧 Computer 可查看。'
    if (idx >= 0) {
      messages.value[idx] = {
        id: thinkingId,
        role: 'agent',
        text: reply,
        processEvents: processEvents.value.length
          ? [...processEvents.value]
          : undefined,
      }
    }
  } else {
    const empty = '处理已结束，但未收到可用成果，请重试。'
    const statusDetail = buildFailureDetail(
      empty,
      processEvents.value.length ? [...processEvents.value] : [],
    )
    const failedMsg = {
      id: thinkingId,
      role: 'agent' as const,
      text: empty,
      processEvents: processEvents.value.length
        ? [...processEvents.value]
        : undefined,
      failed: true,
      statusDetail,
    }
    if (idx >= 0) messages.value[idx] = failedMsg
    else messages.value.push({ ...failedMsg, id: nextMsgId() })
  }
  thinkingMessageId.value = null
  scrollChatToBottom()
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

function openPicksComputer() {
  computerKind.value = 'picks'
  revealComputer()
}

function openListingComputer() {
  computerKind.value = 'listing'
  revealComputer()
}

function previewKindFromStatus(m: ChatMessage): ComputerKind {
  return previewEcommerceKindFromStatus(m.text)
}

function canPreviewFromStatus(m: ChatMessage): boolean {
  if (m.failed) return false
  if (!/已生成/.test(m.text)) return false
  const kind = previewKindFromStatus(m)
  if (kind === 'listing') return Boolean(liveListing.value?.view)
  if (kind === 'picks') return Boolean(livePicklist.value?.view)
  return Boolean(livePicklist.value?.view || liveListing.value?.view)
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

/** Preview is showing when Computer is open on the matching live view. */
function isPreviewOpenFromStatus(m: ChatMessage): boolean {
  const kind = previewKindFromStatus(m) || (livePicklist.value?.view ? 'picks' : 'listing')
  return canPreviewFromStatus(m) && computerKind.value === kind
}

function onStatusCardClick(m: ChatMessage) {
  if (canPreviewFromStatus(m)) {
    if (isPreviewOpenFromStatus(m)) {
      closeComputer()
    } else {
      const kind = previewKindFromStatus(m)
      if (kind === 'listing') {
        openListingComputer()
      } else {
        openPicksComputer()
      }
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
      (generationRunning.value && m.id === thinkingMessageId.value) ||
      (listingAwaitingHuman.value && m.id === thinkingMessageId.value),
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
  if (kind === 'listing') {
    return {
      id: nextMsgId(),
      role: 'agent',
      presentation: 'console',
      text: '已生成上架素材，右侧 Computer 可查看主图位与文案。',
    }
  }
  if (kind === 'picks') {
    return {
      id: nextMsgId(),
      role: 'agent',
      presentation: 'console',
      text: '已生成选品成果，右侧 Computer 可查看。',
    }
  }
  return null
}

function applyLatestArtifact(
  detail: HistoryArtifactDetail | null | undefined,
  openComputer = true,
): ComputerKind {
  livePicklist.value = null
  liveListing.value = null
  computerKind.value = null
  return mergeSessionArtifact(detail, openComputer)
}

/** 写入单条会话成果，不清空另一类（选品+上架同会话回放用）。 */
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
  const kind = kindFromArtifactType(detail.artifactType) || 'picks'
  const payload: GenerationArtifactPayload = { artifactRef: detail.id, view }
  if (kind === 'listing') {
    liveListing.value = payload
  } else {
    livePicklist.value = payload
  }
  if (openComputer) {
    computerKind.value = kind
    revealComputer()
  }
  return kind
}

function clearSessionArtifacts() {
  livePicklist.value = null
  liveListing.value = null
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
  listingSupplementOpen.value = false
  listingSupplementText.value = ''
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
  resetFeedbackUi()
  applyLatestArtifact(null)
  try {
    const [page, latest, picksArt, listingArt] = await Promise.all([
      getSessionMessages(sid),
      getLatestSessionArtifact(sid),
      getLatestSessionArtifact(sid, 'picklist'),
      getLatestSessionArtifact(sid, 'sku'),
    ])
    if (seq !== workspaceSwitchSeq) return
    clearSessionArtifacts()
    mergeSessionArtifact(picksArt, false)
    mergeSessionArtifact(listingArt, false)
    const kind =
      kindFromArtifactType(latest?.artifactType) ||
      (listingArt?.view ? 'listing' : picksArt?.view ? 'picks' : null)
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
  listingSupplementOpen.value = false
  listingSupplementText.value = ''
  thinkingMessageId.value = null
  resetConsoleExpand()
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayKind.value = null
  computerKind.value = null
  livePicklist.value = null
  liveListing.value = null
  sessionPrompt.value = ''
  selectedSkillId.value = null
  sessionTitle.value = DEMO_SESSION_TITLE
  selectedSessionId.value = null
  lastBilledPrompt.value = ''
  lastBilledKind.value = null
  pendingBilledPrompt.value = ''
  resetFeedbackUi()
  void loadSessions()
}

watch(skillArtifact, (value) => {
  if (!value?.view) return
  const kind = inferEcommerceKindFromView(value.view)
  if (kind === 'listing') {
    liveListing.value = value
    return
  }
  if (kind === 'picks') {
    livePicklist.value = value
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
        scene-label="电商开店"
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
                <div v-if="m.role === 'agent' && !picksIsLive && !listingIsLive" class="demo-actions">
                  <button
                    type="button"
                    class="pill"
                    data-demo="open-picks"
                    @click="openPicksComputer"
                  >
                    预览选品清单（演示）
                  </button>
                  <button
                    type="button"
                    class="pill"
                    data-demo="open-listing"
                    @click="openListingComputer"
                  >
                    预览上架素材（演示）
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input-wrap">
            <div class="chat-composer">
              <div
                v-if="listingAwaitingHuman && pendingHuman"
                class="ask-human"
                data-testid="ask-human"
              >
                <div class="ask-human-head">
                  <span class="ask-human-eyebrow">需要你确认</span>
                  <span class="ask-human-hint">右侧 Computer 可先看策划</span>
                </div>
                <p class="ask-human-q">{{ pendingHuman.question }}</p>
                <div class="ask-human-actions" role="group" aria-label="确认策划">
                  <button
                    type="button"
                    class="ask-human-primary"
                    data-testid="ask-human-confirm"
                    :disabled="generationRunning"
                    @click="confirmListingExecute"
                  >
                    确认，出执行稿
                  </button>
                  <button
                    type="button"
                    class="ask-human-secondary"
                    data-testid="ask-human-supplement"
                    :disabled="generationRunning"
                    @click="openListingSupplement"
                  >
                    {{ listingSupplementOpen ? '收起补充' : '补充需求' }}
                  </button>
                </div>
                <div
                  v-if="listingSupplementOpen"
                  class="ask-human-supplement"
                  data-testid="ask-human-supplement-form"
                >
                  <textarea
                    v-model="listingSupplementText"
                    class="ask-human-note"
                    rows="3"
                    placeholder="写下要改的分镜、标题或详情大纲"
                    aria-label="补充需求"
                    :disabled="generationRunning"
                  />
                  <button
                    type="button"
                    class="ask-human-primary ask-human-primary-sm"
                    data-testid="ask-human-supplement-submit"
                    :disabled="generationRunning"
                    @click="submitListingSupplement"
                  >
                    提交补充
                  </button>
                </div>
              </div>
              <div
                v-else
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

        <aside ref="computerEl" class="computer" id="computer" aria-label="Adam's Computer">
          <div class="computer-bar">
            <div class="title">
              <span class="dots" aria-hidden="true"><i /><i /><i /></span>
              Adam's Computer
            </div>
            <button type="button" class="icon-btn" id="close-computer" aria-label="关闭" @click="closeComputer">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="computer-body">
            <ComputerRenderer
              v-if="activeComputerDoc"
              :document="activeComputerDoc"
              :file-name="computerKind === 'picks' ? 'picklist.md' : computerKind === 'listing' ? 'listing.md' : undefined"
              :item-action-label="computerKind === 'picks' ? '做上架素材' : ''"
              :is-item-action-enabled="isPickItemActionEnabled"
              @item-action="onPickItemAction"
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

.pill.active,
.pill[aria-pressed='true'] {
  border-color: color-mix(in srgb, #0f766e 55%, var(--line));
  background: color-mix(in srgb, #0f766e 12%, var(--surface));
  color: #0f766e;
}

.pill:hover:not(:disabled) {
  background: var(--line-2);
}

.pill:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pill svg {
  opacity: 0.7;
  flex-shrink: 0;
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

.card-result-actions .pill.is-on {
  background: var(--accent);
  color: #fff;
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
