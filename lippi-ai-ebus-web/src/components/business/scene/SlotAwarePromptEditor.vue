<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { splitPromptSlots } from '@/utils/promptSlots'

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
    />
  </div>
</template>
