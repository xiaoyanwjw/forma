<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import DocPreview from '@/components/business/computer/DocPreview.vue'
import { ApiError } from '@/api/client'
import { getHistoryArtifact, getHistoryArtifacts } from '@/api/business/history/history'
import { getSessionMessages } from '@/api/business/session/session'
import { formatEventTime } from '@/composables/agent/agentProgress'
import { parseComputerDocView } from '@/types/business/computerView'
import type { HistoryArtifactDetail, HistoryArtifactSummary } from '@/types/business/history'
import type { SessionTurn } from '@/types/business/session'
import { toReplayBubblesFromTurns } from '@/utils/sessionReplay'

const SCENE_OPTIONS = [
  { value: '', label: '全部场景' },
  { value: 'ecommerce', label: '电商开店' },
  { value: 'xiaohongshu', label: '小红书种草' },
] as const

const sceneFilter = ref('')
const items = ref<HistoryArtifactSummary[]>([])
const loading = ref(false)
const listError = ref('')
const selectedId = ref<string | null>(null)
const detail = ref<HistoryArtifactDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const drawerOpen = ref(false)
const activeTab = ref<'artifact' | 'chat'>('chat')
const chatMessages = ref<HistoryChatBubble[]>([])
const chatRawTurns = ref<SessionTurn[]>([])
const chatHasMore = ref(false)
const chatNextToken = ref<string | null>(null)
const chatError = ref('')
const chatLoading = ref(false)
const chatLoadingMore = ref(false)
/** 忽略过期详情响应，避免连点串扰 */
let detailRequestSeq = 0
let chatRequestSeq = 0
/** 已成功加载过的会话，避免 Tab 来回重复拉 */
let loadedChatSessionId: string | null = null

type HistoryChatBubble = {
  role: string
  content: string
  createdAt?: string | null
  kind: 'text' | 'artifact'
}

const computerDoc = computed(() => {
  if (!detail.value?.view) return null
  return parseComputerDocView(detail.value.view)
})

const sessionId = computed(() => detail.value?.sessionId?.trim() || '')

function sceneLabel(code: string): string {
  if (code === 'ecommerce') return '电商开店'
  return code || '场景'
}

function typeLabel(type: string): string {
  if (type === 'picklist') return '选品'
  if (type === 'sku') return 'Listing'
  return type
}

function formatWhen(iso: string): string {
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return iso
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadList() {
  loading.value = true
  listError.value = ''
  try {
    const data = await getHistoryArtifacts(sceneFilter.value || undefined)
    items.value = Array.isArray(data) ? data : []
  } catch (e) {
    items.value = []
    listError.value = e instanceof ApiError ? e.message : '历史加载失败'
  } finally {
    loading.value = false
  }
}

function resetChat() {
  chatRequestSeq += 1
  loadedChatSessionId = null
  chatMessages.value = []
  chatRawTurns.value = []
  chatHasMore.value = false
  chatNextToken.value = null
  chatError.value = ''
  chatLoading.value = false
  chatLoadingMore.value = false
}

function closeDrawer() {
  detailRequestSeq += 1
  resetChat()
  drawerOpen.value = false
  selectedId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  activeTab.value = 'chat'
}

async function openItem(item: HistoryArtifactSummary) {
  const seq = ++detailRequestSeq
  resetChat()
  selectedId.value = item.id
  drawerOpen.value = true
  activeTab.value = 'chat'
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const next = await getHistoryArtifact(item.id)
    if (seq !== detailRequestSeq) {
      return
    }
    detail.value = next
  } catch (e) {
    if (seq !== detailRequestSeq) {
      return
    }
    detailError.value = e instanceof ApiError ? e.message : '详情加载失败'
  } finally {
    if (seq === detailRequestSeq) {
      detailLoading.value = false
    }
  }
}

function paintHistoryChat(turns: SessionTurn[]) {
  const kind =
    detail.value?.artifactType === 'sku'
      ? 'listing'
      : detail.value?.artifactType === 'picklist'
        ? 'picks'
        : null
  chatMessages.value = toReplayBubblesFromTurns(turns, kind).map((b) => ({
    role: b.role,
    content: b.content,
    createdAt: b.at != null ? new Date(b.at).toISOString() : null,
    kind: b.kind,
  }))
}

function openArtifactPreview() {
  activeTab.value = 'artifact'
}

function backToChat() {
  activeTab.value = 'chat'
}

function bubbleTimeLabel(createdAt?: string | null): string {
  if (!createdAt) return ''
  const ms = Date.parse(createdAt)
  if (Number.isNaN(ms)) return ''
  return formatEventTime(ms)
}

