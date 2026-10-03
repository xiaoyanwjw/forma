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
      <span
        class="file-icon"
        aria-hidden="true"
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="currentColor"
        >
          <path
            d="M3.75 1.5a.25.25 0 0 0-.25.25v11.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25V6H9.75A1.75 1.75 0 0 1 8 4.25V1.5Zm6.906.188L14 4.531V4.25A1.75 1.75 0 0 0 12.25 2.5h-1.594ZM2 1.75C2 .784 2.784 0 3.75 0h6.5a.75.75 0 0 1 .53.22l4 4a.75.75 0 0 1 .22.53v8.5A1.75 1.75 0 0 1 13.25 15h-8.5A1.75 1.75 0 0 1 3 13.25V1.75Z"
          />
        </svg>
      </span>
      <span class="name">{{ fileName }}</span>
      <template v-if="meta">
        <span class="sep">·</span>
        <span class="meta">{{ meta }}</span>
      </template>
    </div>
    <div class="git-md">
      <slot />
    </div>
  </article>
</template>

<style scoped>
/* GitHub blob / README frame — content styles live in docPreview.css */
.git-view {
  width: 100%;
  max-width: 100%;
  align-self: stretch;
  background: #fff;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 1px 0 rgba(31, 35, 40, 0.04);
}

.git-filebar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 16px;
  background: #f6f8fa;
  border-bottom: 1px solid #d0d7de;
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 20px;
  color: #656d76;
}

.git-filebar .file-icon {
  display: inline-flex;
  flex: 0 0 auto;
  color: #656d76;
}

.git-filebar .name {
  color: #24292f;
  font-weight: 600;
}

.git-filebar .sep {
  color: #d0d7de;
}

.git-filebar .meta {
  color: #656d76;
}

.git-md {
  padding: 24px 24px 28px;
  background: #fff;
}

@media (max-width: 720px) {
  .git-md {
    padding: 20px 16px 28px;
  }
}
</style>
