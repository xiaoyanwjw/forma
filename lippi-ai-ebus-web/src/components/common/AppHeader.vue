<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getCredits } from '@/api/business/credit/credit'
import { getToken, onAuthChange } from '@/api/http'
import { creditTierLabel, type CreditBalance } from '@/types/business/credit'

const props = withDefaults(
  defineProps<{
    /** When set, left nav shows breadcrumb「场景 / label」instead of plain 场景 link */
    sceneBreadcrumb?: string
    /** Highlight current nav item */
    activeNav?: 'scenes' | 'history' | 'credits'
  }>(),
  {
    sceneBreadcrumb: undefined,
    activeNav: undefined,
  },
)

const loggedIn = ref(Boolean(getToken()))
const balance = ref<CreditBalance | null>(null)
const creditHint = ref('')

const breadcrumbLabel = computed(() => props.sceneBreadcrumb?.trim() ?? '')
const showBreadcrumb = computed(() => breadcrumbLabel.value.length > 0)

const creditChipText = computed(() => {
  if (creditHint.value) {
    return creditHint.value
  }
  if (!balance.value) {
    return ''
  }
  const tier = creditTierLabel(balance.value.tier)
  return `${tier} · ${balance.value.available}/${balance.value.balance}`
})

async function loadCredits() {
  creditHint.value = ''
  balance.value = null
  try {
    const data = await getCredits()
    if (data == null) {
      creditHint.value = '积分暂不可用'
      return
    }
    balance.value = data
  } catch {
    creditHint.value = '积分暂不可用'
  }
}

const stopAuth = onAuthChange(() => {
  const next = Boolean(getToken())
  loggedIn.value = next
  if (!next) {
    balance.value = null
    creditHint.value = ''
  }
})

onMounted(async () => {
  if (loggedIn.value) {
    await loadCredits()
  }
})

onUnmounted(stopAuth)
</script>

<template>
  <header class="app-header" role="banner">
    <div class="app-header-left">
      <RouterLink class="logo" :to="{ name: 'landing' }" aria-label="Adam 首页">
        <span class="logo-mark" aria-hidden="true">A</span>
        Adam
      </RouterLink>
      <nav class="header-nav" aria-label="主导航">
        <span
          v-if="showBreadcrumb"
          class="scene-switch"
          aria-label="面包屑"
        >
          <RouterLink :to="{ name: 'landing' }" class="nav-link crumb-link">场景</RouterLink>
          <span class="bc-sep" aria-hidden="true">/</span>
          <strong class="bc-current">{{ breadcrumbLabel }}</strong>
        </span>
        <RouterLink
          v-else
          class="nav-link"
          :class="{ on: props.activeNav === 'scenes' }"
          :aria-current="props.activeNav === 'scenes' ? 'page' : undefined"
          :to="{ name: 'landing' }"
        >
          场景
        </RouterLink>

        <span
          class="nav-link nav-link--static"
          :class="{ on: props.activeNav === 'history' }"
          :aria-current="props.activeNav === 'history' ? 'page' : undefined"
          data-nav="history"
          aria-disabled="true"
        >
          历史
        </span>

        <RouterLink
          class="nav-link"
          :class="{ on: props.activeNav === 'credits' }"
          :aria-current="props.activeNav === 'credits' ? 'page' : undefined"
          :to="{ name: 'credits' }"
        >
          套餐
        </RouterLink>
      </nav>
    </div>

    <div class="app-header-right">
      <template v-if="loggedIn">
        <span v-if="creditChipText" class="credits-chip">{{ creditChipText }}</span>
        <RouterLink class="btn btn-ghost" :to="{ name: 'credits' }">升级</RouterLink>
        <RouterLink
          class="avatar-btn"
          :to="{ name: 'me' }"
          aria-label="账户"
          title="账户"
        >
          A
        </RouterLink>
      </template>
      <template v-else>
        <RouterLink class="nav-link" :to="{ name: 'login' }">登录</RouterLink>
        <RouterLink class="btn btn-ghost" :to="{ name: 'register' }">注册</RouterLink>
      </template>
    </div>
  </header>
</template>

<style scoped>
/* Full-bleed app header — never max-width centered capsule */
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  width: 100%;
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
}

.app-header-left,
.app-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.app-header-left {
  flex: 1;
}

.app-header-right {
  flex-shrink: 0;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 1.02rem;
  letter-spacing: -0.03em;
  flex-shrink: 0;
}

.logo-mark {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  padding: 6px 10px;
  border-radius: var(--r-sm);
  font-size: 0.875rem;
  color: var(--mute);
}

.nav-link:hover,
.nav-link.on {
  color: var(--ink);
  background: var(--line-2);
}

.nav-link--static {
  cursor: default;
}

.scene-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  color: var(--mute);
  min-width: 0;
  padding: 0 4px;
}

.crumb-link {
  padding: 6px 4px;
}

.bc-sep {
  color: var(--mute-2);
}

.bc-current {
  color: var(--ink);
  font-weight: 600;
}

.credits-chip {
  font-size: 0.8125rem;
  color: var(--mute);
  white-space: nowrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  font-size: 0.875rem;
  font-weight: 550;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.btn-ghost {
  border-color: var(--line);
  background: var(--surface);
  color: var(--ink);
}

.btn-ghost:hover {
  background: var(--line-2);
}

.avatar-btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  display: inline-grid;
  place-items: center;
  font-size: 0.75rem;
  font-weight: 600;
  flex-shrink: 0;
}

.avatar-btn:hover {
  background: var(--accent-hover);
}
</style>
