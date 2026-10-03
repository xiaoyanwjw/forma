<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import {
  changeAccountPassword,
  getAccountCreditUsage,
  getAccountProfile,
  updateAccountProfile,
} from '@/api/identity/account'
import { ApiError } from '@/api/client'
import { clearToken, getToken } from '@/api/http'
import type { AccountProfile } from '@/types/identity/account'
import {
  formatCreditDelta,
  formatNextResetAtShanghai,
  type CreditUsage,
} from '@/types/business/credit'

type AccountSection = 'profile' | 'usage' | 'security'

const SECTIONS: { id: AccountSection; label: string }[] = [
  { id: 'profile', label: '个人资料' },
  { id: 'usage', label: '使用情况' },
  { id: 'security', label: '安全' },
]

const router = useRouter()
const activeSection = ref<AccountSection>('profile')
const profile = ref<AccountProfile | null>(null)
const usernameDraft = ref('')
const loading = ref(true)
const saving = ref(false)
const needsLogin = ref(false)
const loadError = ref('')
const saveError = ref('')
const saveOk = ref(false)

const usage = ref<CreditUsage | null>(null)
const usageLoading = ref(false)
const usageError = ref('')

const oldPassword = ref('')
const newPassword = ref('')
const passwordSaving = ref(false)
const passwordError = ref('')
const passwordOk = ref(false)

const avatarLetter = computed(() => {
  const name = profile.value?.username?.trim()
  if (!name) {
    return 'A'
  }
  return name.charAt(0).toUpperCase()
})

const usernameDirty = computed(
  () => profile.value != null && usernameDraft.value.trim() !== profile.value.username,
)

const usageResetLabel = computed(() => {
  if (!usage.value?.nextResetAt) {
    return '—'
  }
  return formatNextResetAtShanghai(usage.value.nextResetAt)
})

onMounted(async () => {
  await loadProfile()
})

watch(activeSection, (section) => {
  if (section === 'usage' && profile.value && !usageLoading.value) {
    void loadUsage()
  }
})

async function loadProfile() {
  loadError.value = ''
  saveError.value = ''
  saveOk.value = false
  if (!getToken()) {
    needsLogin.value = true
    loadError.value = '未登录，请先登录后管理账户'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await getAccountProfile()
    profile.value = data
    usernameDraft.value = data.username
    if (activeSection.value === 'usage') {
      void loadUsage()
    }
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '无法加载个人资料'
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
    }
  } finally {
    loading.value = false
  }
}

async function loadUsage() {
  if (usageLoading.value) {
    return
  }
  usageError.value = ''
  if (!getToken()) {
    needsLogin.value = true
    usageError.value = '未登录，请先登录后查看使用情况'
    return
  }
  usageLoading.value = true
  try {
    const data = await getAccountCreditUsage()
    usage.value = data
  } catch (e) {
    usageError.value = e instanceof ApiError ? e.message : '无法加载使用情况'
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
      loadError.value = e.message || '未登录或登录已过期'
    }
  } finally {
    usageLoading.value = false
  }
}

function selectSection(id: AccountSection) {
  activeSection.value = id
  saveOk.value = false
  passwordOk.value = false
  passwordError.value = ''
}

function onNavKeydown(event: KeyboardEvent) {
  const ids = SECTIONS.map((s) => s.id)
  const index = ids.indexOf(activeSection.value)
  if (index < 0) {
    return
  }
  let next = index
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    next = (index + 1) % ids.length
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    next = (index - 1 + ids.length) % ids.length
  } else if (event.key === 'Home') {
    next = 0
  } else if (event.key === 'End') {
    next = ids.length - 1
  } else {
    return
  }
  event.preventDefault()
  selectSection(ids[next]!)
  const btn = document.getElementById(`account-tab-${ids[next]}`)
  btn?.focus()
}

async function onSaveUsername() {
  saveError.value = ''
  saveOk.value = false
  const next = usernameDraft.value.trim()
  if (!next) {
    saveError.value = '请填写显示名称'
    return
  }
  if (!usernameDirty.value) {
    return
  }
  saving.value = true
  try {
    const data = await updateAccountProfile({ username: next })
    profile.value = data
    usernameDraft.value = data.username
    saveOk.value = true
  } catch (e) {
    saveError.value = e instanceof ApiError ? e.message : '保存失败'
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
      loadError.value = e.message || '未登录或登录已过期'
    }
  } finally {
    saving.value = false
  }
}

