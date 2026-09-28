<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import ComputerRenderer from '@/components/business/computer/ComputerRenderer.vue'
import { ApiError } from '@/api/client'
import { getHistoryArtifact, getHistoryArtifacts } from '@/api/business/history/history'
import { parseComputerDocument } from '@/types/business/computerView'
import type { HistoryArtifactDetail, HistoryArtifactSummary } from '@/types/business/history'

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
/** 忽略过期详情响应，避免连点串扰 */
let detailRequestSeq = 0

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

async function openItem(item: HistoryArtifactSummary) {
  if (selectedId.value === item.id && detail.value) {
    detailRequestSeq += 1
    selectedId.value = null
    detail.value = null
    detailError.value = ''
    detailLoading.value = false
    return
  }
  const seq = ++detailRequestSeq
  selectedId.value = item.id
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

async function onSceneChange() {
  detailRequestSeq += 1
  selectedId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
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

        <section
          v-if="selectedId"
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
      </div>
    </main>
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
  max-height: 70vh;
  overflow: auto;
}
</style>
