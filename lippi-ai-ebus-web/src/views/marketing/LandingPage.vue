<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getToken } from '@/api/http'

const router = useRouter()
/** 入场时读一次 JWT；有 token 则点进 /credits，不强制 redirect */
const loggedIn = Boolean(getToken())

const QUICK_PILLS = [
  { id: 'picks', label: '选品清单' },
  { id: 'listing', label: '生成上架素材' },
] as const

function goPrimary() {
  void router.push({ name: loggedIn ? 'credits' : 'login' })
}

function goRegister() {
  void router.push({ name: 'register' })
}
</script>

<template>
  <main class="landing">
    <header class="top" aria-label="站点">
      <span class="logo">
        <span class="logo-mark" aria-hidden="true">A</span>
        adam
      </span>
      <div class="top-actions">
        <template v-if="loggedIn">
          <button type="button" class="linkish" @click="goPrimary">套餐与积分</button>
        </template>
        <template v-else>
          <button type="button" class="linkish" @click="goRegister">注册</button>
          <button type="button" class="btn-enter" @click="goPrimary">登录</button>
        </template>
      </div>
    </header>

    <div class="stage">
      <h1 class="headline">我能为你做什么？</h1>

      <div class="quick" aria-label="快捷入口">
        <button
          v-for="pill in QUICK_PILLS"
          :key="pill.id"
          type="button"
          class="pill"
          @click="goPrimary"
        >
          {{ pill.label }}
        </button>
      </div>

      <!-- 只读外观：点击整块进登录/套餐，禁止真编辑发任务 -->
      <button type="button" class="prompt" aria-label="开始任务" @click="goPrimary">
        <span class="prompt-placeholder">分配一个任务或提问任何问题</span>
        <span class="prompt-bar">
          <span class="prompt-plus" aria-hidden="true">+</span>
          <span class="prompt-send" aria-hidden="true">↑</span>
        </span>
      </button>
    </div>
  </main>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(#d8dde6 1px, transparent 1px),
    linear-gradient(180deg, #f0f2f5 0%, #e8ebf0 100%);
  background-size:
    20px 20px,
    auto;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  color: #121212;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.5rem;
  background: rgba(240, 242, 245, 0.82);
  backdrop-filter: blur(8px);
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 600;
  font-size: 1.05rem;
  letter-spacing: -0.03em;
  text-transform: lowercase;
}

.logo-mark {
  width: 1.35rem;
  height: 1.35rem;
  border-radius: 0.35rem;
  background: #121212;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  font-weight: 700;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.linkish {
  font: inherit;
  font-size: 0.875rem;
  color: #667085;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0.4rem 0.65rem;
  border-radius: 3px;
}

.linkish:hover {
  color: #121212;
}

.linkish:focus-visible,
.btn-enter:focus-visible,
.pill:focus-visible,
.prompt:focus-visible {
  outline: 2px solid #e11d48;
  outline-offset: 2px;
}

.btn-enter {
  font: inherit;
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.45rem 0.9rem;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  background: #e11d48;
  color: #fff;
}

.btn-enter:hover {
  filter: brightness(1.06);
}

.stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: min(42rem, 100%);
  margin: 0 auto;
  padding: 2.5rem 1.25rem 4.5rem;
  box-sizing: border-box;
  animation: land-in 0.4s ease-out both;
}

@media (prefers-reduced-motion: reduce) {
  .stage {
    animation: none;
  }
}

@keyframes land-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.headline {
  margin: 0 0 1.5rem;
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 500;
  font-size: clamp(1.75rem, 3.5vw, 2.35rem);
  letter-spacing: -0.02em;
  text-align: center;
  line-height: 1.25;
  color: #121212;
}

.quick {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
}

.pill {
  font: inherit;
  font-size: 0.8125rem;
  padding: 0.5rem 0.9rem;
  border-radius: 999px;
  border: 1px solid #d5d9e0;
  background: #fff;
  color: #121212;
  cursor: pointer;
}

.pill:hover {
  background: #eef1f5;
}

.prompt {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.9rem 0.9rem 0.75rem;
  border: 1px solid #d5d9e0;
  border-radius: 1.35rem;
  background: #fff;
  box-shadow:
    0 1px 2px rgba(18, 18, 18, 0.04),
    0 8px 24px -12px rgba(18, 18, 18, 0.12);
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}

.prompt:hover {
  border-color: #c5cad3;
}

.prompt-placeholder {
  min-height: 3.5rem;
  font-size: 0.95rem;
  line-height: 1.7;
  color: #98a2b3;
}

.prompt-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.prompt-plus,
.prompt-send {
  width: 2rem;
  height: 2rem;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 0.95rem;
  line-height: 1;
  color: #667085;
  background: #eef1f5;
}

.prompt-send {
  background: #121212;
  color: #fff;
}
</style>
