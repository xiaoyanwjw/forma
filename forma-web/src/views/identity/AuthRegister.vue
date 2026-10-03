<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, login as loginApi } from '@/api/identity/auth'
import { afterLogin } from '@/api/identity/afterLogin'
import { ApiError } from '@/api/client'
import {
  AI_DISCLAIMER_REGISTER_LABEL,
} from '@/constants/compliance'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const agreedToAiDisclaimer = ref(false)
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''
  if (!username.value.trim() || !email.value.trim() || !password.value) {
    error.value = '请填写用户名、邮箱和密码'
    return
  }
  if (!agreedToAiDisclaimer.value) {
    error.value = '请先确认已知悉合规声明后再注册'
    return
  }
  loading.value = true
  let phase: 'register' | 'login' | 'me' = 'register'
  try {
    await register({
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value,
      agreedToAiDisclaimer: true,
    })
    phase = 'login'
    const result = await loginApi({
      account: username.value.trim(),
      password: password.value,
    })
    phase = 'me'
    await afterLogin(result.token)
    await router.push({ name: 'scenes' })
  } catch (e) {
    if (phase === 'login') {
      error.value = '注册成功，但自动登录失败，请手动登录'
    } else if (e instanceof ApiError) {
      error.value = e.message
    } else {
      error.value = '注册失败'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth">
    <header class="auth-top">
      <RouterLink class="logo" :to="{ name: 'landing' }" aria-label="Forma">
        <span class="logo-mark" aria-hidden="true">◇</span>
        Forma
      </RouterLink>
    </header>

    <section class="panel">
      <div class="mark" aria-hidden="true">◇</div>
      <h1>注册</h1>
      <p class="lede">和 Forma 一起开始创作</p>

      <form class="form" @submit.prevent="onSubmit">
        <label>
          用户名
          <input v-model="username" autocomplete="username" placeholder="用户名" />
        </label>
        <label>
          邮箱
          <input v-model="email" type="email" autocomplete="email" placeholder="邮箱" />
        </label>
        <label>
          密码
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            placeholder="密码"
          />
        </label>
        <label class="agree">
          <input v-model="agreedToAiDisclaimer" type="checkbox" />
          <span>{{ AI_DISCLAIMER_REGISTER_LABEL }}</span>
        </label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button type="submit" class="btn-ink" :disabled="loading">
          {{ loading ? '提交中…' : '继续' }}
        </button>
      </form>

      <p class="nav">
        已有账号？
        <RouterLink :to="{ name: 'login' }">登录</RouterLink>
      </p>
    </section>
  </main>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background-color: var(--canvas);
  background-image: radial-gradient(var(--dot) 1px, transparent 1px);
  background-size: 20px 20px;
  font-family: var(--font);
  color: var(--ink);
}

.auth-top {
  padding: 0.9rem 1.5rem;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-display);
  font-weight: 650;
  font-size: 1.05rem;
  letter-spacing: -0.03em;
  color: inherit;
  text-decoration: none;
}

.logo-mark {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: var(--r-sm);
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 0.65rem;
  font-weight: 700;
}

.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: min(24rem, 100%);
  margin: 0 auto;
  padding: 1.5rem 1.25rem 3rem;
  text-align: center;
  animation: auth-in 0.35s ease-out both;
}

@keyframes auth-in {
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
  .panel {
    animation: none;
  }
}

.mark {
  width: 44px;
  height: 44px;
  border-radius: var(--r-sm);
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.05rem;
  font-weight: 700;
  margin-bottom: 16px;
}

h1 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 1.75rem;
  font-weight: 650;
  letter-spacing: -0.03em;
}

.lede {
  margin: 0 0 1.75rem;
  color: var(--mute);
  font-size: 0.95rem;
  line-height: 1.5;
}

.form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  text-align: left;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.875rem;
  color: var(--ink);
}

input:not([type='checkbox']) {
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  padding: 0.7rem 0.75rem;
  font: inherit;
  font-size: 0.95rem;
  background: var(--surface);
  color: var(--ink);
}

input:not([type='checkbox']):focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: 1px;
}

.agree {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--ink);
  cursor: pointer;
}

.agree input {
  margin-top: 0.2rem;
  flex-shrink: 0;
}

.btn-ink {
  margin-top: 0.35rem;
  width: 100%;
  border: 1px solid var(--ink);
  border-radius: var(--r-sm);
  padding: 0.75rem 1rem;
  background: var(--ink);
  color: #fff;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 550;
  cursor: pointer;
}

.btn-ink:hover:not(:disabled) {
  background: color-mix(in srgb, var(--ink) 82%, #fff);
}

.btn-ink:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: var(--accent);
  font-size: 0.875rem;
}

.nav {
  margin-top: 1.35rem;
  font-size: 0.875rem;
  color: var(--mute);
}

.nav a {
  color: var(--ink);
  font-weight: 550;
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
