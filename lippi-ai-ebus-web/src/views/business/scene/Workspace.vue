<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
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
import type { GenerationArtifactPayload } from '@/types/business/agent'
import {
  parseComputerDocument,
  type ComputerDocument,
  type ComputerPanelView,
  type ComputerListItem,
} from '@/types/business/computerView'
import { hasPromptSlots } from '@/utils/promptSlots'
import { toReplayBubblesFromTurns, type ReplayArtifactKind } from '@/utils/sessionReplay'
import type { HistoryArtifactDetail } from '@/types/business/history'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'
import { getSceneWorkspaceSpec } from '@/views/business/scene/workspace/registry'
import {
  buildXhsBreakToolbarText,
  XHS_BREAK_TOOLBAR,
} from '@/views/business/scene/xiaohongshu/spec'
import '@/views/business/scene/workspaceSession.css'

const ATTACH_SOON = '近端暂不支持附件'
const SESSION_TITLE = '新任务'
const SLOT_BLOCKED_TEXT = '请先改完「」里的示例再发送，或点上方胶囊使用示例。'

const PANE_COPY: Record<string, { thinking: string; success: string; empty: string }> = {
  picks: {
    thinking: '正在生成选品清单…',
    success: '已生成选品成果，右侧 Computer 可查看。',
    empty: '选品已结束，但未收到可用清单，请重试。',
  },
  listing: {
    thinking: '正在生成上架素材…',
    success: '已生成上架素材，右侧 Computer 可查看主图位与文案。',
    empty: '上架素材已结束，但未收到可用成果，请重试。',
  },
  topiclist: {
    thinking: '正在生成选题清单…',
    success: '已生成选题清单，右侧 Computer 可查看。',
    empty: '选题已结束，但未收到可用清单，请重试。',
  },
  note: {
    thinking: '正在生成笔记草稿…',
    success: '已生成笔记草稿，右侧 Computer 可查看。',
    empty: '笔记生成已结束，但未收到可用草稿，请重试。',
  },
  break: {
    thinking: '正在拆解爆文…',
    success: '已生成爆文拆解，右侧 Computer 可查看。',
    empty: '拆解已结束，但未收到可用成果，请重试。',
  },
}

const FILE_BY_PANE: Record<string, string> = {
  picks: 'picklist.md',
  listing: 'listing.md',
  topiclist: 'topiclist.md',
  note: 'note.md',
  break: 'break.md',
}

const REPLAY_PANE_PREFERENCE = ['listing', 'note', 'break', 'picks', 'topiclist']

type ChatMessage = WorkspaceChatMessage
type SessionArtifactType = Parameters<typeof getLatestSessionArtifact>[1]

const route = useRoute()
const sceneCode = computed(() => {
  const raw = route.params.sceneCode
  const value = Array.isArray(raw) ? raw[0] : raw
  return (value || '').trim()
})
const spec = computed(() => getSceneWorkspaceSpec(sceneCode.value))

const sessionPrompt = ref('')
/** 快捷栏选中的 skillId；null = 空选发送不带 skillId */
const selectedSkillId = ref<string | null>(null)
const skillCapsules = ref<SceneSkillCapsuleItem[]>([])
const sceneBizId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const sessions = ref<SessionSummary[]>([])
const sessionsError = ref('')
const selectedSessionId = ref<string | null>(null)
const sessionTitle = ref(SESSION_TITLE)
const sessionRawTurns = ref<SessionTurn[]>([])
const sessionHasMore = ref(false)
const sessionNextToken = ref<string | null>(null)
const sessionHistoryLoading = ref(false)
const sessionReplayPane = ref<string | null>(null)
const activePane = ref<string | null>(null)
type LiveArtifactPayload = { artifactRef: string; view: ComputerPanelView }

