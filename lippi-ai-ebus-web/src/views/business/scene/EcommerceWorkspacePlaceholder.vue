<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import { getScenes } from '@/api/business/scene/scene'
import { formatEventTime, processEventDisplayLabel, type ProcessEvent } from '@/composables/agent/agentProgress'
import { useAgentPicklistRun } from '@/composables/agent/useAgentPicklistRun'
import {
  DEMO_LISTING_VIEW,
  DEMO_PICKS,
  DEMO_SESSION_TITLE,
} from '@/views/business/scene/ecommerceDemoFixtures'
import type { PicklistArtifactPayload } from '@/types/business/agent'
import '@/views/business/scene/ecommerceWorkspaceSession.css'

/** Stable scene binding for this workbench — Epic 3 session create must carry it */
const SCENE_CODE = 'ecommerce' as const
const SCENE_BREADCRUMB = '电商开店'
const ATTACH_SOON = '近端暂不支持附件'

/** Concrete defaults — capsule one-click must be sendable (no 【占位】). */
const PICKS_TEMPLATE = '请帮我生成厨房小件类选品清单，客单价 19–39 元。'
const LISTING_TEMPLATE =
  '请为商品「硅胶沥水垫」生成上架素材，优先适配淘宝。'

const TEMPLATE_SLOT_MARK = /【品类】|【最低价】|【最高价】|【商品名称】|【淘宝\/拼多多\/闲鱼】/

type ComputerKind = 'picks' | 'listing' | null

interface ChatMessage {
  id: string
  role: 'user' | 'agent'
  text: string
  /** Ordered AGENT / LLM / TOOL rows from SSE */
  processEvents?: ProcessEvent[]
}

const sessionPrompt = ref('')
/** Optional Catalog bizId when list is available; null if unresolved */
const sceneBizId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const sessionTitle = ref(DEMO_SESSION_TITLE)
const computerKind = ref<ComputerKind>(null)
const livePicklist = ref<PicklistArtifactPayload | null>(null)
const chatScrollEl = ref<HTMLElement | null>(null)
const computerEl = ref<HTMLElement | null>(null)
const thinkingMessageId = ref<string | null>(null)
const expandedStreamIds = ref(new Set<string>())

const {
  running: picklistRunning,
  error: picklistError,
  artifact: picklistArtifact,
  sessionId,
  processEvents,
  startPicklistRun,
  reset: resetPicklistRun,
} = useAgentPicklistRun()

const computerOpen = computed(() => computerKind.value != null)
const sessionSendEnabled = computed(
  () => sessionPrompt.value.trim().length > 0 && !picklistRunning.value,
)

const displayPicks = computed(() => {
  if (livePicklist.value?.items?.length) {
    return livePicklist.value.items
  }
  return DEMO_PICKS.map((p) => ({
    title: p.title,
    priceBand: '',
    painPoint: p.painPoint,
    angle: p.angle,
    diff: p.diff,
    niche: p.niche,
    demand: '',
    competition: '',
    margin: '',
    risk: '',
  }))
})

const picksIsLive = computed(() => Boolean(livePicklist.value?.items?.length))

