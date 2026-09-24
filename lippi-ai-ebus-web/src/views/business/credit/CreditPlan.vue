<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
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
          <h2>价目</h2>
          <p class="hint">三档只差积分。月费按模型成本后公布。</p>
          <ul class="rows">
            <li
              v-for="row in CREDIT_PLAN_ROWS"
              :key="row.tier"
              class="row"
              :class="{ current: balance.tier === row.tier }"
            >
              <strong>{{ row.label }}</strong>
              <span>{{ row.monthlyQuota }} 积分/月 · {{ row.monthlyPriceLabel }}</span>
            </li>
          </ul>
        </section>

        <p class="nav">
          <router-link :to="{ name: 'me' }">账户</router-link>
        </p>
      </template>
    </div>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: #f0f2f5;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  color: #121212;
}
.inner {
  max-width: 36rem;
  margin: 0 auto;
  padding: 3rem 1rem;
}
h1,
h2,
.tier,
.available {
  font-family: 'Space Grotesk', 'Noto Sans SC', system-ui, sans-serif;
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
  color: #666;
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
  color: #555;
  font-size: 0.9rem;
}
.insufficient {
  margin: 1rem 0 0;
  padding: 0.75rem 0.85rem;
  border-left: 3px solid #e11d48;
  background: #fff;
  color: #121212;
}
.pricing {
  margin-top: 2rem;
}
.hint {
  color: #555;
  margin: 0 0 0.75rem;
  font-size: 0.95rem;
}
.rows {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 0;
  border-bottom: 1px solid #c5c9d0;
}
.row.current {
  border-bottom-color: #e11d48;
}
.row.current strong::after {
  content: ' · 当前';
  color: #e11d48;
  font-weight: 500;
  font-size: 0.85rem;
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
  background: #e11d48;
  color: #fff;
  border: none;
  border-radius: 3px;
  padding: 0.55rem 0.95rem;
  cursor: pointer;
  font-family: inherit;
}
a {
  color: #121212;
}
</style>
