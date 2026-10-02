<script setup lang="ts">
withDefaults(
  defineProps<{
    fileName: string
    meta?: string
  }>(),
  {
    meta: '',
  },
)
</script>

<template>
  <article
    class="git-view"
    data-testid="git-view"
  >
    <div class="git-filebar">
      <span class="name">{{ fileName }}</span>
      <template v-if="meta">
        <span class="sep">·</span>
        <span>{{ meta }}</span>
      </template>
    </div>
    <div class="git-md">
      <slot />
    </div>
  </article>
</template>

<style scoped>
.git-view {
  width: min(100%, 520px);
  align-self: flex-start;
  background: #fff;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  overflow: hidden;
}

.git-filebar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #d0d7de;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.72rem;
  color: #656d76;
}

.git-filebar .name {
  color: #24292f;
  font-weight: 600;
}

.git-filebar .sep {
  color: #d0d7de;
}

.git-md {
  padding: 24px 24px 28px;
  color: #24292f;
  font-size: 0.875rem;
  line-height: 1.6;
}

.git-md :deep(h1) {
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #d0d7de;
  font-size: 1.35rem;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.3;
  color: #24292f;
}

.git-md :deep(.lead) {
  margin: 0 0 20px;
  font-size: 0.8125rem;
  color: #656d76;
  line-height: 1.5;
}

.git-md :deep(h2) {
  margin: 22px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #d8dee4;
  font-size: 1.05rem;
  font-weight: 650;
  letter-spacing: -0.015em;
  color: #24292f;
}

.git-md :deep(h2:first-child) {
  margin-top: 0;
}

.git-md :deep(h3) {
  margin: 0 0 6px;
  font-size: 0.95rem;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: #24292f;
  line-height: 1.4;
}

.git-md :deep(p.body),
.git-md :deep(.git-body) {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1.65;
  color: #24292f;
  white-space: pre-wrap;
  word-break: break-word;
}

.git-md :deep(ol.shots) {
  margin: 0 0 4px;
  padding: 0 0 0 1.25em;
}

.git-md :deep(ol.shots > li) {
  margin: 0 0 14px;
  padding: 0;
  color: #24292f;
}

.git-md :deep(ol.shots > li::marker) {
  color: #656d76;
  font-weight: 600;
}

.git-md :deep(.shot-t) {
  margin: 0 0 6px;
  font-size: 0.875rem;
  font-weight: 600;
  color: #24292f;
}

.git-md :deep(pre.prompt) {
  margin: 0;
  padding: 10px 12px;
  background: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.72rem;
  line-height: 1.55;
  color: #24292f;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}

.git-md :deep(.neg) {
  margin: 6px 0 0;
  font-size: 0.72rem;
  color: #656d76;
}

.git-md :deep(.thumbs) {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0 0 18px;
}

.git-md :deep(.thumb) {
  aspect-ratio: 4 / 3;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  background: #f6f8fa;
  position: relative;
  overflow: hidden;
}

.git-md :deep(.thumb:nth-child(1)) {
  background:
    radial-gradient(ellipse 70% 60% at 70% 30%, rgba(255, 255, 255, 0.7), transparent 55%),
    linear-gradient(155deg, #eaeef2, #c5ced6);
}

.git-md :deep(.thumb:nth-child(2)) {
  background: linear-gradient(160deg, #e2e5e8, #a8b0b8);
}

.git-md :deep(.thumb:nth-child(3)) {
  background: linear-gradient(170deg, #eef0ea, #b8c2b4);
}

.git-md :deep(.thumb span) {
  position: absolute;
  left: 6px;
  bottom: 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.6rem;
  color: #fff;
  background: rgba(36, 41, 47, 0.55);
  border-radius: 4px;
  padding: 2px 5px;
}

.git-md :deep(.warn) {
  outline: 1px solid #f59e0b;
  outline-offset: 2px;
  border-radius: 4px;
}
</style>