const activeComputerDoc = computed(() => {
  if (computerKind.value === 'listing') return DEMO_LISTING_VIEW
  if (computerKind.value === 'picks') {
    if (livePicklist.value?.view) return livePicklist.value.view
    return null
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

function fillPicksSession() {
  sessionPrompt.value = PICKS_TEMPLATE
}

function fillListingSession() {
  sessionPrompt.value = LISTING_TEMPLATE
}

function isPicklistIntent(text: string): boolean {
  const t = text.trim()
  if (!t) return false
  if (/上架|listing|主图|详情文案/i.test(t) && !/选品/.test(t)) {
    return false
  }
  return /选品|测款|品类|候选|可卖|帮我选/.test(t) || t.includes('选品清单')
}

async function sendFromSession() {
  const text = sessionPrompt.value.trim()
  if (!text || picklistRunning.value) return

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

  if (!isPicklistIntent(text)) {
    messages.value.push({
      id: nextMsgId(),
      role: 'agent',
      text: '近端可生成「选品清单」。请用上方选品胶囊，或直接描述品类与客单价。上架素材将在后续故事接入。',
    })
    scrollChatToBottom()
    return
  }

  const thinkingId = nextMsgId()
  thinkingMessageId.value = thinkingId
  messages.value.push({
    id: thinkingId,
    role: 'agent',
    text: '正在生成选品清单…',
  })
  scrollChatToBottom()

  await startPicklistRun({
    text,
    sceneCode: SCENE_CODE,
    sceneId: sceneBizId.value ?? undefined,
    sessionId: sessionId.value ?? undefined,
  })

  const idx = messages.value.findIndex((m) => m.id === thinkingId)
  const processSnapshot = processEvents.value.length ? [...processEvents.value] : undefined
  if (picklistError.value) {
    const reason = picklistError.value
    const soft =
      /积分不足|额度不足|不足/.test(reason)
        ? `${reason}。可前往套餐页升级后再试。`
        : reason
    if (idx >= 0) {
      messages.value[idx] = {
        id: thinkingId,
        role: 'agent',
        text: soft,
        processEvents: processSnapshot,
      }
    } else {
      messages.value.push({
        id: nextMsgId(),
        role: 'agent',
        text: soft,
        processEvents: processSnapshot,
      })
    }
    thinkingMessageId.value = null
    scrollChatToBottom()
    return
  }

  if (picklistArtifact.value?.items?.length) {
    livePicklist.value = picklistArtifact.value
    computerKind.value = 'picks'
    revealComputer()
    const n = picklistArtifact.value.items.length
    const reply = `已生成 ${n} 条选品候选，右侧 Computer 可查看四维简评与可卖理由。`
    if (idx >= 0) {
      messages.value[idx] = {
        id: thinkingId,
        role: 'agent',
        text: reply,
        processEvents: processSnapshot,
      }
    } else {
      messages.value.push({
        id: nextMsgId(),
        role: 'agent',
        text: reply,
        processEvents: processSnapshot,
      })
    }
  } else {
    const fallback = '选品已结束，但未收到可用清单，请重试。'
    if (idx >= 0) {
      messages.value[idx] = {
        id: thinkingId,
        role: 'agent',
        text: fallback,
        processEvents: processSnapshot,
      }
    } else {
      messages.value.push({
        id: nextMsgId(),
        role: 'agent',
        text: fallback,
        processEvents: processSnapshot,
      })
    }
  }
  thinkingMessageId.value = null
  scrollChatToBottom()
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

function canPreviewFromStatus(m: ChatMessage): boolean {
  return Boolean(livePicklist.value?.items?.length) || /已生成/.test(m.text)
}

function onStatusCardClick(m: ChatMessage) {
  if (!canPreviewFromStatus(m)) return
  openPicksComputer()
}

function onProcessEventClick(e: ProcessEvent) {
  if (e.kind === 'llm') {
    toggleStreamExpand(e.id)
  }
}

function closeComputer() {
  computerKind.value = null
}

function newTask() {
  resetPicklistRun()
  thinkingMessageId.value = null
  expandedStreamIds.value = new Set()
  messages.value = []
  computerKind.value = null
  livePicklist.value = null
  sessionPrompt.value = ''
  sessionTitle.value = DEMO_SESSION_TITLE
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

function padIndex(i: number) {
  return String(i + 1).padStart(2, '0')
}

function eventTag(kind: ProcessEvent['kind']): string {
  if (kind === 'agent') return 'AGENT'
  if (kind === 'llm') return 'LLM'
  return 'TOOL'
}

function eventTitle(e: ProcessEvent): string {
  return processEventDisplayLabel(e.title)
}

watch(picklistArtifact, (value) => {
  if (value?.items?.length) {
    livePicklist.value = value
  }
})

watch(processEvents, () => {
  if (!picklistRunning.value || !thinkingMessageId.value) return
  const idx = messages.value.findIndex((m) => m.id === thinkingMessageId.value)
  if (idx < 0) return
  const cur = messages.value[idx]
  messages.value[idx] = {
    ...cur,
    processEvents: processEvents.value.length ? [...processEvents.value] : undefined,
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
        <div class="side-section">当前</div>
        <button type="button" class="side-item on">
          {{ sessionTitle }}
          <span class="sub">进行中 · 电商开店</span>
        </button>
      </aside>

      <div class="workspace" :class="{ split: computerOpen }">
        <section class="chat-pane">
          <div class="chat-toolbar">
            <button type="button" class="chat-new-task" @click="newTask">新任务</button>
          </div>
          <div ref="chatScrollEl" class="chat-scroll" role="log" aria-live="polite">
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="m.role"
            >
              <div class="role">{{ m.role === 'user' ? '你' : 'Adam' }}</div>
              <div class="body">
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
                      open: e.kind === 'llm' && expandedStreamIds.has(e.id),
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
                      <template v-if="e.kind === 'llm'">
                        <div class="chat-event-head" :aria-expanded="expandedStreamIds.has(e.id)">
                          <span class="chat-event-title">{{ eventTitle(e) }}</span>
                          <span class="chat-stream-toggle">{{
                            expandedStreamIds.has(e.id) ? '收起' : '展开'
                          }}</span>
                        </div>
                        <pre v-if="expandedStreamIds.has(e.id)" class="chat-stream-body">{{
                          e.body || ''
                        }}</pre>
                      </template>
                      <span v-else class="chat-event-title">{{ eventTitle(e) }}</span>
                    </div>
                    <span
                      v-if="e.kind === 'llm'"
                      class="chat-event-chevron"
                      aria-hidden="true"
                    >{{ expandedStreamIds.has(e.id) ? '▾' : '▸' }}</span>
                    <span
                      v-else-if="e.kind === 'tool'"
                      class="chat-event-mark"
                      aria-hidden="true"
                    >{{ e.done ? '✓' : '…' }}</span>
                    <span v-else class="chat-event-mark" aria-hidden="true" />
                  </div>
                </div>
                <p v-if="m.role === 'user'">{{ m.text }}</p>
                <button
                  v-else-if="m.text"
                  type="button"
                  class="chat-event chat-event-status"
                  :class="{
                    active: picklistRunning && m.id === thinkingMessageId,
                    'is-preview': canPreviewFromStatus(m),
                  }"
                  :disabled="!canPreviewFromStatus(m)"
                  @click="onStatusCardClick(m)"
                >
                  <span class="chat-event-time">{{
                    formatEventTime(m.processEvents?.[m.processEvents.length - 1]?.at ?? Date.now())
                  }}</span>
                  <span class="chat-event-tag tag-status">STATUS</span>
                  <div class="chat-event-main">
                    <div class="chat-event-head">
                      <span class="chat-event-title chat-result-text">{{ m.text }}</span>
                      <span v-if="canPreviewFromStatus(m)" class="chat-stream-toggle">查看</span>
                    </div>
                  </div>
                  <span
                    v-if="canPreviewFromStatus(m)"
                    class="chat-event-chevron"
                    aria-hidden="true"
                  >▸</span>
                  <span
                    v-else
                    class="chat-event-mark"
                    aria-hidden="true"
                  >{{ picklistRunning && m.id === thinkingMessageId ? '…' : '✓' }}</span>
                </button>
                <div v-if="m.role === 'agent' && !picksIsLive" class="demo-actions">
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
              <div class="quick-row" role="group" aria-label="快捷任务">
                <button type="button" class="pill" :disabled="picklistRunning" @click="fillPicksSession">
                  选品清单
                </button>
                <button type="button" class="pill" :disabled="picklistRunning" @click="fillListingSession">
                  生成上架素材
                </button>
              </div>
              <div class="prompt-box">
                <textarea
                  v-model="sessionPrompt"
                  class="prompt-editor"
                  rows="2"
                  placeholder="分配一个任务或提问任何问题"
                  aria-label="继续提问"
                  :disabled="picklistRunning"
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
            <ComputerRenderer v-if="activeComputerDoc" :document="activeComputerDoc" />
            <!-- FallbackPicklistCard：无 view 时用旧字段，禁止 FE project() -->
            <div v-else-if="computerKind === 'picks'" class="comp-card">
              <div class="comp-card-head">
                <span>选品清单</span>
                <span class="status">{{ picksIsLive ? '已结算' : '演示' }}</span>
              </div>
              <div class="comp-card-body">
                <p v-if="livePicklist?.disclaimer" class="pick-disclaimer">{{ livePicklist.disclaimer }}</p>
                <p v-if="livePicklist?.assumptions" class="pick-assumptions">假设：{{ livePicklist.assumptions }}</p>
                <ol class="pick-list pick-list--legacy">
                  <li v-for="(item, i) in displayPicks" :key="item.title + '-' + i">
                    <span class="n">{{ padIndex(i) }}</span>
                    <div>
                      <div class="t">
                        <span
                          v-if="item.title.startsWith('【优先试】')"
                          class="priority-tag"
                        >优先试</span>
                        {{ item.title.replace(/^【优先试】/, '') }}
                      </div>
                      <div v-if="item.priceBand" class="r">价格带：{{ item.priceBand }}</div>
                      <div v-if="item.painPoint" class="r">痛点：{{ item.painPoint }}</div>
                      <div v-if="item.angle" class="r">切入：{{ item.angle }}</div>
                      <div v-if="item.diff" class="r">差异：{{ item.diff }}</div>
                      <div v-if="item.niche" class="r dim">细分：{{ item.niche }}</div>
                      <div v-if="item.demand" class="dims">
                        <span>需求 {{ item.demand }}</span>
                        <span>竞争 {{ item.competition }}</span>
                        <span>利润 {{ item.margin }}</span>
                        <span>风险 {{ item.risk }}</span>
                      </div>
                    </div>
                  </li>
                </ol>
              </div>
            </div>
          </div>
        </aside>
      </div>
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

.pill svg {
  opacity: 0.7;
  flex-shrink: 0;
}

.prompt-box {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow);
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prompt-editor {
  border: 0;
  resize: none;
  min-height: 56px;
  background: transparent;
  color: var(--ink);
  font-size: 0.95rem;
  line-height: 1.7;
  width: 100%;
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

.pick-disclaimer,
.pick-assumptions {
  margin: 0 0 10px;
  font-size: 0.75rem;
  color: var(--mute);
  line-height: 1.5;
}

.dims {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 6px;
  font-size: 0.72rem;
  color: var(--mute);
  line-height: 1.4;
}

.priority-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  font-size: 0.68rem;
  font-weight: 650;
  color: #7c4a1e;
  background: #f3e8d8;
  border-radius: 4px;
  border: 0;
  vertical-align: 1px;
  line-height: 1.35;
}

.r.dim {
  opacity: 0.85;
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
