<script setup lang="ts">
defineProps<{
  sceneCode: string
}>()
</script>

<template>
  <svg
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    aria-hidden="true"
  >
    <template v-if="sceneCode === 'ecommerce'">
      <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
      <path d="M9 22V12h6v10" />
    </template>
    <template v-else-if="sceneCode === 'short_video'">
      <rect x="2" y="4" width="20" height="16" rx="2" />
      <path d="M10 9l5 3-5 3V9z" />
    </template>
    <template v-else-if="sceneCode === 'xiaohongshu'">
      <path d="M12 20l-3.5-7H4l8-9 8 9h-4.5L12 20z" />
    </template>
    <template v-else-if="sceneCode === 'local_life'">
      <path d="M12 21s7-4.5 7-11a7 7 0 10-14 0c0 6.5 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="7" />
    </template>
  </svg>
</template>
