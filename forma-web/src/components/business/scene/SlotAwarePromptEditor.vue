<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { slotRangeContaining, splitPromptSlots } from '@/utils/promptSlots'

const model = defineModel<string>({ default: '' })

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    placeholder?: string
    ariaLabel?: string
    rows?: number
  }>(),
  {
    disabled: false,
    placeholder: '',
    ariaLabel: '继续提问',
    rows: 2,
  },
)

const ta = ref<HTMLTextAreaElement | null>(null)
const hl = ref<HTMLElement | null>(null)

const segments = computed(() => splitPromptSlots(model.value))
const hasValue = computed(() => model.value.length > 0)

function syncScroll() {
  if (!ta.value || !hl.value) return
  hl.value.scrollTop = ta.value.scrollTop
  hl.value.scrollLeft = ta.value.scrollLeft
}

function selectSlotIfCaretInside() {
  const el = ta.value
  if (!el || props.disabled) return
  const start = el.selectionStart ?? 0
  const end = el.selectionEnd ?? 0
  if (start !== end) return
  const range = slotRangeContaining(model.value, start)
  if (!range) return
  el.setSelectionRange(range.start, range.end)
}

function onPointerUp() {
  void nextTick(selectSlotIfCaretInside)
}

function onBeforeInput(e: InputEvent) {
  const el = ta.value
  if (!el || props.disabled) return
  const selStart = el.selectionStart ?? 0
  const selEnd = el.selectionEnd ?? 0
  if (selStart !== selEnd) return
  const range = slotRangeContaining(model.value, selStart)
  if (!range) return
  el.setSelectionRange(range.start, range.end)
  if (e.inputType === 'insertCompositionText') return
  const data = e.data
  if (e.inputType.startsWith('insert') && data) {
    e.preventDefault()
    replaceRange(range.start, range.end, data)
    return
  }
  if (e.inputType.startsWith('delete')) {
    e.preventDefault()
    replaceRange(range.start, range.end, '')
  }
}

function onCompositionStart() {
  selectSlotIfCaretInside()
}

function replaceRange(start: number, end: number, insert: string) {
  const next = model.value.slice(0, start) + insert + model.value.slice(end)
  model.value = next
  void nextTick(() => {
    const el = ta.value
    if (!el) return
    const caret = start + insert.length
    el.setSelectionRange(caret, caret)
    syncScroll()
  })
}

watch(model, async () => {
  await nextTick()
  syncScroll()
})
</script>

<template>
  <div class="prompt-editor-stack">
    <div
      ref="hl"
      class="prompt-highlight"
      aria-hidden="true"
      :class="{ 'is-empty': !hasValue }"
    >
      <template v-if="hasValue">
        <template v-for="(seg, i) in segments" :key="i">
          <span v-if="seg.slot" class="ph">{{ seg.text }}</span>
          <template v-else>{{ seg.text }}</template>
        </template>
      </template>
    </div>
    <textarea
      ref="ta"
      v-model="model"
      class="prompt-editor"
      :class="{ 'has-value': hasValue }"
      :rows="props.rows"
      :placeholder="props.placeholder"
      :aria-label="props.ariaLabel"
      :disabled="props.disabled"
      @scroll="syncScroll"
      @mouseup="onPointerUp"
      @keyup="onPointerUp"
      @beforeinput="onBeforeInput"
      @compositionstart="onCompositionStart"
    />
  </div>
</template>
