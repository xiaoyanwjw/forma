<script setup lang="ts">
import { FEEDBACK_TAG_GOOD_QUALITY, FEEDBACK_TAG_POOR_QUALITY } from '@/types/business/feedback'

defineProps<{
  canRetry: boolean
  canSubmitFeedback: boolean
  canDislike: boolean
  feedbackTag: string | null
  feedbackHint: string
}>()

const emit = defineEmits<{
  retry: []
  like: []
  dislike: []
}>()
</script>

<template>
  <div class="card-result-actions" data-testid="card-result-actions" @click.stop>
    <button
      type="button"
      class="card-action-icon"
      data-testid="one-click-retry"
      aria-label="重试"
      title="重试"
      :disabled="!canRetry"
      @click="emit('retry')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          fill="currentColor"
          d="M12 6V3L8 7l4 4V8c2.76 0 5 2.24 5 5a5 5 0 0 1-9.9 1h-2.02A7 7 0 0 0 12 20c3.87 0 7-3.13 7-7s-3.13-7-7-7z"
        />
      </svg>
    </button>
    <button
      type="button"
      class="card-action-icon"
      data-testid="card-like"
      aria-label="点赞"
      title="点赞"
      :class="{ 'is-on': feedbackTag === FEEDBACK_TAG_GOOD_QUALITY }"
      :disabled="!canSubmitFeedback"
      @click="emit('like')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          fill="currentColor"
          d="M9 21h9a2 2 0 0 0 1.86-1.26l2.7-7.05A1.5 1.5 0 0 0 21.18 10H14V6a3 3 0 0 0-3-3l-4 9v9zm-6 0h4V12H3v9z"
        />
      </svg>
    </button>
    <button
      type="button"
      class="card-action-icon"
      data-testid="card-dislike"
      aria-label="点踩"
      title="点踩"
      :class="{ 'is-on': feedbackTag === FEEDBACK_TAG_POOR_QUALITY }"
      :disabled="!canDislike"
      @click="emit('dislike')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          fill="currentColor"
          d="M15 3H6a2 2 0 0 0-1.86 1.26l-2.7 7.05A1.5 1.5 0 0 0 2.82 14H10v4a3 3 0 0 0 3 3l4-9V3zm6 0h-4v9h4V3z"
        />
      </svg>
    </button>
    <p v-if="feedbackHint" class="feedback-hint" data-testid="feedback-hint" role="status">
      {{ feedbackHint }}
    </p>
  </div>
</template>

<style scoped>
.card-result-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 0;
  padding: 0;
}

.card-action-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--mute);
  cursor: pointer;
  transition:
    color 0.15s,
    background 0.15s;
}

.card-action-icon svg {
  width: 16px;
  height: 16px;
  display: block;
}

.card-action-icon:hover:not(:disabled) {
  color: var(--ink);
  background: color-mix(in srgb, var(--chip) 80%, transparent);
}

.card-action-icon.is-on {
  color: var(--accent);
}

.card-action-icon:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.feedback-hint {
  flex: 1 1 100%;
  margin: 0;
  font-size: 0.75rem;
  color: var(--mute);
}
</style>
