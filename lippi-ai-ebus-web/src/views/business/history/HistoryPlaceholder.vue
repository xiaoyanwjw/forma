<script setup lang="ts">
import AppHeader from '@/components/common/AppHeader.vue'
</script>

<template>
  <div class="shell">
    <AppHeader active-nav="history" />
    <main class="page">
      <div class="inner">
        <h1>历史</h1>
        <p>近期任务记录会显示在这里。</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.page {
  min-height: calc(100vh - var(--header-h));
}

.inner {
  max-width: 880px;
  margin: 0 auto;
  padding: 40px 24px 80px;
}

h1 {
  margin: 0 0 8px;
  font-size: 1.75rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

p {
  margin: 0;
  color: var(--mute);
}
</style>
