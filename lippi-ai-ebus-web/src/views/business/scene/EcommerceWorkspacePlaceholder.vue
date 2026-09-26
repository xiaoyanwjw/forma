<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import { getScenes } from '@/api/business/scene/scene'

/** Stable scene binding for this workbench — Epic 3 session create must carry it */
const SCENE_CODE = 'ecommerce' as const
const SCENE_BREADCRUMB = '电商开店'
const GEN_SOON = '生成能力即将开放'

const PICKS_TEMPLATE = '请帮我生成【品类】类选品清单，客单价【最低价】–【最高价】元。'
const LISTING_TEMPLATE =
  '请为商品「【商品名称】」生成上架素材，优先适配【淘宝/拼多多/闲鱼】。'

const prompt = ref('')
/** Optional Catalog bizId when list is available; null if unresolved */
const sceneBizId = ref<string | null>(null)

function fillPicks() {
  prompt.value = PICKS_TEMPLATE
}

function fillListing() {
  prompt.value = LISTING_TEMPLATE
}

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
    // Empty UI still works with sceneCode alone; Epic 3 will harden lookup
  }
})
</script>

<template>
  <div
    class="shell"
    :data-scene-code="SCENE_CODE"
    :data-scene-biz-id="sceneBizId ?? undefined"
  >
    <AppHeader :scene-breadcrumb="SCENE_BREADCRUMB" />
    <div class="home">
      <main class="home-main">
        <h1>我能为你做什么？</h1>
        <div class="quick-row" role="group" aria-label="快捷任务">
          <button type="button" class="pill" @click="fillPicks">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path d="M9 11l3 3L22 4" />
              <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
            </svg>
            选品清单
          </button>
          <button type="button" class="pill" @click="fillListing">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M3 9h18M9 21V9" />
            </svg>
            生成上架素材
          </button>
        </div>
        <div class="prompt-box">
          <textarea
            v-model="prompt"
            class="prompt-editor"
            rows="3"
            placeholder="分配一个任务或提问任何问题"
            aria-label="提问"
          />
          <div class="prompt-toolbar">
            <button
              type="button"
              class="icon-btn"
              aria-label="附件"
              aria-describedby="gen-soon-hint"
              disabled
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </button>
            <button
              type="button"
              class="send-btn"
              aria-label="发送"
              aria-describedby="gen-soon-hint"
              disabled
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" />
              </svg>
            </button>
          </div>
          <p id="gen-soon-hint" class="sr-only">{{ GEN_SOON }}</p>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.home {
  min-height: calc(100vh - var(--header-h));
  display: flex;
  flex-direction: column;
}

.home-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px 80px;
  width: min(720px, 100%);
  margin: 0 auto;
}

.home-main h1 {
  margin: 0 0 28px;
  font-size: clamp(1.75rem, 3.5vw, 2.35rem);
  font-weight: 600;
  letter-spacing: -0.02em;
  text-align: center;
  color: var(--ink);
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin: 4px 0 14px;
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
  transition: background 0.15s, border-color 0.15s;
}

.pill:hover {
  background: var(--line-2);
}

.pill svg {
  opacity: 0.7;
  flex-shrink: 0;
}

.prompt-box {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
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
  cursor: not-allowed;
}

.icon-btn:disabled,
.send-btn:disabled {
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