const liveByPane = ref<Record<string, LiveArtifactPayload | null>>({})
const chatScrollEl = ref<HTMLElement | null>(null)
const computerEl = ref<HTMLElement | null>(null)
const thinkingMessageId = ref<string | null>(null)
const supplementOpen = ref(false)
const supplementText = ref('')
const lastBilledPrompt = ref('')
const lastBilledPane = ref<string | null>(null)
const pendingBilledPrompt = ref('')
const inFlightPane = ref<string | null>(null)
const suspendedPane = ref<string | null>(null)
let workspaceSwitchSeq = 0
let msgSeq = 0

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

const awaitingHuman = computed(() => Boolean(pendingHuman.value))
const sessionBusy = computed(() => generationRunning.value || awaitingHuman.value)
const computerOpen = computed(() => activePane.value != null)
const sessionSendEnabled = computed(
  () => sessionPrompt.value.trim().length > 0 && !sessionBusy.value,
)

const activeLiveArtifact = computed(() => {
  const pane = activePane.value
  if (pane && liveByPane.value[pane]?.artifactRef) {
    return liveByPane.value[pane]
  }
  const billed = lastBilledPane.value
  if (billed && liveByPane.value[billed]?.artifactRef) {
    return liveByPane.value[billed]
  }
  const preferred = preferredLivePane()
  if (preferred && liveByPane.value[preferred]?.artifactRef) {
    return liveByPane.value[preferred]
  }
  return Object.values(liveByPane.value).find((item) => item?.artifactRef) ?? null
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
    Boolean(lastBilledPane.value) &&
    !sessionBusy.value,
)

const activeComputerDoc = computed((): ComputerDocument | null => {
  const pane = activePane.value
  if (!pane) return null
  const view = liveByPane.value[pane]?.view
  if (!view || view.version !== 1) return null
  return view
})

const computerFileName = computed(() => {
  const pane = activePane.value
  if (!pane) return undefined
  return FILE_BY_PANE[pane] ?? `${pane}.md`
})

const activeItemHandoff = computed(() => {
  const pane = activePane.value
  if (!pane) return null
  return spec.value?.itemHandoffs?.find((handoff) => handoff.whenPane === pane) ?? null
})

const itemActionLabel = computed(() => activeItemHandoff.value?.actionLabel ?? '')

const breakToolbar = computed(() => {
  if (spec.value?.sceneCode !== 'xiaohongshu') return null
  if (activePane.value !== XHS_BREAK_TOOLBAR.whenPane) return null
  if (!liveByPane.value.break?.view) return null
  return XHS_BREAK_TOOLBAR
})

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

function processSnapshot(): ProcessEvent[] | undefined {
  return processEvents.value.length ? [...processEvents.value] : undefined
}

function setLive(pane: string, payload: LiveArtifactPayload) {
  liveByPane.value = { ...liveByPane.value, [pane]: payload }
}

function clearSessionArtifacts() {
  liveByPane.value = {}
  activePane.value = null
}

function thinkingLabel(pane: string | null) {
  if (!pane) return '正在处理…'
  return PANE_COPY[pane]?.thinking ?? '正在处理…'
}

function emptyText(pane: string) {
  return PANE_COPY[pane]?.empty ?? '处理已结束，但未收到可用成果，请重试。'
}

function successText(pane: string, blockCount: number) {
  if (pane === 'picks') {
    return blockCount > 0
      ? '已生成选品候选，右侧 Computer 可查看详情。'
      : '已生成选品成果，右侧 Computer 可查看。'
  }
  return PANE_COPY[pane]?.success ?? '已生成结果，右侧 Computer 可查看。'
}

function skillIdForPane(pane: string): string | undefined {
  const map = spec.value?.paneBySkillId
  if (!map) return undefined
  for (const [skillId, mapped] of Object.entries(map)) {
    if (mapped === pane) return skillId
  }
  return undefined
}

function paneFromArtifactType(artifactType?: string | null): string | null {
  const key = (artifactType || '').trim().toLowerCase()
  if (!key || !spec.value) return null
  return spec.value.paneByArtifactType[key] ?? null
}

function inferPaneFromView(view: ComputerPanelView | undefined): string | null {
  if (!view || !spec.value) return null
  if (view.version === 2) {
    const title = view.title || ''
    if (spec.value.sceneCode === 'ecommerce') {
      if (/上架|listing/i.test(title)) return 'listing'
      if (/选品|清单/i.test(title)) return 'picks'
      return null
    }
    if (spec.value.sceneCode === 'xiaohongshu') {
      if (/拆解|爆文/.test(title)) return 'break'
      if (/笔记/.test(title)) return 'note'
      if (/选题/.test(title)) return 'topiclist'
      return 'note'
    }
    return null
  }
  if (spec.value.sceneCode === 'ecommerce') return inferEcommercePane(view)
  if (spec.value.sceneCode === 'xiaohongshu') return inferXhsPane(view)
  return null
}

function inferEcommercePane(view: ComputerDocument): string | null {
  if (view.blocks.some((b) => b.type === 'list' && b.ordered !== false && /上架|listing/i.test(view.title || ''))) {
    return 'listing'
  }
  if (
    view.blocks.some(
      (b) => b.type === 'section' && (b.heading === '详情标题' || b.heading === '详情正文'),
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

function inferXhsPane(view: ComputerDocument): string | null {
  const title = view.title || ''
  if (/拆解|爆文/.test(title)) return 'break'
  if (/笔记/.test(title)) return 'note'
  if (/选题/.test(title)) return 'topiclist'
  if (view.blocks.some((b) => b.type === 'list')) return 'topiclist'
  return 'note'
}

function preferredLivePane(): string | null {
  for (const pane of REPLAY_PANE_PREFERENCE) {
    if (liveByPane.value[pane]?.view) return pane
  }
  for (const [pane, payload] of Object.entries(liveByPane.value)) {
    if (payload?.view) return pane
  }
  return null
}

function asReplayKind(pane: string | null): ReplayArtifactKind | null {
  if (
    pane === 'picks' ||
    pane === 'listing' ||
    pane === 'topiclist' ||
    pane === 'note' ||
    pane === 'break'
  ) {
    return pane
  }
  return null
}

function applySoftCreditHint(reason: string): string {
  return /积分不足|额度不足|不足/.test(reason)
    ? `${reason}。可前往套餐页升级后再试。`
    : reason
}

function toggleCapsuleSkill(skill: SceneSkillCapsuleItem) {
  if (selectedSkillId.value === skill.skillId) {
    selectedSkillId.value = null
    return
  }
  selectedSkillId.value = skill.skillId
  sessionPrompt.value = skill.examplePrompt || ''
}

function revealComputer() {
  void nextTick(() => {
    computerEl.value?.scrollIntoView({ block: 'nearest' })
  })
}

function openComputer(pane: string) {
  activePane.value = pane
  revealComputer()
}

function closeComputer() {
  activePane.value = null
}

function previewPaneFromStatus(text: string): string | null {
  if (/上架素材|主图位/.test(text)) return 'listing'
  if (/选品/.test(text)) return 'picks'
  if (/笔记草稿/.test(text)) return 'note'
  if (/爆文拆解/.test(text)) return 'break'
  if (/选题/.test(text)) return 'topiclist'
  return null
}

function canPreviewFromStatus(m: ChatMessage): boolean {
  if (m.failed) return false
  if (!/已生成/.test(m.text)) return false
  const pane = previewPaneFromStatus(m.text)
  if (pane) return Boolean(liveByPane.value[pane]?.view)
  return Object.values(liveByPane.value).some((item) => Boolean(item?.view))
}

function isLatestPreviewableStatus(m: ChatMessage): boolean {
  if (!canPreviewFromStatus(m)) return false
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const cur = messages.value[i]
    if (cur && canPreviewFromStatus(cur)) return cur.id === m.id
  }
  return false
}

function isPreviewOpenFromStatus(m: ChatMessage): boolean {
  const pane = previewPaneFromStatus(m.text) || activePane.value
  return Boolean(pane && canPreviewFromStatus(m) && activePane.value === pane)
}

function onStatusCardClick(m: ChatMessage) {
  if (canPreviewFromStatus(m)) {
    if (isPreviewOpenFromStatus(m)) {
      closeComputer()
    } else {
      const pane = previewPaneFromStatus(m.text) || preferredLivePane()
      if (pane) openComputer(pane)
    }
    return
  }
  if (canExpandStatus(m)) toggleStatusExpand(m.id)
}

function onProcessEventClick(e: ProcessEvent) {
  if (canExpandProcessEvent(e)) toggleStreamExpand(e.id)
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
      (awaitingHuman.value && m.id === thinkingMessageId.value),
  )
}

function replaceThinking(thinkingId: string, next: ChatMessage) {
  const idx = messages.value.findIndex((m) => m.id === thinkingId)
  if (idx >= 0) messages.value[idx] = next
  else messages.value.push({ ...next, id: nextMsgId() })
}

async function finishGenerationMessage(opts: {
  thinkingId: string
  error: string
  artifact: GenerationArtifactPayload | null
  pane: string
  processSnapshot: ProcessEvent[] | undefined
}) {
  if (opts.error) {
    const soft = applySoftCreditHint(opts.error)
    replaceThinking(opts.thinkingId, {
      id: opts.thinkingId,
      role: 'agent',
      text: soft,
      processEvents: opts.processSnapshot,
      failed: true,
      statusDetail: buildFailureDetail(soft, opts.processSnapshot || []),
    })
    thinkingMessageId.value = null
    if (!liveByPane.value[opts.pane]?.view) closeComputer()
    scrollChatToBottom()
    return
  }

  if (opts.artifact?.view) {
    setLive(opts.pane, opts.artifact)
    activePane.value = opts.pane
    if (pendingBilledPrompt.value.trim()) {
      lastBilledPrompt.value = pendingBilledPrompt.value.trim()
      lastBilledPane.value = opts.pane
    }
    feedbackHint.value = ''
    revealComputer()
    void loadSessions()
    const blockCount = opts.artifact.view.content.trim() ? 1 : 0
    const reply = successText(opts.pane, blockCount)
    replaceThinking(opts.thinkingId, {
      id: opts.thinkingId,
      role: 'agent',
      text: reply,
      processEvents: opts.processSnapshot,
    })
  } else {
    const empty = emptyText(opts.pane)
    replaceThinking(opts.thinkingId, {
      id: opts.thinkingId,
      role: 'agent',
      text: empty,
      processEvents: opts.processSnapshot,
      failed: true,
      statusDetail: buildFailureDetail(empty, opts.processSnapshot || []),
    })
    if (!liveByPane.value[opts.pane]?.view) closeComputer()
  }
  thinkingMessageId.value = null
  scrollChatToBottom()
}

function finishUnresolved(thinkingId: string) {
  const snapshot = processSnapshot()
  if (skillError.value) {
    const soft = applySoftCreditHint(skillError.value)
    replaceThinking(thinkingId, {
      id: thinkingId,
      role: 'agent',
      text: soft,
      processEvents: snapshot,
      failed: true,
      statusDetail: buildFailureDetail(soft, snapshot || []),
    })
  } else if (skillArtifact.value?.view) {
    replaceThinking(thinkingId, {
      id: thinkingId,
      role: 'agent',
      text: '已生成结果，右侧 Computer 可查看。',
      processEvents: snapshot,
    })
  } else {
    const empty = '处理已结束，但未收到可用成果，请重试。'
    replaceThinking(thinkingId, {
      id: thinkingId,
      role: 'agent',
      text: empty,
      processEvents: snapshot,
      failed: true,
      statusDetail: buildFailureDetail(empty, snapshot || []),
    })
  }
  thinkingMessageId.value = null
  scrollChatToBottom()
}

function presentHumanInput(thinkingId: string, pane: string | null) {
  supplementOpen.value = false
  supplementText.value = ''
  const resolved = pane || inferPaneFromView(skillArtifact.value?.view)
  suspendedPane.value = resolved
  if (resolved && skillArtifact.value?.view) {
    setLive(resolved, skillArtifact.value)
    activePane.value = resolved
    revealComputer()
  }
  const question = pendingHuman.value?.question || '策划分镜已出。请确认出执行稿，或补充需求。'
  replaceThinking(thinkingId, {
    id: thinkingId,
    role: 'agent',
    text: question,
    processEvents: processSnapshot(),
  })
  thinkingMessageId.value = thinkingId
  scrollChatToBottom()
}

async function settleThinking(thinkingId: string, pane: string | null) {
  if (pendingHuman.value) {
    presentHumanInput(thinkingId, pane)
    return
  }
  const artifact = skillArtifact.value
  const resolved = pane || inferPaneFromView(artifact?.view)
  if (resolved) {
    await finishGenerationMessage({
      thinkingId,
      error: skillError.value,
      artifact,
      pane: resolved,
      processSnapshot: processSnapshot(),
    })
    return
  }
  finishUnresolved(thinkingId)
}

async function runSkill(text: string, skillId?: string) {
  const current = spec.value
  if (!current) return
  pendingBilledPrompt.value = text
  const paneFromSkill = skillId ? (current.paneBySkillId[skillId] ?? null) : null
  inFlightPane.value = paneFromSkill
  if (paneFromSkill) activePane.value = paneFromSkill
  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: thinkingLabel(paneFromSkill),
  })
  scrollChatToBottom()
  try {
    await startSkillRun({
      text,
      skillId: skillId || undefined,
      sceneCode: current.sceneCode,
      sceneId: sceneBizId.value ?? undefined,
      sessionId: sessionId.value ?? undefined,
    })
    await settleThinking(thinkingId, paneFromSkill)
  } finally {
    if (!pendingHuman.value) inFlightPane.value = null
  }
}