async function loadChat() {
  const sid = sessionId.value
  if (!sid) {
    loadedChatSessionId = null
    chatMessages.value = []
    chatRawTurns.value = []
    chatHasMore.value = false
    chatNextToken.value = null
    chatError.value = ''
    chatLoading.value = false
    return
  }
  if (loadedChatSessionId === sid && chatRawTurns.value.length > 0 && !chatError.value) {
    return
  }
  const seq = ++chatRequestSeq
  chatLoading.value = true
  chatError.value = ''
  try {
    const page = await getSessionMessages(sid)
    if (seq !== chatRequestSeq) {
      return
    }
    const items = Array.isArray(page?.items) ? page.items : []
    chatRawTurns.value = items
    const token = page?.nextToken?.trim() || null
    chatNextToken.value = token
    chatHasMore.value = Boolean(token)
    paintHistoryChat(chatRawTurns.value)
    loadedChatSessionId = sid
  } catch (e) {
    if (seq !== chatRequestSeq) {
      return
    }
    loadedChatSessionId = null
    chatMessages.value = []
    chatRawTurns.value = []
    chatHasMore.value = false
    chatNextToken.value = null
    chatError.value = e instanceof ApiError ? e.message : '会话加载失败'
  } finally {
    if (seq === chatRequestSeq) {
      chatLoading.value = false
    }
  }
}

async function loadMoreChat() {
  const sid = sessionId.value
  const token = chatNextToken.value
  if (!sid || !chatHasMore.value || !token || chatLoadingMore.value) {
    return
  }
  const seq = ++chatRequestSeq
  chatLoadingMore.value = true
  chatError.value = ''
  try {
    const page = await getSessionMessages(sid, { nextToken: token })
    if (seq !== chatRequestSeq) {
      return
    }
    const items = Array.isArray(page?.items) ? page.items : []
    chatRawTurns.value = [...items, ...chatRawTurns.value]
    const next = page?.nextToken?.trim() || null
    chatNextToken.value = next
    chatHasMore.value = Boolean(next)
    paintHistoryChat(chatRawTurns.value)
  } catch (e) {
    if (seq !== chatRequestSeq) {
      return
    }
    chatError.value = e instanceof ApiError ? e.message : '加载更多失败'
  } finally {
    if (seq === chatRequestSeq) {
      chatLoadingMore.value = false
    }
  }
}

watch([activeTab, detail], () => {
  if (activeTab.value !== 'chat') {
    return
  }
  void loadChat()
})

