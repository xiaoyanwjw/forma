<script setup lang="ts">
import type { SessionSummary } from '@/types/business/session'

defineProps<{
  sessions: SessionSummary[]
  selectedSessionId: string | null
  sessionsError: string
  sceneLabel: string
}>()

const emit = defineEmits<{
  newTask: []
  select: [session: SessionSummary]
}>()
</script>

<template>
  <aside class="sidebar" aria-label="会话侧栏">
    <button type="button" class="side-new" @click="emit('newTask')">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path d="M12 5v14M5 12h14" />
      </svg>
      新任务
    </button>
    <div class="side-section">会话</div>
    <p v-if="sessionsError" class="sessions-error" data-testid="sessions-error">{{ sessionsError }}</p>
    <div class="session-list" data-testid="session-list">
      <button
        v-for="s in sessions"
        :key="s.sessionId"
        type="button"
        class="side-item"
        :class="{ on: selectedSessionId === s.sessionId }"
        data-testid="session-item"
        @click="emit('select', s)"
      >
        {{ s.title }}
        <span class="sub">{{ sceneLabel }}</span>
      </button>
    </div>
  </aside>
</template>