async function sendFromSession() {
  const text = sessionPrompt.value.trim()
  if (!text || sessionBusy.value || !spec.value) return
  if (hasPromptSlots(text)) {
    messages.value.push({
      id: nextMsgId(),
      role: 'agent',
      text: SLOT_BLOCKED_TEXT,
    })
    scrollChatToBottom()
    return
  }
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  sessionPrompt.value = ''
  scrollChatToBottom()
  await runSkill(text, selectedSkillId.value ?? undefined)
}

function isItemActionEnabled(item: ComputerListItem, index: number): boolean {
  const handoff = activeItemHandoff.value
  if (!handoff) return false
  return Boolean(handoff.buildText(item, index))
}

async function onItemAction(payload: { item: ComputerListItem; index: number }) {
  const handoff = activeItemHandoff.value
  if (!handoff || sessionBusy.value) return
  const text = handoff.buildText(payload.item, payload.index)?.trim()
  if (!text) return
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  scrollChatToBottom()
  await runSkill(text, handoff.targetSkillId)
}

async function onBreakToolbar() {
  if (sessionBusy.value || !breakToolbar.value) return
  const text = buildXhsBreakToolbarText({
    view: liveByPane.value.break?.view,
    lastPrompt: lastBilledPrompt.value || pendingBilledPrompt.value,
  })?.trim()
  if (!text) return
  messages.value.push({ id: nextMsgId(), role: 'user', text })
  scrollChatToBottom()
  await runSkill(text, breakToolbar.value.targetSkillId)
}