async function onChangePassword() {
  passwordError.value = ''
  passwordOk.value = false
  const current = oldPassword.value
  const next = newPassword.value
  if (!current) {
    passwordError.value = '请填写当前密码'
    return
  }
  if (!next) {
    passwordError.value = '请填写新密码'
    return
  }
  if (next.length < 6) {
    passwordError.value = '密码至少 6 位'
    return
  }
  if (next.length > 72) {
    passwordError.value = '密码最长 72 位'
    return
  }
  if (current === next) {
    passwordError.value = '新密码不能与当前密码相同'
    return
  }
  passwordSaving.value = true
  try {
    await changeAccountPassword({ oldPassword: current, newPassword: next })
    oldPassword.value = ''
    newPassword.value = ''
    passwordOk.value = true
  } catch (e) {
    passwordError.value = e instanceof ApiError ? e.message : '修改密码失败'
    if (e instanceof ApiError && e.code === 401) {
      clearToken()
      needsLogin.value = true
      loadError.value = e.message || '未登录或登录已过期'
    }
  } finally {
    passwordSaving.value = false
  }
}

function goLogin() {
  void router.push({ name: 'login' })
}

function logout() {
  clearToken()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <AppHeader />
    <div class="settings-shell">
      <aside class="settings-nav" aria-label="账户设置">
        <div class="settings-nav-title">账户</div>
        <div
          class="settings-tabs"
          role="tablist"
          aria-label="账户分区"
          @keydown="onNavKeydown"
        >
          <button
            v-for="section in SECTIONS"
            :id="`account-tab-${section.id}`"
            :key="section.id"
            type="button"
            class="settings-link"
            role="tab"
            :class="{ on: activeSection === section.id }"
            :aria-selected="activeSection === section.id"
            :tabindex="activeSection === section.id ? 0 : -1"
            :data-section="section.id"
            @click="selectSection(section.id)"
          >
            {{ section.label }}
          </button>
        </div>
      </aside>

      <main class="settings-main">
        <template v-if="loading">
          <p>加载中…</p>
        </template>

        <template v-else-if="needsLogin">
          <p class="error" role="alert">{{ loadError }}</p>
          <p class="nav">
            <button type="button" class="btn btn-primary" @click="goLogin">去登录</button>
          </p>
        </template>

        <template v-else-if="loadError && !profile">
          <p class="error" role="alert">{{ loadError }}</p>
          <p class="nav">
            <button type="button" class="btn btn-ghost" @click="loadProfile">重试</button>
            <router-link :to="{ name: 'login' }">登录</router-link>
          </p>
        </template>

        <template v-else>
          <section
            v-show="activeSection === 'profile'"
            class="settings-panel"
            role="tabpanel"
            aria-labelledby="title-profile"
          >
            <h1 id="title-profile">个人资料</h1>
            <p class="settings-lead">管理你在 Forma 中显示的名称与联系方式。</p>

            <div class="profile-hero">
              <div class="profile-avatar" aria-hidden="true">{{ avatarLetter }}</div>
              <div>
                <div class="profile-name">{{ profile?.username }}</div>
                <div class="profile-sub">{{ profile?.email }}</div>
              </div>
              <button type="button" class="btn btn-ghost" disabled title="即将开放">
                更换头像
              </button>
            </div>

            <div class="settings-card">
              <div class="settings-row">
                <div class="row-body">
                  <label class="row-label" for="display-name">显示名称</label>
                  <input
                    id="display-name"
                    v-model="usernameDraft"
                    class="row-input"
                    type="text"
                    autocomplete="nickname"
                    maxlength="64"
                    @input="saveOk = false"
                  />
                </div>
                <button
                  type="button"
                  class="btn btn-ghost"
                  :disabled="saving || !usernameDirty"
                  @click="onSaveUsername"
                >
                  {{ saving ? '保存中…' : '保存' }}
                </button>
              </div>
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">邮箱</div>
                  <div class="row-value">{{ profile?.email }}</div>
                </div>
                <button type="button" class="btn btn-ghost" disabled title="即将开放">
                  更换邮箱
                </button>
              </div>
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">当前套餐</div>
                  <div class="row-value">可在套餐页查看额度与方案</div>
                </div>
                <router-link class="btn btn-ghost" :to="{ name: 'credits' }">
                  查看套餐
                </router-link>
              </div>
            </div>

            <p v-if="saveError" class="error" role="alert">{{ saveError }}</p>
            <p v-else-if="saveOk" class="ok" role="status">显示名称已更新</p>
          </section>

          <section
            v-show="activeSection === 'usage'"
            class="settings-panel"
            role="tabpanel"
            aria-labelledby="title-usage"
          >
            <h1 id="title-usage">使用情况</h1>
            <p class="settings-lead">
              查看积分余额与近期消耗，扣分发生在成功产出可用结果之后。
            </p>

            <template v-if="usageLoading && !usage">
              <p>加载中…</p>
            </template>

            <template v-else-if="usageError && !usage">
              <p class="error" role="alert">{{ usageError }}</p>
              <p class="nav">
                <button type="button" class="btn btn-ghost" @click="loadUsage">重试</button>
                <router-link class="btn btn-ghost" :to="{ name: 'credits' }">查看套餐</router-link>
              </p>
            </template>

            <template v-else-if="usage">
              <div class="usage-summary" aria-label="本月用量摘要">
                <div class="usage-stat">
                  <div class="usage-label">本月剩余</div>
                  <div class="usage-num">
                    {{ usage.available }} <span>/ {{ usage.monthlyQuota }}</span>
                  </div>
                </div>
                <div class="usage-stat">
                  <div class="usage-label">本月已用</div>
                  <div class="usage-num">{{ usage.used }}</div>
                </div>
                <div class="usage-stat">
                  <div class="usage-label">下次重置</div>
                  <div class="usage-num small">{{ usageResetLabel }}</div>
                </div>
              </div>

              <div v-if="usage.entries.length > 0" class="settings-card">
                <div
                  v-for="entry in usage.entries"
                  :key="entry.holdId"
                  class="usage-item"
                >
                  <div>
                    <div class="row-value">{{ entry.title }}</div>
                    <div class="row-label">{{ formatNextResetAtShanghai(entry.occurredAt) }}</div>
                  </div>
                  <span class="usage-delta">{{ formatCreditDelta(entry.delta) }}</span>
                </div>
              </div>
              <div v-else class="settings-card">
                <div class="usage-empty">
                  <div class="row-value">还没有扣分记录</div>
                  <div class="row-label">
                    成功产出可用结果后才会扣分。当前可先到套餐页查看额度与方案。
                  </div>
                </div>
              </div>

              <p class="pricing-note">
                完整规则见
                <router-link :to="{ name: 'credits' }">套餐页</router-link>
                。选品清单与上架素材各扣 1 积分。
              </p>
              <template v-if="usageError">
                <p class="error" role="alert">{{ usageError }}</p>
                <p class="nav">
                  <button type="button" class="btn btn-ghost" @click="loadUsage">重试</button>
                </p>
              </template>
            </template>
          </section>

          <section
            v-show="activeSection === 'security'"
            class="settings-panel"
            role="tabpanel"
            aria-labelledby="title-security"
          >
            <h1 id="title-security">安全</h1>
            <p class="settings-lead">登录与账户安全相关操作。</p>

            <form class="settings-card" @submit.prevent="onChangePassword">
              <div class="settings-row">
                <div class="row-body">
                  <label class="row-label" for="old-password">当前密码</label>
                  <input
                    id="old-password"
                    v-model="oldPassword"
                    class="row-input"
                    type="password"
                    autocomplete="current-password"
                    maxlength="72"
                    @input="passwordOk = false"
                  />
                </div>
              </div>
              <div class="settings-row">
                <div class="row-body">
                  <label class="row-label" for="new-password">新密码</label>
                  <input
                    id="new-password"
                    v-model="newPassword"
                    class="row-input"
                    type="password"
                    autocomplete="new-password"
                    maxlength="72"
                    @input="passwordOk = false"
                  />
                </div>
                <button
                  type="submit"
                  class="btn btn-ghost"
                  data-action="change-password"
                  :disabled="passwordSaving"
                >
                  {{ passwordSaving ? '更新中…' : '更新密码' }}
                </button>
              </div>
            </form>
            <p v-if="passwordError" class="error" role="alert">{{ passwordError }}</p>
            <p v-else-if="passwordOk" class="ok" role="status">密码已更新</p>

            <div class="settings-card danger">
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">退出登录</div>
                  <div class="row-value">在此设备结束会话</div>
                </div>
                <button type="button" class="btn btn-ghost" data-action="logout" @click="logout">
                  退出
                </button>
              </div>
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">删除账户</div>
                  <div class="row-value">即将开放，当前不可用</div>
                </div>
                <button type="button" class="btn btn-ghost danger-btn" disabled title="即将开放">
                  删除账户
                </button>
              </div>
            </div>
          </section>
        </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.settings-shell {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 0;
  max-width: 1000px;
  margin: 0 auto;
  min-height: calc(100vh - var(--header-h));
  font-family: var(--font);
  color: var(--ink);
}

.settings-nav {
  padding: 28px 16px;
  border-right: 1px solid var(--line);
}

.settings-nav-title {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--mute-2);
  padding: 0 10px 12px;
}

