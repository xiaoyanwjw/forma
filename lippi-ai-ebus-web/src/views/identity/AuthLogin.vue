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
    await router.push({ name: 'me' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page">
    <h1>登录</h1>
    <p class="hint">可用用户名或邮箱 + 密码</p>
    <form class="form" @submit.prevent="onSubmit">
      <label>
        账号
        <input v-model="account" autocomplete="username" placeholder="用户名或邮箱" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
    <p class="nav">
      没有账号？
      <router-link :to="{ name: 'register' }">去注册</router-link>
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
input {
  border: 1px solid #c5c9d0;
  border-radius: 3px;
  padding: 0.55rem 0.65rem;
  font-size: 1rem;
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
