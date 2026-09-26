<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import { getCredits } from '@/api/business/credit/credit'
import { ApiError } from '@/api/client'
import { clearToken, getToken } from '@/api/http'
import {
  CREDIT_PLAN_ROWS,
  INSUFFICIENT_CREDITS_HINT,
  creditTierLabel,
  formatNextResetAtShanghai,
  type CreditBalance,
} from '@/types/business/credit'

const router = useRouter()
const balance = ref<CreditBalance | null>(null)
const error = ref('')
const needsLogin = ref(false)
const loading = ref(true)

const showInsufficient = computed(
  () => balance.value != null && balance.value.available === 0,
)

const resetLabel = computed(() => {
  if (!balance.value?.nextResetAt) {
    return ''
  }
  return formatNextResetAtShanghai(balance.value.nextResetAt)
})

onMounted(async () => {
  if (!getToken()) {
    needsLogin.value = true
    error.value = '未登录，请先登录后查看套餐与积分'
    loading.value = false
    return
  }
  try {
    const data = await getCredits()
    if (data == null) {
      error.value = '无法获取积分信息'
      return
    }
    balance.value = data
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法获取积分信息'
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
    }
  } finally {
    loading.value = false
  }
})

function goLogin() {
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <AppHeader active-nav="credits" />
    <main class="page">
      <div class="inner">
        <h1>套餐与积分</h1>

        <p v-if="loading">加载中…</p>

        <template v-else-if="needsLogin">
          <p class="error">{{ error }}</p>
          <p class="nav">
            <button type="button" class="btn-primary" @click="goLogin">去登录</button>
            <router-link :to="{ name: 'register' }">注册</router-link>
          </p>
        </template>

        <template v-else-if="error">
          <p class="error">{{ error }}</p>
          <p class="nav">
            <router-link :to="{ name: 'me' }">账户</router-link>
            <router-link :to="{ name: 'login' }">登录</router-link>
          </p>
        </template>

        <template v-else-if="balance">
          <section class="summary" aria-label="当前积分摘要">
            <dl class="summary-grid">
              <dt>当前套餐</dt>
              <dd class="tier">{{ creditTierLabel(balance.tier) }}</dd>
              <dt>本月剩余</dt>
              <dd class="available">{{ balance.available }}</dd>
              <dt>下次重置</dt>
              <dd>{{ resetLabel }}</dd>
            </dl>
            <p v-if="balance.reserved > 0" class="reserved">
              预占中 {{ balance.reserved }}（剩余以可用积分为准）
            </p>
            <p v-if="showInsufficient" class="insufficient" role="status">
              {{ INSUFFICIENT_CREDITS_HINT }}
            </p>
          </section>

          <section class="pricing" aria-label="套餐价目">
            <h2>套餐</h2>
            <p class="hint">三档只差积分。月费按模型成本后公布。</p>
            <ul class="cards">
              <li
                v-for="row in CREDIT_PLAN_ROWS"
                :key="row.tier"
                class="plan-card"
                :class="{ current: balance.tier === row.tier }"
              >
                <p class="card-tier">{{ row.label }}</p>
                <p class="card-price">
                  <span class="price-value">{{ row.monthlyPriceLabel }}</span>
                  <span class="price-unit">/ 月</span>
                </p>
                <p class="card-sub">每月 {{ row.monthlyQuota }} 积分</p>
                <p class="card-status" :class="{ on: balance.tier === row.tier }">
                  {{ balance.tier === row.tier ? '当前套餐' : '—' }}
                </p>
                <ul class="card-points">
                  <li>{{ row.monthlyQuota }} 积分 / 月</li>
                  <li>全部已上线模板可用</li>
                  <li>按订阅锚点月重置</li>
                </ul>
              </li>
            </ul>
          </section>

          <p class="nav">
            <router-link :to="{ name: 'me' }">账户</router-link>
          </p>
        </template>
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
  width: 100%;
  box-sizing: border-box;
  font-family: var(--font);
  color: var(--ink);
}
.inner {
  max-width: 58rem;
  margin: 0 auto;
  padding: 3rem 1rem 4rem;
}
h1,
h2,
.tier,
.available,
.price-value,
.card-tier {
  font-family: var(--font);
  letter-spacing: -0.02em;
}
h1 {
  font-size: 1.75rem;
  font-weight: 600;
  margin: 0 0 1.25rem;
}
h2 {
  font-size: 1.15rem;
  font-weight: 600;
  margin: 0 0 0.35rem;
}
.summary-grid {
  display: grid;
  grid-template-columns: 6.5rem 1fr;
  gap: 0.55rem 1rem;
  margin: 0;
}
.summary-grid dt {
  color: var(--mute);
}
.summary-grid dd {
  margin: 0;
}
.available {
  font-size: 1.35rem;
  font-weight: 600;
}
.reserved {
  margin: 0.85rem 0 0;
  color: var(--mute);
  font-size: 0.9rem;
}
.insufficient {
  margin: 1rem 0 0;
  padding: 0.75rem 0.85rem;
  border-left: 3px solid #e11d48;
  background: var(--surface);
  color: var(--ink);
}
.pricing {
  margin-top: 2.25rem;
}
.hint {
  color: var(--mute);
  margin: 0 0 1.1rem;
  font-size: 0.95rem;
}
.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}
@media (min-width: 720px) {
  .cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 0.85rem;
  }
}
.plan-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  padding: 1.35rem 1.2rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  box-sizing: border-box;
}
.plan-card.current {
  border-color: var(--ink);
  box-shadow: 0 0 0 1px var(--ink);
}
.card-tier {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}
.card-price {
  margin: 0.15rem 0 0;
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
}
.price-value {
  font-size: 2rem;
  font-weight: 600;
  line-height: 1.1;
}
.price-unit {
  color: var(--mute-2);
  font-size: 0.9rem;
}
.card-sub {
  margin: 0;
  color: var(--mute);
  font-size: 0.9rem;
}
.card-status {
  margin: 0.35rem 0 0.15rem;
  width: 100%;
  box-sizing: border-box;
  text-align: center;
  padding: 0.55rem 0.75rem;
  border-radius: var(--r-sm);
  font-size: 0.9rem;
  font-weight: 500;
  background: var(--line-2);
  color: var(--mute-2);
}
.card-status.on {
  background: var(--accent);
  color: #fff;
}
.card-points {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.card-points li {
  position: relative;
  padding-left: 1.1rem;
  color: var(--ink);
  font-size: 0.88rem;
  line-height: 1.35;
}
.card-points li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.45em;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: var(--mute-2);
}
.plan-card.current .card-points li::before {
  background: var(--ink);
}
.error {
  color: #e11d48;
}
.nav {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1.75rem;
}
.btn-primary {
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: var(--r-sm);
  padding: 0.55rem 0.95rem;
  cursor: pointer;
  font-family: inherit;
}
a {
  color: var(--ink);
}
</style>
