<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentEmptyRun } from '@/composables/agent/useAgentEmptyRun'
import { getToken } from '@/api/http'

const router = useRouter()
const ready = ref(false)
const { running, error, eventNames, events, startEmptyRun } = useAgentEmptyRun()

onMounted(() => {
  if (!getToken()) {
    void router.replace({ name: 'login' })
    return
  }
  ready.value = true
})

async function onStart() {
  await startEmptyRun({ sceneCode: 'ecommerce' })
}
</script>

<template>
  <main v-if="ready" class="page">
    <h1>Agent 空跑试跑</h1>
    <p class="hint">
      登录后发起一次空跑：会暂时预占 1 积分，结束后释放，不实扣。用于验证 SSE 事件骨架。
    </p>
    <p class="actions">
      <button type="button" :disabled="running" @click="onStart">
        {{ running ? '空跑中…' : '发起空跑' }}
      </button>
      <router-link :to="{ name: 'me' }">返回我的账号</router-link>
      <router-link :to="{ name: 'credits' }">套餐与积分</router-link>
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="eventNames.length" class="events">
      <h2>AD-4 事件名</h2>
      <ol>
        <li v-for="(name, index) in eventNames" :key="`${index}-${name}`">{{ name }}</li>
      </ol>
      <details>
        <summary>事件详情</summary>
        <pre>{{ JSON.stringify(events, null, 2) }}</pre>
      </details>
    </section>
  </main>
</template>

<style scoped>
.page {
  max-width: 36rem;
  margin: 3rem auto;
  padding: 0 1rem;
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  color: #121212;
}
.hint {
  color: #444;
  line-height: 1.5;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  margin: 1.25rem 0;
}
button {
  background: #e11d48;
  color: #fff;
  border: none;
  border-radius: 3px;
  padding: 0.5rem 1rem;
  cursor: pointer;
  font-family: var(--font);
}
button:disabled {
  opacity: 0.6;
  cursor: wait;
}
.error {
  color: #e11d48;
}
.events h2 {
  font-family: var(--font);
  font-size: 1.1rem;
}
.events ol {
  padding-left: 1.25rem;
}
.events pre {
  overflow: auto;
  background: #f4f6f8;
  padding: 0.75rem;
  border-radius: 3px;
  font-size: 0.8rem;
}
a {
  color: #121212;
}
</style>