async function confirmHumanOption(optionId: string) {
  if (!pendingHuman.value || generationRunning.value) return
  supplementOpen.value = false
  const thinkingId = thinkingMessageId.value
  if (thinkingId && optionId === 'confirm_execute') {
    const idx = messages.value.findIndex((m) => m.id === thinkingId)
    const cur = idx >= 0 ? messages.value[idx] : undefined
    if (cur) {
      messages.value[idx] = {
        id: cur.id,
        role: 'agent',
        text: '正在根据确认生成执行稿…',
        processEvents: processSnapshot() ?? cur.processEvents,
      }
    }
  }
  inFlightPane.value = suspendedPane.value
  await resumeSkillRun({ optionId })
  if (thinkingId) await settleThinking(thinkingId, suspendedPane.value)
  if (!pendingHuman.value) {
    inFlightPane.value = null
    suspendedPane.value = null
  }
}

function openSupplement() {
  supplementOpen.value = !supplementOpen.value
}

async function submitSupplement() {
  if (!pendingHuman.value || generationRunning.value) return
  const note = supplementText.value.trim()
  inFlightPane.value = suspendedPane.value
  await resumeSkillRun({ optionId: 'supplement', freeText: note || undefined })
  supplementText.value = ''
  supplementOpen.value = false
  const thinkingId = thinkingMessageId.value
  if (thinkingId) await settleThinking(thinkingId, suspendedPane.value)
  if (!pendingHuman.value) {
    inFlightPane.value = null
    suspendedPane.value = null
  }
}

