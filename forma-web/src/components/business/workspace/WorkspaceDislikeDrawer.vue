<script setup lang="ts">
defineProps<{
  open: boolean
  note: string
  busy: boolean
  canSubmit: boolean
}>()

const emit = defineEmits<{
  'update:note': [value: string]
  cancel: []
  submit: []
}>()
</script>

<template>
  <div
    v-if="open"
    class="dislike-drawer"
    data-testid="dislike-drawer"
    @click.stop
  >
    <p class="dislike-drawer-title">这次成果哪里不好？</p>
    <textarea
      class="dislike-comment"
      data-testid="dislike-comment"
      rows="4"
      maxlength="512"
      placeholder="可选短文说明"
      aria-label="质量差短文"
      :value="note"
      :disabled="busy"
      @input="emit('update:note', ($event.target as HTMLTextAreaElement).value)"
    />
    <div class="dislike-drawer-actions">
      <button type="button" class="pill" data-testid="dislike-cancel" @click="emit('cancel')">
        取消
      </button>
      <button
        type="button"
        class="pill"
        data-testid="dislike-submit"
        :disabled="!canSubmit"
        @click="emit('submit')"
      >
        提交
      </button>
    </div>
  </div>
</template>

<style scoped>
.dislike-drawer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  padding: 16px 18px 20px;
  border-top: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.08);
}

.dislike-drawer-title {
  margin: 0 0 10px;
  font-size: 0.9rem;
  color: var(--ink);
}

.dislike-comment {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  background: transparent;
  color: var(--ink);
  font-size: 0.8125rem;
  resize: vertical;
}

.dislike-drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
