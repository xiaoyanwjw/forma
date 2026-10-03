<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getToken } from '@/api/http'
import { AI_DISCLAIMER_SHORT } from '@/constants/compliance'

const router = useRouter()
/** 入场时读一次 JWT；有 token 则点进场景画廊，不强制 redirect */
const loggedIn = Boolean(getToken())

const QUICK_PILLS = [
  { id: 'picks', label: '选品清单' },
  { id: 'listing', label: '生成上架素材' },
  { id: 'xhs', label: '种草选题' },
] as const

function goPrimary() {
  void router.push({ name: loggedIn ? 'scenes' : 'login' })
}

function goLogin() {
  void router.push({ name: 'login' })
}

function goRegister() {
  void router.push({ name: 'register' })
}
</script>

<template>
  <main class="landing">
    <header class="top" aria-label="站点">
      <span class="logo">
        <span class="logo-mark" aria-hidden="true">◇</span>
        Forma
      </span>
      <div class="top-actions">
        <template v-if="loggedIn">
          <button type="button" class="btn-ink" @click="goPrimary">进入场景</button>
        </template>
        <template v-else>
          <button type="button" class="btn-ink" @click="goLogin">登录</button>
          <button type="button" class="btn-ghost" @click="goRegister">注册</button>
        </template>
      </div>
    </header>

    <div class="stage">
      <h1 class="headline">我能为你做什么？</h1>
      <p class="lede">和 Forma 一起开始创作</p>

      <!-- 只读外观：点击整块进登录/场景，禁止真编辑发任务 -->
      <button type="button" class="prompt" aria-label="开始任务" @click="goPrimary">
        <span class="prompt-placeholder">分配一个任务或提问任何问题</span>
        <span class="prompt-bar">
          <span class="prompt-plus" aria-hidden="true">+</span>
          <span class="prompt-send" aria-hidden="true">↑</span>
        </span>
      </button>

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
    </div>

    <footer class="foot" data-testid="ai-disclaimer-footer">
      <p>{{ AI_DISCLAIMER_SHORT }}</p>
    </footer>
  </main>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background-color: var(--canvas);
  background-image: radial-gradient(var(--dot) 1px, transparent 1px);
  background-size: 20px 20px;
  font-family: var(--font);
  color: var(--ink);
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.5rem;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--ink);
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-display);
  font-weight: 650;
  font-size: 1.12rem;
  letter-spacing: -0.03em;
}

.logo-mark {
  width: 1.35rem;
  height: 1.35rem;
  border-radius: var(--r-sm);
  background: var(--ink);
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

.btn-ink,
.btn-ghost {
  font: inherit;
  font-size: 0.875rem;
  font-weight: 550;
  padding: 0.45rem 0.95rem;
  border-radius: var(--r-sm);
  cursor: pointer;
}

.btn-ink {
  border: 1px solid var(--ink);
  background: var(--ink);
  color: #fff;
}

.btn-ink:hover {
  background: color-mix(in srgb, var(--ink) 82%, #fff);
  border-color: color-mix(in srgb, var(--ink) 82%, #fff);
}

.btn-ghost {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
}

.btn-ghost:hover {
  background: var(--line-2);
}

.btn-ink:focus-visible,
.btn-ghost:focus-visible,
.pill:focus-visible,
.prompt:focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: 2px;
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
  margin: 0 0 0.65rem;
  font-family: var(--font-display);
  font-weight: 650;
  font-size: clamp(1.85rem, 4vw, 2.55rem);
  letter-spacing: -0.03em;
  text-align: center;
  line-height: 1.2;
  color: var(--ink);
}

.lede {
  margin: 0 0 1.75rem;
  max-width: 28rem;
  text-align: center;
  font-size: 0.95rem;
  line-height: 1.55;
  color: var(--mute);
}

.prompt {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.9rem 0.9rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: 1.35rem;
  background: var(--surface);
  box-shadow: var(--shadow);
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}

.prompt:hover {
  border-color: color-mix(in srgb, var(--ink) 22%, var(--line));
}

.prompt-placeholder {
  min-height: 3.5rem;
  font-size: 0.95rem;
  line-height: 1.7;
  color: var(--mute-2);
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
  color: var(--mute);
  background: var(--chip);
}

.prompt-send {
  background: var(--ink);
  color: #fff;
}

.quick {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1rem;
}

.pill {
  font: inherit;
  font-size: 0.8125rem;
  padding: 0.5rem 0.9rem;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}

.pill:hover {
  background: var(--chip);
}

.foot {
  padding: 0.75rem 1.25rem 1.25rem;
  text-align: center;
}

.foot p {
  margin: 0 auto;
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--mute-2);
  max-width: 36rem;
}
</style>