async function oneClickRetry() {
  if (!canOneClickRetry.value || !lastBilledPane.value) return
  const text = lastBilledPrompt.value.trim()
  const pane = lastBilledPane.value
  messages.value.push({ id: nextMsgId(), role: 'user', text: `重试：${text}` })
  scrollChatToBottom()
  await runSkill(text, skillIdForPane(pane))
}

function replayMessagesFromApi(
  turns: SessionTurn[] | null | undefined,
  artifactKind: ReplayArtifactKind | null,
): ChatMessage[] {
  return toReplayBubblesFromTurns(turns, artifactKind).map((bubble) => {
    if (bubble.role === 'user') {
      return { id: nextMsgId(), role: 'user' as const, text: bubble.content, at: bubble.at }
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
  const items = Array.isArray(page?.items) ? page.items : []
  if (mode === 'replace') sessionRawTurns.value = items
  else sessionRawTurns.value = [...items, ...sessionRawTurns.value]
  const token = page?.nextToken?.trim() || null
  sessionNextToken.value = token
  sessionHasMore.value = Boolean(token)
}

function lastUserPromptFromReplay(rows: ChatMessage[], titleFallback: string): string {
  for (let i = rows.length - 1; i >= 0; i--) {
    const cur = rows[i]
    if (cur?.role === 'user' && cur.text.trim()) return cur.text.trim()
  }
  return titleFallback.trim()
}

function synthesizePreviewableStatus(pane: string | null): ChatMessage | null {
  if (!pane) return null
  return {
    id: nextMsgId(),
    role: 'agent',
    presentation: 'console',
    text: successText(pane, 1),
  }
}

function paintSessionReplay(pane: string | null) {
  sessionReplayPane.value = pane
  const replayed = replayMessagesFromApi(sessionRawTurns.value, asReplayKind(pane))
  lastBilledPane.value = pane
  lastBilledPrompt.value = pane
    ? lastUserPromptFromReplay(replayed, sessionTitle.value || SESSION_TITLE)
    : ''
  const hasArtifactStatus = replayed.some(
    (m) => m.role === 'agent' && m.presentation === 'console' && /已生成/.test(m.text),
  )
  const status = !hasArtifactStatus ? synthesizePreviewableStatus(pane) : null
  messages.value = status ? [...replayed, status] : replayed
}

function mergeSessionArtifact(
  detail: HistoryArtifactDetail | null | undefined,
  open = false,
): string | null {
  if (!detail?.id || !detail.view) return null
  const view = parseComputerDocument(detail.view)
  if (!view) return null
  const pane = paneFromArtifactType(detail.artifactType) || inferPaneFromView(view)
  if (!pane) return null
  setLive(pane, { artifactRef: detail.id, view })
  if (open) {
    activePane.value = pane
    revealComputer()
  }
  return pane
}

async function loadSessions() {
  const code = spec.value?.sceneCode
  if (!code) return
  try {
    const data = await listSessions(code)
    sessions.value = Array.isArray(data) ? data : []
    sessionsError.value = ''
  } catch (e) {
    sessions.value = []
    sessionsError.value = e instanceof ApiError ? e.message : '会话加载失败'
  }
}

async function selectSession(item: SessionSummary) {
  const sid = item.sessionId?.trim()
  const current = spec.value
  if (!sid || !current) return
  const seq = ++workspaceSwitchSeq
  resetSkillRun()
  supplementOpen.value = false
  supplementText.value = ''
  thinkingMessageId.value = null
  resetConsoleExpand()
  sessionId.value = sid
  selectedSessionId.value = sid
  sessionTitle.value = item.title || SESSION_TITLE
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayPane.value = null
  lastBilledPrompt.value = ''
  lastBilledPane.value = null
  pendingBilledPrompt.value = ''
  inFlightPane.value = null
  suspendedPane.value = null
  resetFeedbackUi()
  clearSessionArtifacts()
  try {
    const [page, latest, ...typed] = await Promise.all([
      getSessionMessages(sid),
      getLatestSessionArtifact(sid),
      ...current.artifactTypes.map((artifactType) =>
        getLatestSessionArtifact(sid, artifactType as SessionArtifactType),
      ),
    ])
    if (seq !== workspaceSwitchSeq) return
    clearSessionArtifacts()
    for (const art of typed) mergeSessionArtifact(art, false)
    const pane = paneFromArtifactType(latest?.artifactType) || preferredLivePane()
    applyMessagePage(page, 'replace')
    paintSessionReplay(pane)
    scrollChatToBottom()
  } catch (e) {
    if (seq !== workspaceSwitchSeq) return
    feedbackHint.value = e instanceof ApiError ? e.message : '会话加载失败'
  }
}

async function loadMoreSessionHistory() {
  const sid = selectedSessionId.value?.trim()
  const token = sessionNextToken.value
  if (!sid || !sessionHasMore.value || !token || sessionHistoryLoading.value || sessionBusy.value) {
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
    paintSessionReplay(sessionReplayPane.value)
    await nextTick()
    if (el) el.scrollTop = Math.max(0, el.scrollHeight - prevHeight)
  } catch (e) {
    if (switchSeq !== workspaceSwitchSeq) return
    feedbackHint.value = e instanceof ApiError ? e.message : '加载更多失败'
  } finally {
    if (switchSeq === workspaceSwitchSeq) sessionHistoryLoading.value = false
  }
}

function newTask() {
  workspaceSwitchSeq += 1
  resetSkillRun()
  supplementOpen.value = false
  supplementText.value = ''
  thinkingMessageId.value = null
  resetConsoleExpand()
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayPane.value = null
  activePane.value = null
  liveByPane.value = {}
  sessionPrompt.value = ''
  selectedSkillId.value = null
  sessionTitle.value = SESSION_TITLE
  selectedSessionId.value = null
  lastBilledPrompt.value = ''
  lastBilledPane.value = null
  pendingBilledPrompt.value = ''
  inFlightPane.value = null
  suspendedPane.value = null
  resetFeedbackUi()
  void loadSessions()
}

async function bootstrapScene() {
  const seq = ++workspaceSwitchSeq
  resetSkillRun()
  supplementOpen.value = false
  supplementText.value = ''
  thinkingMessageId.value = null
  resetConsoleExpand()
  messages.value = []
  sessionRawTurns.value = []
  sessionHasMore.value = false
  sessionNextToken.value = null
  sessionHistoryLoading.value = false
  sessionReplayPane.value = null
  activePane.value = null
  liveByPane.value = {}
  sessionPrompt.value = ''
  selectedSkillId.value = null
  sessionTitle.value = SESSION_TITLE
  selectedSessionId.value = null
  lastBilledPrompt.value = ''
  lastBilledPane.value = null
  pendingBilledPrompt.value = ''
  inFlightPane.value = null
  suspendedPane.value = null
  resetFeedbackUi()
  sceneBizId.value = null
  skillCapsules.value = []
  sessions.value = []
  sessionsError.value = ''
  const current = spec.value
  if (!current) return
  const code = current.sceneCode
  try {
    const data = await getScenes()
    if (seq !== workspaceSwitchSeq) return
    const hit = Array.isArray(data)
      ? data.find((scene) => scene.sceneCode === code && scene.status === 'AVAILABLE')
      : undefined
    if (hit?.bizId) sceneBizId.value = hit.bizId
  } catch {
    // sceneCode alone still works
  }
  try {
    const capsule = await getSceneSkillCapsules(code)
    if (seq !== workspaceSwitchSeq) return
    skillCapsules.value = Array.isArray(capsule?.skills) ? capsule.skills : []
  } catch {
    if (seq !== workspaceSwitchSeq) return
    skillCapsules.value = []
  }
  if (seq !== workspaceSwitchSeq) return
  await loadSessions()
}

watch(skillArtifact, (value) => {
  if (!value?.view) return
  const pane = inFlightPane.value || inferPaneFromView(value.view)
  if (!pane) return
  setLive(pane, value)
  if (inFlightPane.value) activePane.value = inFlightPane.value
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
    processEvents: processSnapshot(),
  }
})

watch(sessionId, (id, prev) => {
  selectedSessionId.value = id
  if (id && id !== prev) void loadSessions()
})

watch(sceneCode, () => {
  void bootstrapScene()
}, { immediate: true })
</script>

<template>
  <div v-if="!spec" class="shell unknown-scene" data-testid="unknown-scene">
    <AppHeader :hide-secondary-nav="true" />
    <main class="unknown-copy">
      <p>这个场景还没有工作台。</p>
      <RouterLink :to="{ name: 'scenes' }">回画廊</RouterLink>
    </main>
  </div>
  <template v-else>
    <div
      class="shell"
      :data-scene-code="spec.sceneCode"
      :data-scene-biz-id="sceneBizId ?? undefined"
    >
      <AppHeader :scene-breadcrumb="spec.breadcrumb" :hide-secondary-nav="true" />

      <div class="session" data-testid="session-shell">
        <WorkspaceSessionSidebar
          :sessions="sessions"
          :selected-session-id="selectedSessionId"
          :sessions-error="sessionsError"
          :scene-label="spec.breadcrumb"
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
              <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
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
                  v-if="awaitingHuman && pendingHuman"
                  class="ask-human"
                  data-testid="ask-human"
                >
                  <div class="ask-human-head">
                    <span class="ask-human-eyebrow">需要你确认</span>
                    <span class="ask-human-hint">右侧 Computer 可先看策划</span>
                  </div>
                  <p class="ask-human-q">{{ pendingHuman.question }}</p>
                  <div class="ask-human-actions" role="group" aria-label="确认策划">
                    <template v-for="opt in pendingHuman.options" :key="opt.id">
                      <button
                        v-if="opt.id === 'supplement'"
                        type="button"
                        class="ask-human-secondary"
                        data-testid="ask-human-supplement"
                        :disabled="generationRunning"
                        @click="openSupplement"
                      >
                        {{ supplementOpen ? '收起补充' : opt.label }}
                      </button>
                      <button
                        v-else
                        type="button"
                        :class="opt.id === 'confirm_execute' ? 'ask-human-primary' : 'ask-human-secondary'"
                        :data-testid="opt.id === 'confirm_execute' ? 'ask-human-confirm' : `ask-human-${opt.id}`"
                        :disabled="generationRunning"
                        @click="confirmHumanOption(opt.id)"
                      >
                        {{ opt.label }}
                      </button>
                    </template>
                  </div>
                  <div
                    v-if="supplementOpen"
                    class="ask-human-supplement"
                    data-testid="ask-human-supplement-form"
                  >
                    <textarea
                      v-model="supplementText"
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
                      @click="submitSupplement"
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
              <div class="computer-bar-actions">
                <button
                  v-if="breakToolbar"
                  type="button"
                  class="pill"
                  data-testid="break-note-handoff"
                  :disabled="sessionBusy"
                  @click="onBreakToolbar"
                >
                  {{ breakToolbar.actionLabel }}
                </button>
                <button type="button" class="icon-btn" id="close-computer" aria-label="关闭" @click="closeComputer">
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
                :file-name="computerFileName"
                :item-action-label="itemActionLabel"
                :is-item-action-enabled="isItemActionEnabled"
                @item-action="onItemAction"
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
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.unknown-copy {
  max-width: 36em;
  margin: 48px auto;
  padding: 0 24px;
}

.unknown-copy p {
  margin: 0 0 12px;
  color: var(--ink);
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
</style>
