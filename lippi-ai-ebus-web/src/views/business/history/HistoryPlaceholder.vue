<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import { ApiError } from '@/api/client'
import { getHistoryArtifact, getHistoryArtifacts } from '@/api/business/history/history'
import { getSessionMessages } from '@/api/business/session/session'
import { parseComputerDocument } from '@/types/business/computerView'
import type { HistoryArtifactDetail, HistoryArtifactSummary } from '@/types/business/history'
import type { SessionMessage } from '@/types/business/session'

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
const activeTab = ref<'artifact' | 'chat'>('artifact')
const chatMessages = ref<SessionMessage[]>([])
const chatError = ref('')
const chatLoading = ref(false)
/** 忽略过期详情响应，避免连点串扰 */
let detailRequestSeq = 0
let chatRequestSeq = 0

const computerDoc = computed(() => {
  if (!detail.value?.view) return null
  return parseComputerDocument(detail.value.view)
})

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
  chatMessages.value = []
  chatError.value = ''
  chatLoading.value = false
}

function closeDrawer() {
  detailRequestSeq += 1
  resetChat()
  drawerOpen.value = false
  selectedId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  activeTab.value = 'artifact'
}

async function openItem(item: HistoryArtifactSummary) {
  const seq = ++detailRequestSeq
  resetChat()
  selectedId.value = item.id
  drawerOpen.value = true
  activeTab.value = 'artifact'
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

async function loadChat() {
  const sessionId = detail.value?.sessionId?.trim()
  if (!sessionId) {
    chatMessages.value = []
    chatError.value = ''
    chatLoading.value = false
    return
  }
  const seq = ++chatRequestSeq
  chatLoading.value = true
  chatError.value = ''
  try {
    const data = await getSessionMessages(sessionId)
    if (seq !== chatRequestSeq) {
      return
    }
    chatMessages.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (seq !== chatRequestSeq) {
      return
    }
    chatMessages.value = []
    chatError.value = e instanceof ApiError ? e.message : '会话加载失败'
  } finally {
    if (seq === chatRequestSeq) {
      chatLoading.value = false
    }
  }
}

function selectTab(tab: 'artifact' | 'chat') {
  activeTab.value = tab
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
        <div class="drawer-head">
          <div class="tabs" role="tablist">
            <button
              type="button"
              role="tab"
              data-testid="history-tab-artifact"
              :aria-selected="activeTab === 'artifact'"
              :class="{ on: activeTab === 'artifact' }"
              @click="selectTab('artifact')"
            >
              成果
            </button>
            <button
              type="button"
              role="tab"
              data-testid="history-tab-chat"
              :aria-selected="activeTab === 'chat'"
              :class="{ on: activeTab === 'chat' }"
              @click="selectTab('chat')"
            >
              对话
            </button>
          </div>
          <button type="button" class="drawer-close" @click="closeDrawer">关闭</button>
        </div>

        <section
          v-show="activeTab === 'artifact'"
          class="detail"
          data-testid="history-detail"
          aria-label="成果预览"
        >
          <p v-if="detailLoading" class="hint">正在打开预览…</p>
          <p v-else-if="detailError" class="hint error">{{ detailError }}</p>
          <div v-else-if="computerDoc" class="computer-pane">
            <div class="computer-bar">
              <span>Adam's Computer</span>
              <span class="mute">{{ detail?.title }}</span>
            </div>
            <div class="computer-body">
              <ComputerRenderer :document="computerDoc" />
            </div>
          </div>
          <p v-else class="hint">该成果暂无可用预览。</p>
        </section>

        <section
          v-show="activeTab === 'chat'"
          class="chat-pane"
          data-testid="history-chat"
          aria-label="对话回放"
        >
          <p v-if="!detailLoading && !detail?.sessionId" class="hint" data-testid="history-chat-empty">
            暂无会话记录
          </p>
          <p v-else-if="chatLoading" class="hint">正在加载对话…</p>
          <p v-else-if="chatError" class="hint error">{{ chatError }}</p>
          <div v-else class="chat-list">
            <article
              v-for="(m, i) in chatMessages"
              :key="i"
              class="bubble"
              :class="m.role"
            >
              <span class="bubble-role">{{ m.role === 'user' ? '你' : 'Adam' }}</span>
              <p>{{ m.content }}</p>
            </article>
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
  margin-top: 24px;
}

.computer-pane {
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--surface);
}

.computer-bar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  font-size: 0.8125rem;
  font-weight: 600;
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
  inset: 0;
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
  width: min(560px, 100%);
  background: var(--bg, #fff);
  border-left: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  box-shadow: -12px 0 32px rgba(15, 18, 24, 0.08);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.tabs {
  display: flex;
  gap: 4px;
}

.tabs button {
  border: 0;
  background: transparent;
  color: var(--mute);
  padding: 6px 12px;
  border-radius: var(--r-sm);
  cursor: pointer;
  font-size: 0.9rem;
}

.tabs button.on {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
}

.drawer-close {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: var(--r-sm);
  padding: 4px 10px;
  cursor: pointer;
  font-size: 0.8125rem;
}

.drawer-panel .detail,
.chat-pane {
  flex: 1;
  overflow: auto;
  padding: 16px;
  margin: 0;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bubble {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
}

.bubble.user {
  align-self: flex-end;
}

.bubble-role {
  display: block;
  font-size: 0.75rem;
  color: var(--mute);
  margin-bottom: 4px;
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
}
</style>
