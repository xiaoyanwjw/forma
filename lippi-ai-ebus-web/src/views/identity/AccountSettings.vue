<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/common/AppHeader.vue'
import { getAccountProfile, updateAccountProfile } from '@/api/identity/account'
import { ApiError } from '@/api/client'
import { clearToken, getToken } from '@/api/http'
import type { AccountProfile } from '@/types/identity/account'

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

onMounted(async () => {
  await loadProfile()
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

function selectSection(id: AccountSection) {
  activeSection.value = id
  saveOk.value = false
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
            <p class="settings-lead">管理你在 Adam 中显示的名称与联系方式。</p>

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
              积分余额与近期消耗将在此展示。扣分发生在成功产出可用结果之后。
            </p>
            <div class="settings-card">
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">用量明细</div>
                  <div class="row-value">即将完善，当前可先到套餐页查看余额。</div>
                </div>
                <router-link class="btn btn-ghost" :to="{ name: 'credits' }">
                  查看套餐
                </router-link>
              </div>
            </div>
          </section>

          <section
            v-show="activeSection === 'security'"
            class="settings-panel"
            role="tabpanel"
            aria-labelledby="title-security"
          >
            <h1 id="title-security">安全</h1>
            <p class="settings-lead">登录与账户安全相关操作。改密能力即将完善。</p>

            <div class="settings-card">
              <div class="settings-row">
                <div class="row-body">
                  <div class="row-label">密码</div>
                  <div class="row-value">即将支持在此修改密码</div>
                </div>
                <button type="button" class="btn btn-ghost" disabled title="即将开放">
                  更新密码
                </button>
              </div>
            </div>

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
  background: var(--accent);
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
  color: #fff;
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