async function onSceneChange() {
  closeDrawer()
  await loadList()
}

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="shell">
    <AppHeader active-nav="history" />
    <main class="page">
      <div class="inner">
        <h1>生成历史</h1>
        <p class="lead">近 60 天内的选品清单与上架素材，可随时回看。</p>

        <div class="toolbar">
          <label class="filter">
            <span class="filter-label">场景</span>
            <select
              v-model="sceneFilter"
              data-testid="history-scene-filter"
              @change="onSceneChange"
            >
              <option v-for="opt in SCENE_OPTIONS" :key="opt.value || 'all'" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </label>
        </div>

        <p v-if="loading" class="hint" data-testid="history-loading">加载中…</p>
        <p v-else-if="listError" class="hint error" data-testid="history-error">{{ listError }}</p>
        <p v-else-if="items.length === 0" class="hint" data-testid="history-empty">
          近 60 天还没有选品或上架成果。去电商工作台生成一条后会显示在这里。
        </p>

        <div v-else class="history-list" data-testid="history-list">
          <article
            v-for="item in items"
            :key="item.id"
            class="history-item"
            :class="{ on: selectedId === item.id }"
            role="button"
            tabindex="0"
            @click="openItem(item)"
            @keydown.enter.prevent="openItem(item)"
            @keydown.space.prevent="openItem(item)"
          >
            <div>
              <h3>{{ item.title || '未命名成果' }}</h3>
              <p>{{ sceneLabel(item.sceneCode) }} · {{ formatWhen(item.createdAt) }}</p>
            </div>
            <span class="tag">{{ typeLabel(item.artifactType) }}</span>
          </article>
        </div>

      </div>
    </main>

    <div
      v-if="drawerOpen"
      class="drawer-root"
      data-testid="history-drawer"
    >
      <button
        type="button"
        class="drawer-mask"
        aria-label="关闭预览"
        @click="closeDrawer"
      />
      <aside class="drawer-panel" role="dialog" aria-label="历史预览">
        <section
          v-if="activeTab === 'artifact'"
          class="detail"
          data-testid="history-detail"
          aria-label="成果预览"
        >
          <p v-if="detailLoading" class="hint">正在打开预览…</p>
          <p v-else-if="detailError" class="hint error">{{ detailError }}</p>
          <div v-else-if="computerDoc" class="computer-pane">
            <div class="computer-bar">
              <div class="computer-bar-left">
                <button
                  type="button"
                  class="computer-back"
                  data-testid="history-back-to-chat"
                  aria-label="返回对话"
                  title="返回对话"
                  @click="backToChat"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"
                    />
                  </svg>
                </button>
                <span>Adam's Computer</span>
              </div>
              <span class="mute">{{ detail?.title }}</span>
            </div>
            <div class="computer-body">
              <DocPreview :document="computerDoc" />
            </div>
          </div>
          <p v-else class="hint">该成果暂无可用预览。</p>
        </section>

        <section
          v-if="activeTab === 'chat'"
          class="chat-pane"
          data-testid="history-chat"
          aria-label="对话回放"
        >
          <p v-if="!detailLoading && !sessionId" class="hint" data-testid="history-chat-empty">
            暂无会话记录
          </p>
          <p v-else-if="chatLoading" class="hint">正在加载对话…</p>
          <p v-else-if="chatError" class="hint error">{{ chatError }}</p>
          <div v-else class="chat-list history-chat-stream">
            <div v-if="chatHasMore" class="chat-load-more">
              <button
                type="button"
                data-testid="history-chat-load-more"
                :disabled="chatLoadingMore"
                @click="loadMoreChat"
              >
                {{ chatLoadingMore ? '加载中…' : '加载更多' }}
              </button>
            </div>
            <div
              v-for="(m, i) in chatMessages"
              :key="i"
              class="msg"
              :class="m.role === 'user' ? 'user' : 'agent'"
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
                <p v-if="m.role === 'user'" class="msg-text" data-testid="history-user-bubble">
                  {{ m.content }}
                </p>
                <div
                  v-else-if="m.kind === 'artifact'"
                  class="chat-console"
                >
                  <div
                    class="chat-event chat-event-status is-preview expandable"
                    data-testid="history-agent-bubble"
                    role="button"
                    tabindex="0"
                    @click="openArtifactPreview"
                    @keydown.enter.prevent="openArtifactPreview"
                    @keydown.space.prevent="openArtifactPreview"
                  >
                    <span v-if="bubbleTimeLabel(m.createdAt)" class="chat-event-time">{{
                      bubbleTimeLabel(m.createdAt)
                    }}</span>
                    <span v-else class="chat-event-time" aria-hidden="true" />
                    <span class="chat-event-tag tag-status">OK</span>
                    <div class="chat-event-main">
                      <div class="chat-event-head">
                        <span class="chat-event-title chat-result-text">{{ m.content }}</span>
                        <span class="chat-stream-toggle">查看</span>
                      </div>
                    </div>
                  </div>
                </div>
                <p
                  v-else
                  class="msg-text agent-text history-agent-text"
                  data-testid="history-agent-bubble"
                  role="button"
                  tabindex="0"
                  @click="openArtifactPreview"
                  @keydown.enter.prevent="openArtifactPreview"
                  @keydown.space.prevent="openArtifactPreview"
                >
                  <span v-if="bubbleTimeLabel(m.createdAt)" class="msg-time">{{
                    bubbleTimeLabel(m.createdAt)
                  }}</span>
                  {{ m.content }}
                </p>
              </div>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.page {
  min-height: calc(100vh - var(--header-h));
}

.inner {
  max-width: 880px;
  margin: 0 auto;
  padding: 40px 24px 80px;
}

h1 {
  margin: 0 0 8px;
  font-size: 1.75rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.lead {
  margin: 0 0 28px;
  color: var(--mute);
}

.toolbar {
  margin-bottom: 16px;
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.875rem;
  color: var(--mute);
}

.filter select {
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink);
  padding: 6px 10px;
  font-size: 0.875rem;
}

.hint {
  margin: 0;
  color: var(--mute);
  font-size: 0.9rem;
}

.hint.error {
  color: var(--danger, #b42318);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  cursor: pointer;
  text-align: left;
}

.history-item:hover,
.history-item.on {
  border-color: var(--ink);
}

.history-item h3 {
  margin: 0 0 4px;
  font-size: 0.95rem;
}

.history-item p {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--mute);
}

.tag {
  font-size: 0.75rem;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--mute);
  flex-shrink: 0;
}

.detail {
  margin-top: 0;
}

