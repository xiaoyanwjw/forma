<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getToken } from '@/api/http'

const router = useRouter()
/** 入场时读一次 JWT；有 token 则 CTA 进 /credits，不强制 redirect */
const loggedIn = Boolean(getToken())

/** 静态清单样例：仅展示交付物感，不调生成 API */
const SAMPLE_PICKS = [
  { name: '硅胶沥水垫（多色）', reason: '厨房刚需、复购点清晰，图文易做差异化。' },
  { name: '壁挂式免打孔置物架', reason: '租房人群搜索稳，包装轻、退货风险相对可控。' },
  { name: '可折叠脏衣篮', reason: '体积小好发货，场景图好拍，客单价友好。' },
  { name: '桌面收纳盒套装', reason: '办公居家双场景，套装组合提升客单。' },
  { name: '防滑浴缸垫', reason: '季节性需求稳，规格清晰便于 Listing。' },
  { name: '可水洗宠物窝垫', reason: '宠物类复购高，材质卖点好写。' },
  { name: '磁吸电缆收纳夹', reason: '小件易上量，主图差异化空间大。' },
] as const

function goLogin() {
  void router.push({ name: 'login' })
}

function goRegister() {
  void router.push({ name: 'register' })
}

function goCredits() {
  void router.push({ name: 'credits' })
}

function padIndex(i: number): string {
  return String(i + 1).padStart(2, '0')
}
</script>

<template>
  <main class="landing">
    <div class="stage">
      <section class="hero" aria-label="品牌">
        <h1 class="brand">Adam</h1>
        <p class="tagline">自助做出一份能用的选品清单，再继续 Listing。</p>
        <div class="cta" aria-label="行动入口">
          <template v-if="loggedIn">
            <button type="button" class="btn-primary" @click="goCredits">进入套餐与积分</button>
          </template>
          <template v-else>
            <button type="button" class="btn-primary" @click="goLogin">登录</button>
            <button type="button" class="btn-secondary" @click="goRegister">注册</button>
          </template>
        </div>
      </section>

      <aside class="sheet" aria-label="选品清单样例">
        <header class="sheet-head">
          <span class="sheet-title">选品清单 · 家居类</span>
          <span class="sheet-status">样例</span>
        </header>
        <ol class="picks">
          <li v-for="(pick, i) in SAMPLE_PICKS" :key="pick.name">
            <span class="n">{{ padIndex(i) }}</span>
            <div>
              <div class="t">{{ pick.name }}</div>
              <div class="r">{{ pick.reason }}</div>
            </div>
          </li>
        </ol>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background:
    radial-gradient(ellipse 80% 50% at 10% 0%, rgba(225, 29, 72, 0.06), transparent 55%),
    linear-gradient(165deg, #e8ebf0 0%, #f0f2f5 42%, #e4e8ee 100%);
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  color: #121212;
  padding: 2.5rem 1.25rem 3rem;
}

.stage {
  max-width: 64rem;
  margin: 0 auto;
  display: grid;
  gap: 2.25rem;
  align-items: start;
  animation: land-in 0.45s ease-out both;
}

@media (min-width: 860px) {
  .stage {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr);
    gap: 2.75rem;
    align-items: center;
    min-height: calc(100vh - 5.5rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stage {
    animation: none;
  }
}

@keyframes land-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.brand {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
  font-size: clamp(3.25rem, 9vw, 5.5rem);
  font-weight: 600;
  letter-spacing: -0.045em;
  line-height: 0.95;
  color: #121212;
}

.tagline {
  margin: 0;
  max-width: 22rem;
  font-size: 1.05rem;
  line-height: 1.55;
  color: #333;
}

.cta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-top: 0.35rem;
}

.btn-primary,
.btn-secondary {
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 500;
  padding: 0.6rem 1.15rem;
  border-radius: 3px;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: #e11d48;
  color: #fff;
}

.btn-secondary {
  background: #121212;
  color: #fff;
}

.btn-primary:hover,
.btn-secondary:hover {
  filter: brightness(1.06);
}

.btn-primary:focus-visible,
.btn-secondary:focus-visible {
  outline: 2px solid #e11d48;
  outline-offset: 2px;
}

.sheet {
  background: #fff;
  border: 1px solid #d5d9e0;
  border-radius: 4px;
  box-shadow:
    0 1px 0 rgba(18, 18, 18, 0.04),
    0 18px 40px -28px rgba(18, 18, 18, 0.35);
  overflow: hidden;
}

.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
  padding: 0.85rem 1.1rem;
  border-bottom: 1px solid #e6e9ef;
  background: #fafbfc;
}

.sheet-title {
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 600;
  font-size: 0.95rem;
  letter-spacing: -0.02em;
}

.sheet-status {
  font-size: 0.8rem;
  color: #666;
}

.picks {
  list-style: none;
  margin: 0;
  padding: 0.35rem 0;
}

.picks li {
  display: grid;
  grid-template-columns: 2.4rem 1fr;
  gap: 0.65rem;
  align-items: start;
  padding: 0.75rem 1.1rem;
  border-bottom: 1px solid #eef0f4;
}

.picks li:last-child {
  border-bottom: none;
}

.n {
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 600;
  font-size: 0.9rem;
  letter-spacing: -0.02em;
  color: #e11d48;
  line-height: 1.4;
}

.t {
  font-weight: 500;
  font-size: 0.95rem;
  line-height: 1.35;
}

.r {
  margin-top: 0.2rem;
  font-size: 0.85rem;
  line-height: 1.45;
  color: #555;
}
</style>