.settings-tabs {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.settings-link {
  display: block;
  width: 100%;
  text-align: left;
  padding: 9px 10px;
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  font-family: inherit;
  font-size: 0.9rem;
  color: var(--mute);
  cursor: pointer;
}

.settings-link:hover,
.settings-link.on {
  color: var(--ink);
  background: var(--line-2);
}

.settings-main {
  padding: 36px 28px 80px;
}

.settings-panel h1 {
  margin: 0 0 8px;
  font-size: 1.5rem;
  letter-spacing: -0.03em;
}

.settings-lead {
  margin: 0 0 24px;
  color: var(--mute);
  font-size: 0.95rem;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 999px;
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.25rem;
  font-weight: 600;
}

.profile-name {
  font-size: 1.1rem;
  font-weight: 600;
}

.profile-sub {
  font-size: 0.875rem;
  color: var(--mute);
}

.settings-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
  margin-bottom: 16px;
}

.settings-card.danger {
  border-color: #e7e5e4;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line-2);
}

.settings-row:last-child {
  border-bottom: none;
}

.row-body {
  min-width: 0;
  flex: 1;
}

.row-label {
  display: block;
  font-size: 0.75rem;
  color: var(--mute);
  margin-bottom: 2px;
}

.row-value {
  font-size: 0.925rem;
  color: var(--ink);
}