.computer-pane {
  border: 0;
  border-radius: 0;
  overflow: hidden;
  background: var(--surface);
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.computer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  font-size: 0.8125rem;
  font-weight: 600;
}

.computer-bar-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.computer-back {
  flex-shrink: 0;
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink, #171717);
  cursor: pointer;
}

.computer-back:hover {
  background: color-mix(in srgb, var(--chip, #f5f5f5) 80%, var(--line));
}

.computer-back svg {
  width: 20px;
  height: 20px;
  display: block;
}

.computer-bar .mute {
  font-weight: 400;
  color: var(--mute);
}

.computer-body {
  padding: 12px 14px 20px;
  max-height: none;
  overflow: auto;
  flex: 1;
}

.drawer-root {
  position: fixed;
  top: var(--header-h, 56px);
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 40;
  pointer-events: none;
}

.drawer-mask {
  position: absolute;
  inset: 0;
  border: 0;
  background: rgba(15, 18, 24, 0.28);
  cursor: pointer;
  pointer-events: auto;
}

.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 50%;
  max-width: 100%;
  background: var(--bg, #fff);
  border-left: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  box-shadow: -12px 0 32px rgba(15, 18, 24, 0.08);
}

@media (max-width: 860px) {
  .drawer-panel {
    width: 100%;
  }
}

.drawer-panel .detail,
.chat-pane {
  flex: 1;
  overflow: auto;
  padding: 0;
  margin: 0;
}

.chat-pane {
  padding: 16px 20px 28px;
}

.drawer-panel .detail .hint {
  padding: 16px 20px;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.chat-load-more {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.chat-load-more button {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--mute);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 0.8125rem;
  cursor: pointer;
}

.chat-load-more button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* Align with workspace conversation stream */
.history-chat-stream .msg {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: none;
  margin: 0 0 20px;
}

.history-chat-stream .msg.user {
  align-items: center;
}

.history-chat-stream .msg-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.8rem;
  font-weight: 700;
  user-select: none;
}

.history-chat-stream .msg.user .msg-avatar {
  color: #525252;
  background: color-mix(in srgb, var(--chip) 85%, var(--line));
  border: 1px solid var(--line);
}

.history-chat-stream .msg.agent .msg-avatar {
  margin-top: 2px;
  color: #fff;
  background: #0f766e;
}

.history-chat-stream .msg-avatar-icon {
  width: 16px;
  height: 16px;
  display: block;
}

.history-chat-stream .msg-avatar-letter {
  line-height: 1;
}

.history-chat-stream .body {
  flex: 1;
  min-width: 0;
  font-size: 0.9375rem;
  color: var(--ink);
}

.history-chat-stream .msg-text {
  display: inline-block;
  max-width: 100%;
  margin: 0;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.history-chat-stream .msg.user .msg-text {
  padding: 0;
  border: none;
  background: transparent;
}

.history-chat-stream .msg-text.agent-text {
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.history-chat-stream .history-agent-text {
  cursor: pointer;
}

.history-chat-stream .history-agent-text:hover {
  border-color: color-mix(in srgb, var(--ink) 35%, var(--line));
}

.history-chat-stream .msg-time {
  display: block;
  margin: 0 0 4px;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
  color: var(--mute);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.history-chat-stream .chat-console {
  position: relative;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--line-2, var(--line));
  border-radius: var(--r-md, 12px);
  background: color-mix(in srgb, var(--chip) 28%, var(--surface));
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.75rem;
  line-height: 1.6;
  color: var(--ink);
}

.history-chat-stream .chat-event {
  display: grid;
  grid-template-columns: 58px 44px minmax(0, 1fr);
  gap: 0 12px;
  align-items: start;
  padding: 4px 6px;
  margin: 0 -6px;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  width: 100%;
  text-align: left;
  font: inherit;
  color: inherit;
}

.history-chat-stream .chat-event-status.is-preview:hover {
  background: color-mix(in srgb, var(--surface) 55%, transparent);
}

.history-chat-stream .chat-event-time {
  color: var(--mute);
  font-variant-numeric: tabular-nums;
}

.history-chat-stream .chat-event-tag.tag-status {
  color: #16a34a;
  font-weight: 700;
}

.history-chat-stream .chat-event-main {
  min-width: 0;
}

.history-chat-stream .chat-event-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.history-chat-stream .chat-result-text {
  font-weight: 500;
  line-height: 1.55;
  min-width: 0;
  color: var(--ink);
  font-family: inherit;
  font-size: 0.875rem;
}

.history-chat-stream .chat-stream-toggle {
  flex-shrink: 0;
  color: var(--mute);
  font-size: 0.75rem;
}
</style>
