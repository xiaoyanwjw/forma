<script setup lang="ts">
import { computed, ref } from 'vue'
import { marked } from 'marked'

const props = withDefaults(
  defineProps<{
    /** Raw markdown source */
    source: string
    /** Show copy control in the top-right corner */
    showCopy?: boolean
    /** Wrap 「…」 segments with .ph after render (chat user bubbles). */
    highlightSlots?: boolean
  }>(),
  {
    showCopy: true,
    highlightSlots: false,
  },
)

/** Drop raw HTML tokens from source; marked still emits safe structural tags. */
marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    html() {
      return ''
    },
  },
})

const SLOT_RE = /「[^」]*」/g

function wrapPromptSlots(html: string): string {
  return html.replace(SLOT_RE, (m) => `<span class="ph">${m}</span>`)
}

const html = computed(() => {
  const raw = props.source ?? ''
  if (!raw.trim()) return ''
  const parsed = (marked.parse(raw, { async: false }) as string).trim()
  return props.highlightSlots ? wrapPromptSlots(parsed) : parsed
})

const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined

async function copySource() {
  const text = props.source ?? ''
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    ta.remove()
  }
  copied.value = true
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 1500)
}
</script>

<template>
  <div
    class="md-view"
    :class="{ 'is-compact': !showCopy }"
    data-testid="markdown-view"
  >
    <button
      v-if="showCopy"
      type="button"
      class="md-copy"
      data-testid="markdown-copy"
      :aria-label="copied ? '已复制' : '复制 Markdown'"
      :title="copied ? '已复制' : '复制'"
      @click="copySource"
    >
      <svg
        v-if="!copied"
        viewBox="0 0 24 24"
        width="16"
        height="16"
        aria-hidden="true"
      >
        <path
          fill="currentColor"
          d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"
        />
      </svg>
      <svg
        v-else
        viewBox="0 0 24 24"
        width="16"
        height="16"
        aria-hidden="true"
      >
        <path
          fill="currentColor"
          d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"
        />
      </svg>
    </button>
    <!-- eslint-disable-next-line vue/no-v-html -- marked output; raw HTML tokens stripped -->
    <div class="md-body" data-testid="markdown-body" v-html="html" />
  </div>
</template>

<style scoped>
.md-view {
  position: relative;
  margin: 0 0 10px;
}

.md-copy {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 6px;
  background: var(--surface, #fff);
  color: var(--mute, #64748b);
  cursor: pointer;
  line-height: 0;
}

.md-copy:hover {
  color: var(--ink, #0f172a);
  border-color: color-mix(in srgb, var(--ink, #0f172a) 18%, var(--line, #e5e7eb));
}

.md-body {
  padding-right: 36px;
  font-size: 0.8125rem;
  color: var(--ink, #0f172a);
  line-height: 1.55;
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 0.9em 0 0.4em;
  font-weight: 650;
  line-height: 1.35;
  color: var(--ink, #0f172a);
}

.md-body :deep(h1) {
  font-size: 1.15rem;
}

.md-body :deep(h2) {
  font-size: 1.02rem;
}

.md-body :deep(h3) {
  font-size: 0.92rem;
}

.md-body :deep(h4) {
  font-size: 0.85rem;
}

.md-body :deep(h1:first-child),
.md-body :deep(h2:first-child),
.md-body :deep(h3:first-child),
.md-body :deep(h4:first-child),
.md-body :deep(p:first-child) {
  margin-top: 0;
}

.md-body :deep(p) {
  margin: 0 0 0.65em;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0 0 0.65em;
  padding-left: 1.35em;
}

.md-body :deep(li) {
  margin: 0.2em 0;
}

.md-body :deep(a) {
  color: #15803d;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.md-body :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.9em;
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: color-mix(in srgb, var(--chip, #f1f5f9) 88%, transparent);
}

.md-body :deep(pre) {
  margin: 0 0 0.75em;
  padding: 10px 12px;
  overflow: auto;
  border-radius: 6px;
  border: 1px solid var(--line, #e5e7eb);
  background: var(--surface, #fff);
}

.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.md-body :deep(blockquote) {
  margin: 0 0 0.75em;
  padding: 0.15em 0 0.15em 0.85em;
  border-left: 3px solid var(--line, #e5e7eb);
  color: var(--mute, #64748b);
}

.md-body :deep(hr) {
  margin: 0.9em 0;
  border: 0;
  border-top: 1px solid var(--line, #e5e7eb);
}

.md-body :deep(strong) {
  font-weight: 650;
}

.md-view.is-compact {
  margin: 0;
  white-space: normal;
}

.md-view.is-compact .md-body {
  padding-right: 0;
  font-size: inherit;
  line-height: inherit;
  white-space: normal;
}

.md-view.is-compact .md-body :deep(p) {
  margin: 0 0 0.35em;
}

.md-view.is-compact .md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(.ph) {
  display: inline;
  padding: 0.05em 0.35em;
  border-radius: 4px;
  border: 1px solid var(--line, #e5e7eb);
  background: color-mix(in srgb, var(--chip, #f1f5f9) 88%, transparent);
  font-weight: inherit;
}
</style>