.row-input {
  width: 100%;
  max-width: 20rem;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  padding: 0.45rem 0.65rem;
  font-family: inherit;
  font-size: 0.95rem;
  color: var(--ink);
  background: var(--surface);
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
  font-family: inherit;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-ghost {
  border-color: var(--line);
  background: var(--surface);
  color: var(--ink);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--line-2);
}

.btn-primary {
  background: var(--accent);
  color: var(--accent-ink);
  border-color: var(--accent);
}

.danger-btn {
  color: #b91c1c;
}

.error {
  color: #e11d48;
  margin: 0.75rem 0 0;
}

.ok {
  color: #15803d;
  margin: 0.75rem 0 0;
}

.nav {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1.25rem;
}

a {
  color: var(--ink);
}

.usage-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.usage-stat {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  padding: 14px 16px;
}

.usage-label {
  font-size: 0.75rem;
  color: var(--mute);
  margin-bottom: 6px;
}

.usage-num {
  font-size: 1.5rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.usage-num span {
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--mute);
}

.usage-num.small {
  font-size: 1.05rem;
}

.usage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line-2);
}

.usage-item:last-child {
  border-bottom: none;
}

.usage-delta {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  flex-shrink: 0;
}

.usage-empty {
  padding: 14px 16px;
}

.pricing-note {
  margin: 12px 0 0;
  font-size: 0.875rem;
  color: var(--mute);
}

.pricing-note a {
  color: var(--ink);
  text-decoration: underline;
  text-underline-offset: 2px;
}

@media (max-width: 800px) {
  .usage-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 800px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    border-right: none;
    border-bottom: 1px solid var(--line);
    padding: 12px 16px;
  }

  .settings-nav-title {
    width: 100%;
  }

  .settings-tabs {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 4px;
  }

  .settings-link {
    width: auto;
  }

  .settings-main {
    padding: 24px 16px 64px;
  }
}
</style>
