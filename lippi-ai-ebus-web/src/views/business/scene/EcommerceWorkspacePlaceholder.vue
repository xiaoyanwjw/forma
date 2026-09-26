<script setup lang="ts">
import AppHeader from '@/components/common/AppHeader.vue'
</script>

<template>
  <div class="shell">
    <AppHeader active-nav="scenes" />
    <main class="page">
      <div class="inner">
        <h1>电商开店</h1>
        <p>从这里继续选品与上架。</p>
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
  max-width: 960px;
  margin: 0 auto;
  padding: 48px 24px 80px;
}

h1 {
  margin: 0 0 8px;
  font-size: clamp(1.65rem, 2.6vw, 2rem);
  font-weight: 600;
  letter-spacing: -0.03em;
}

p {
  margin: 0;
  color: var(--mute);
  font-size: 0.95rem;
  max-width: 36em;
}
</style>
