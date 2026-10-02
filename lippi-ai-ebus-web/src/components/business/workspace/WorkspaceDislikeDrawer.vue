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
