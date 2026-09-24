<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, login as loginApi } from '@/api/identity/auth'
import { afterLogin } from '@/api/identity/afterLogin'
import { ApiError } from '@/api/client'
import {
  AI_DISCLAIMER_REGISTER_LABEL,
  AI_DISCLAIMER_SHORT,
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
    await router.push({ name: 'credits' })
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
  <main class="page">
    <h1>注册</h1>
    <p class="hint">请并用用户名与邮箱；成功后自动登录并校验 /me</p>
    <form class="form" @submit.prevent="onSubmit">
      <label>
        用户名
        <input v-model="username" autocomplete="username" />
      </label>
      <label>
        邮箱
        <input v-model="email" type="email" autocomplete="email" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="new-password" />
      </label>
      <p class="disclaimer" data-testid="ai-disclaimer">{{ AI_DISCLAIMER_SHORT }}</p>
      <label class="agree">
        <input v-model="agreedToAiDisclaimer" type="checkbox" />
        <span>{{ AI_DISCLAIMER_REGISTER_LABEL }}</span>
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? '提交中…' : '注册' }}</button>
    </form>
    <p class="nav">
      已有账号？
      <router-link :to="{ name: 'login' }">去登录</router-link>
    </p>
  </main>
</template>

<style scoped>
.page {
  max-width: 28rem;
  margin: 3rem auto;
  padding: 0 1rem;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  color: #121212;
}
.hint {
  color: #555;
  margin-bottom: 1.5rem;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}
input:not([type='checkbox']) {
  border: 1px solid #c5c9d0;
  border-radius: 3px;
  padding: 0.55rem 0.65rem;
  font-size: 1rem;
}
.disclaimer {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.55;
  color: #667085;
}
.agree {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: #121212;
  cursor: pointer;
}
.agree input {
  margin-top: 0.2rem;
  flex-shrink: 0;
}
button {
  margin-top: 0.5rem;
  background: #e11d48;
  color: #fff;
  border: none;
  border-radius: 3px;
  padding: 0.65rem 1rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  color: #e11d48;
  margin: 0;
}
.nav {
  margin-top: 1.25rem;
}
</style>
