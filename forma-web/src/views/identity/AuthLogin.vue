<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/identity/auth'
import { afterLogin } from '@/api/identity/afterLogin'
import { ApiError } from '@/api/client'

const router = useRouter()
const account = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''
  if (!account.value.trim() || !password.value) {
    error.value = '请填写账号和密码'
    return
  }
  loading.value = true
  try {
    const result = await login({
      account: account.value.trim(),
      password: password.value,
    })
    await afterLogin(result.token)
    await router.push({ name: 'scenes' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败'
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
      <h1>登录</h1>
      <p class="lede">和 Forma 一起开始创作</p>

      <form class="form" @submit.prevent="onSubmit">
        <label>
          账号
          <input v-model="account" autocomplete="username" placeholder="用户名或邮箱" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" placeholder="密码" />
        </label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button type="submit" class="btn-ink" :disabled="loading">
          {{ loading ? '登录中…' : '继续' }}
        </button>
      </form>

      <p class="nav">
        还没有账号？
        <RouterLink :to="{ name: 'register' }">注册</RouterLink>
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

input {
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  padding: 0.7rem 0.75rem;
  font: inherit;
  font-size: 0.95rem;
  background: var(--surface);
  color: var(--ink);
}

input:focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: 1px;
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
