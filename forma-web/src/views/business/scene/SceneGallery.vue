<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import SceneCard from '@/components/business/scene/SceneCard.vue'
import SceneIcon from '@/components/business/scene/SceneIcon.vue'
import { getScenes } from '@/api/business/scene/scene'
import { getSceneWorkspaceSpec } from '@/views/business/scene/workspace/registry'
import { ApiError } from '@/api/client'
import { clearToken } from '@/api/http'
import type { Scene } from '@/types/business/scene'

const TOAST_MS = 4500

const router = useRouter()
const scenes = ref<Scene[]>([])
const loading = ref(true)
const error = ref('')
const needsLogin = ref(false)
const toastText = ref('')
const toastVisible = ref(false)
let toastTimer: ReturnType<typeof setTimeout> | undefined

const sortedScenes = computed(() =>
  [...scenes.value].sort((a, b) => a.sortOrder - b.sortOrder),
)

function workspaceTarget(scene: Scene) {
  if (scene.status !== 'AVAILABLE') {
    return undefined
  }
  if (!getSceneWorkspaceSpec(scene.sceneCode)) return undefined
  return { name: 'scene-workspace', params: { sceneCode: scene.sceneCode } }
}

function clearToastTimer() {
  if (toastTimer !== undefined) {
    clearTimeout(toastTimer)
    toastTimer = undefined
  }
}

function scheduleToastHide() {
  clearToastTimer()
  toastTimer = setTimeout(() => {
    toastVisible.value = false
    toastTimer = undefined
  }, TOAST_MS)
}

async function showComingSoonToast(scene: Scene) {
  const next = `「${scene.displayName}」马上就来。你也可以先从电商开店开始。`
  clearToastTimer()
  // Toggle off first so aria-live re-announces on successive gray-card clicks
  toastVisible.value = false
  toastText.value = next
  await nextTick()
  toastVisible.value = true
  scheduleToastHide()
}

onMounted(async () => {
  try {
    const data = await getScenes()
    scenes.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
      // error.value = '未登录或登录已失效，请先登录后再选场景'
      scenes.value = []
      return
    }
    error.value = e instanceof ApiError ? e.message : '场景列表暂时加载不了，请稍后再试'
    scenes.value = []
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  clearToastTimer()
})

function goLogin() {
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <AppHeader active-nav="scenes" />
    <main class="page gallery-wrap">
      <template v-if="needsLogin">
        <section class="gate" aria-label="登录引导">
          <h1 class="gate-title">Forma</h1>
          <p class="gate-lede">和 Forma 一起开始创作</p>
          <div class="gate-actions">
            <button type="button" class="btn-ink" @click="goLogin">登录</button>
            <RouterLink class="btn-ghost" :to="{ name: 'register' }">注册</RouterLink>
          </div>
        </section>
      </template>

      <template v-else>
        <div class="gallery-head">
          <h1>今天想做什么？</h1>
          <p>选一个场景，开始创作</p>
        </div>

        <p v-if="loading" class="status">加载中…</p>

        <template v-else-if="error">
          <p class="status error" role="alert">{{ error }}</p>
        </template>

        <div v-else-if="sortedScenes.length" class="scene-grid" role="list">
          <SceneCard
            v-for="scene in sortedScenes"
            :key="scene.bizId"
            :scene="scene"
            :to="workspaceTarget(scene)"
            @coming-soon="showComingSoonToast"
          >
            <template #icon>
              <SceneIcon :scene-code="scene.sceneCode" />
            </template>
          </SceneCard>
        </div>

        <p v-else class="status">暂时没有可展示的场景。</p>
      </template>
    </main>

    <div
      class="toast"
      :class="{ show: toastVisible }"
      role="status"
      aria-live="polite"
      :aria-hidden="toastVisible ? 'false' : 'true'"
    >
      {{ toastText }}
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

.gallery-wrap {
  max-width: 960px;
  margin: 0 auto;
  padding: 48px 24px 80px;
}

.gate {
  min-height: calc(100vh - var(--header-h) - 96px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px 12px;
  animation: gate-in 0.35s ease-out both;
}

@keyframes gate-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .gate {
    animation: none;
  }
}

.gate-title {
  margin: 0 0 10px;
  font-family: var(--font-display);
  font-size: clamp(1.55rem, 2.8vw, 2rem);
  font-weight: 650;
  letter-spacing: -0.03em;
}

.gate-lede {
  margin: 0 0 28px;
  max-width: 26rem;
  color: var(--mute);
  font-size: 0.95rem;
  line-height: 1.55;
}

.gate-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.btn-ink,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 7.5rem;
  padding: 10px 18px;
  border-radius: var(--r-sm);
  font-size: 0.9rem;
  font-weight: 550;
  cursor: pointer;
  text-decoration: none;
}

.btn-ink {
  border: 1px solid var(--ink);
  background: var(--ink);
  color: #fff;
}

.btn-ink:hover {
  background: color-mix(in srgb, var(--ink) 82%, #fff);
}

.btn-ghost {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
}

.btn-ghost:hover {
  background: var(--line-2);
}

.gallery-head {
  margin-bottom: 32px;
}

.gallery-head h1 {
  margin: 0 0 8px;
  font-size: clamp(1.65rem, 2.6vw, 2rem);
  font-weight: 600;
  letter-spacing: -0.03em;
}

.gallery-head p {
  margin: 0;
  color: var(--mute);
  font-size: 0.95rem;
  max-width: 36em;
}

.scene-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 700px) {
  .scene-grid {
    grid-template-columns: 1fr;
  }
}

.status {
  margin: 0;
  color: var(--mute);
  font-size: 0.95rem;
}

.status.error {
  color: var(--ink);
}

.toast {
  position: fixed;
  left: 24px;
  bottom: 24px;
  transform: translateY(120%);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  box-shadow: var(--shadow);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: min(420px, calc(100% - 48px));
  font-size: 0.875rem;
  z-index: 60;
  transition: transform 0.22s ease;
  pointer-events: none;
}

.toast.show {
  transform: translateY(0);
}
</style>
