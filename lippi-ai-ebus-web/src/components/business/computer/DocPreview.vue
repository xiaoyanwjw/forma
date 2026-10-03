<script setup lang="ts">
import { computed } from 'vue'
import type { ComputerDocView } from '@/types/business/computerView'
import { toPreviewHtml } from '@/utils/computerDocHtml'
import GitView from './GitView.vue'
import 'github-markdown-css/github-markdown-light.css'

const props = defineProps<{ document: ComputerDocView; fileName?: string }>()
const emit = defineEmits<{ handoff: [{ skillId: string; prompt: string }] }>()

const html = computed(() => toPreviewHtml(props.document.format, props.document.content))
const name = computed(
  () => (props.fileName || '').trim() || `${props.document.title || 'doc'}.md`,
)

function onClick(e: MouseEvent) {
  const t = e.target
  if (!(t instanceof Element)) return
  const btn = t.closest('[data-adam-action="handoff"]') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  const skillId = btn.getAttribute('data-adam-skill-id')?.trim() || ''
  const prompt = btn.getAttribute('data-adam-prompt') || ''
  if (!skillId || !prompt.trim()) return
  emit('handoff', { skillId, prompt })
}
</script>

<template>
  <GitView :file-name="name">
    <div
      class="markdown-body"
      data-testid="doc-preview-body"
      @click="onClick"
      v-html="html"
    />
  </GitView>
</template>
