<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe } from '@/api/identity/auth'
import { ApiError } from '@/api/client'
import { clearToken, getToken } from '@/api/http'
import type { Me } from '@/types/identity/auth'

const router = useRouter()
const me = ref<Me | null>(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
  if (!getToken()) {
    error.value = '未登录'
    loading.value = false
    return
  }
  try {
    me.value = await getMe()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法获取当前用户'
    clearToken()
  } finally {
    loading.value = false
  }
})

function logout() {
  clearToken()
  void router.push({ name: 'login' })
}
</script>

<template>
  <main class="page">
    <h1>当前用户</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <dl v-else-if="me" class="me">
      <dt>userId</dt>
      <dd>{{ me.userId }}</dd>
      <dt>用户名</dt>
      <dd>{{ me.username }}</dd>
      <dt>邮箱</dt>
      <dd>{{ me.email }}</dd>
    </dl>
    <p class="nav">
      <button type="button" @click="logout">退出</button>
      <router-link :to="{ name: 'credits' }">套餐与积分</router-link>
      <router-link :to="{ name: 'login' }">登录</router-link>
      <router-link :to="{ name: 'register' }">注册</router-link>
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
.me {
  display: grid;
  grid-template-columns: 5rem 1fr;
  gap: 0.5rem 1rem;
}
.me dt {
  color: #666;
}
.me dd {
  margin: 0;
  word-break: break-all;
}
.error {
  color: #e11d48;
}
.nav {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1.5rem;
}
button {
  background: #121212;
  color: #fff;
  border: none;
  border-radius: 3px;
  padding: 0.45rem 0.85rem;
  cursor: pointer;
}
</style>
