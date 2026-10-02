<script setup lang="ts">
import { computed } from 'vue'
import type {
  ComputerBlock,
  ComputerDocument,
  ComputerListItem,
  ComputerListLine,
  ComputerNoteBlock,
  ComputerTag,
} from '@/types/business/computerView'
import { displayTagText, sanitizeHttpsHref } from '@/types/business/computerView'
import MarkdownView from '@/components/common/MarkdownView.vue'
import GitView from './GitView.vue'
import {
  buildStoryboardBeats,
  defaultGitFileName,
  hasStoryboardDoc,
  storyboardSectionClass,
  toStoryboardDoc,
} from './gitDoc'

const props = withDefaults(
  defineProps<{
    document: ComputerDocument
    /** Override README file bar name; default slug(document.title).md */
    fileName?: string
    itemActionLabel?: string
    /** Semantic badge key `priority` → display text (电商「优先试」/ 小红书「优先发」). */
    priorityBadgeLabel?: string
    isItemActionEnabled?: (item: ComputerListItem, index: number) => boolean
  }>(),
  {
    fileName: undefined,
    itemActionLabel: '',
    priorityBadgeLabel: '优先试',
    isItemActionEnabled: () => true,
  },
)

const emit = defineEmits<{
  'item-action': [{ item: ComputerListItem; index: number }]
}>()

function isKnownBlock(block: ComputerBlock | { type: string }): block is ComputerBlock {
  return (
    block.type === 'markdown' ||
    block.type === 'note' ||
    block.type === 'list' ||
    block.type === 'media' ||
    block.type === 'section'
  )
}

const visibleBlocks = computed(() => {
  const blocks: ComputerBlock[] = []
  for (const block of props.document.blocks) {
    if (isKnownBlock(block)) {
      blocks.push(block)
      continue
    }
    console.warn('[ComputerRenderer] skip unknown block type:', (block as { type: string }).type)
  }
  return blocks
})

const documentTitle = computed(() => props.document.title)
/** Hide protocol placeholder `ready`; show any other status as-is. */
const documentStatus = computed(() => {
  const raw = props.document.status?.trim()
  if (!raw || raw === 'ready') return undefined
  return raw
})

const showStoryboard = computed(() => hasStoryboardDoc(props.document))

const storyboardDoc = computed(() => (
  showStoryboard.value ? toStoryboardDoc(visibleBlocks.value) : null
))

const storyboardBeats = computed(() => {
  const doc = storyboardDoc.value
  if (!doc) return []
  return buildStoryboardBeats(doc.frames, doc.framePromptsSummary)
})

const titleLen = computed(() => (storyboardDoc.value?.detailTitle || '').length)
const bodyOverLimit = computed(() => (storyboardDoc.value?.detailBody || '').length > 2000)

const resolvedFileName = computed(() => {
  const override = (props.fileName || '').trim()
  if (override) return override
  if (showStoryboard.value && storyboardDoc.value?.detailTitle) {
    return defaultGitFileName(storyboardDoc.value.detailTitle)
  }
  return defaultGitFileName(documentTitle.value)
})

const fileMeta = computed(() => {
  if (showStoryboard.value) {
    return `${titleLen.value} 字标题`
  }
  return documentStatus.value || ''
})

const leadText = computed(() => {
  const n = storyboardBeats.value.length
  return n > 0 ? `上架素材 · ${n} 镜分镜` : '上架素材'
})

function isOrderedList(block: Extract<ComputerBlock, { type: 'list' }>): boolean {
  return block.ordered !== false
}

function tagPillClass(tag: ComputerTag): string[] {
  return ['dim-pill', `tone-${tag.tone || 'neutral'}`]
}

function isPriceLine(line: ComputerListLine): boolean {
  return line.emphasis === 'price'
}

function displayLines(lines: ComputerListLine[] | undefined): ComputerListLine[] {
  return lines?.length ? lines : []
}

function lineClass(line: ComputerListLine): string[] {
  const classes = ['item-line']
  if (line.label) {
    classes.push('has-label')
  }
  return classes
}

function priceLine(lines: ComputerListLine[] | undefined): ComputerListLine | undefined {
  return displayLines(lines).find((l) => isPriceLine(l))
}

function factLines(lines: ComputerListLine[] | undefined): ComputerListLine[] {
  return displayLines(lines).filter((l) => !isPriceLine(l))
}

function noteText(block: ComputerNoteBlock): string {
  return block.text
}

const PRIORITY_TITLE_MARKS = ['【优先试】', '【优先发】'] as const

function stripPriorityTitleMark(title: string): string {
  for (const mark of PRIORITY_TITLE_MARKS) {
    if (title.startsWith(mark)) {
      return title.slice(mark.length)
    }
  }
  return title
}

function hasPriorityTitleMark(title: string): boolean {
  return PRIORITY_TITLE_MARKS.some((mark) => title.startsWith(mark))
}

function itemBadge(item: { badge?: string; title: string }): string | undefined {
  const raw = item.badge?.trim()
  if (raw) {
    if (raw === 'priority') {
      return props.priorityBadgeLabel
    }
    return raw
  }
  if (hasPriorityTitleMark(item.title)) {
    return props.priorityBadgeLabel
  }
  return undefined
}

function itemHref(item: { href?: string }): string | undefined {
  return sanitizeHttpsHref(item.href)
}

function itemTitle(item: { badge?: string; title: string }): string {
  if (item.badge || hasPriorityTitleMark(item.title)) {
    return stripPriorityTitleMark(item.title)
  }
  return item.title
}

function showItemAction(): boolean {
  return Boolean(props.itemActionLabel?.trim())
}

function itemActionEnabled(item: ComputerListItem, itemIndex: number): boolean {
  return props.isItemActionEnabled(item, itemIndex)
}

function emitItemAction(item: ComputerListItem, itemIndex: number) {
  if (!itemActionEnabled(item, itemIndex)) return
  emit('item-action', { item, index: itemIndex })
}

function mediaPlanText(block: Extract<ComputerBlock, { type: 'media' }>): string {
  return (block.placeholder || block.alt || '').trim()
}
</script>

<template>
  <GitView
    :file-name="resolvedFileName"
    :meta="fileMeta"
    :aria-label="showStoryboard ? '上架素材预览' : undefined"
  >
    <template v-if="showStoryboard && storyboardDoc">
      <h1>{{ storyboardDoc.detailTitle || '上架素材' }}</h1>
      <p class="lead">{{ leadText }}</p>

      <div v-if="storyboardBeats.length" class="thumbs" aria-hidden="true">
        <div v-for="(_, i) in storyboardBeats.slice(0, 3)" :key="i" class="thumb">
          <span>{{ String(i + 1).padStart(2, '0') }}</span>
        </div>
      </div>

      <template v-if="storyboardBeats.length">
        <h2>主图分镜</h2>
        <ol class="shots">
          <li v-for="(beat, i) in storyboardBeats" :key="i">
            <p class="shot-t">{{ beat.caption }}</p>
            <pre v-if="beat.prompt" class="prompt">{{ beat.prompt }}</pre>
            <p v-if="beat.negative" class="neg">negative: {{ beat.negative }}</p>
          </li>
        </ol>
      </template>

      <h2>详情文案</h2>
      <h3 class="listing-copy is-title" :class="{ warn: titleLen > 60 }">
        {{ storyboardDoc.detailTitle || '—' }}
      </h3>
      <p
        class="body listing-copy is-body section-body"
        :class="{ warn: bodyOverLimit }"
      >
        {{ storyboardDoc.detailBody || '—' }}
      </p>

      <template v-if="storyboardDoc.displayNotes">
        <h2>展示说明</h2>
        <p class="body">{{ storyboardDoc.displayNotes }}</p>
      </template>
    </template>

    <template v-else>
      <h1 v-if="documentTitle">{{ documentTitle }}</h1>
      <template v-for="(block, index) in visibleBlocks" :key="index">
        <MarkdownView
          v-if="block.type === 'markdown'"
          class="cv-markdown"
          :source="block.text"
        />
        <p
          v-else-if="block.type === 'note'"
          class="cv-note"
          :class="{ mute: block.tone === 'mute' }"
        >
          {{ noteText(block) }}
        </p>

        <component
          :is="isOrderedList(block) ? 'ol' : 'ul'"
          v-else-if="block.type === 'list'"
          class="pick-list"
        >
          <li v-for="(item, itemIndex) in block.items" :key="item.title + '-' + itemIndex">
            <div class="item-body">
              <div class="item-top">
                <div class="t">
                  <span v-if="itemBadge(item)" class="priority-tag">{{ itemBadge(item) }}</span>
                  <a
                    v-if="itemHref(item)"
                    class="item-title-link"
                    :href="itemHref(item)"
                    target="_blank"
                    rel="noopener noreferrer"
                  >{{ itemTitle(item) }}</a>
                  <template v-else>{{ itemTitle(item) }}</template>
                  <button
                    v-if="showItemAction()"
                    type="button"
                    class="item-action-btn"
                    :disabled="!itemActionEnabled(item, itemIndex)"
                    @click="emitItemAction(item, itemIndex)"
                  >
                    {{ itemActionLabel }}
                  </button>
                </div>
                <div v-if="priceLine(item.lines)" class="item-price">{{ priceLine(item.lines)?.text }}</div>
              </div>
              <div v-if="factLines(item.lines).length" class="item-lines">
                <div
                  v-for="(line, lineIndex) in factLines(item.lines)"
                  :key="lineIndex"
                  :class="lineClass(line)"
                >
                  <span v-if="line.label" class="item-line-label">{{ line.label }}</span>
                  <span class="item-line-text">{{ line.text }}</span>
                </div>
              </div>
              <div v-if="item.tags?.length" class="dims">
                <span
                  v-for="(tag, tagIndex) in item.tags"
                  :key="tagIndex"
                  :class="tagPillClass(tag)"
                >{{ displayTagText(tag) }}</span>
              </div>
            </div>
          </li>
        </component>

        <div v-else-if="block.type === 'media'" class="cv-media">
          <div
            v-if="mediaPlanText(block)"
            class="cv-media-frame cv-media-plan-card"
          >
            <div class="cv-media-meta">
              <span class="cv-media-kicker">主图方案</span>
              <span
                v-if="block.src || block.mediaObjectId"
                class="cv-media-chip"
              >占位已挂载</span>
            </div>
            <p class="cv-media-plan">{{ mediaPlanText(block) }}</p>
          </div>
          <img
            v-else-if="block.src"
            class="cv-media-frame cv-media-img"
            :src="block.src"
            :alt="block.alt || ''"
          />
          <div v-else class="cv-media-frame cv-media-empty" aria-hidden="true">
            主图位
          </div>
        </div>

        <div
          v-else-if="block.type === 'section'"
          class="listing-copy"
          :class="storyboardSectionClass(block.heading, block.tone)"
        >
          <h4>{{ block.heading }}</h4>
          <p class="section-body">{{ block.body }}</p>
        </div>
      </template>
    </template>
  </GitView>
</template>

<style scoped>
.cv-markdown {
  margin: 0 0 10px;
}

.cv-note {
  margin: 0 0 10px;
  font-size: 0.75rem;
  color: var(--ink);
  line-height: 1.5;
}

.cv-note.mute {
  color: var(--mute);
}

.pick-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.pick-list li {
  display: block;
  padding: 14px 0;
  border-bottom: 1px solid var(--line-2);
}

.pick-list li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.item-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.item-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
}

.pick-list .t {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 10px;
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ink);
  min-width: 0;
  flex: 1;
}

.pick-list .item-title-link {
  color: inherit;
  text-decoration: none;
}

.pick-list .item-title-link:hover {
  text-decoration: underline;
}

.item-action-btn {
  flex-shrink: 0;
  appearance: none;
  border: 1px solid color-mix(in srgb, #0f766e 45%, transparent);
  margin: 0;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1.35;
  color: #0f766e;
  background: color-mix(in srgb, #0f766e 10%, var(--surface, #fff));
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.item-action-btn:hover:not(:disabled) {
  background: color-mix(in srgb, #0f766e 18%, var(--surface, #fff));
  border-color: color-mix(in srgb, #0f766e 70%, transparent);
  color: #0b5f58;
}

.item-action-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  color: var(--mute, #737373);
  border-color: var(--line);
  background: var(--surface, #fff);
}

.item-price {
  flex-shrink: 0;
  font-size: 0.8rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  white-space: nowrap;
}

.item-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-line {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
  font-size: 0.78rem;
  line-height: 1.5;
}

.item-line-label {
  font-size: 0.7rem;
  color: var(--mute-2, #a3a3a3);
}

.item-line-text {
  color: var(--mute);
  word-break: break-word;
}

.priority-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  font-size: 0.68rem;
  font-weight: 650;
  color: #7c4a1e;
  background: #f3e8d8;
  border-radius: 4px;
  border: 0;
  vertical-align: 1px;
  line-height: 1.35;
}

.dims {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 0.68rem;
  line-height: 1.4;
}

.dim-pill {
  display: inline-flex;
  align-items: center;
  border: 0;
  border-radius: 999px;
  padding: 2px 8px;
  font-weight: 600;
}

.dim-pill.tone-neutral {
  color: #57534e;
  background: #f5f5f4;
}

.dim-pill.tone-positive {
  color: #166534;
  background: #ecfdf3;
}

.dim-pill.tone-caution {
  color: #9a3412;
  background: #fff7ed;
}

.dim-pill.tone-danger {
  color: #9f1239;
  background: #fff1f2;
}

.dim-pill.tone-info {
  color: #1e3a8a;
  background: #eff6ff;
}

.dim-pill.tone-safe {
  color: #115e59;
  background: #ecfdf5;
}

.cv-media {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 4px;
}

.cv-media-frame {
  width: 100%;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background:
    radial-gradient(ellipse 80% 55% at 20% 0%, rgba(15, 23, 42, 0.04), transparent 55%),
    linear-gradient(165deg, #f4f6f8 0%, #e8ecf1 100%);
  color: var(--mute);
}

.cv-media-plan-card {
  aspect-ratio: auto;
  min-height: 148px;
  padding: 14px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-sizing: border-box;
}

.cv-media-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cv-media-kicker {
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  color: #475569;
}

.cv-media-chip {
  flex-shrink: 0;
  font-size: 0.65rem;
  font-weight: 600;
  color: #166534;
  background: #ecfdf3;
  border-radius: 999px;
  padding: 2px 8px;
}

.cv-media-plan {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 500;
  line-height: 1.55;
  color: var(--ink);
  word-break: break-word;
}

.cv-media-img {
  display: block;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  object-position: center;
  background: #f1f5f9;
  padding: 0;
}

.cv-media-empty {
  aspect-ratio: 4 / 3;
  display: grid;
  place-items: center;
  font-size: 0.8125rem;
  font-weight: 600;
}

.listing-copy {
  padding: 12px 0 4px;
  border-top: 1px solid var(--line-2);
}

.listing-copy:first-child {
  border-top: 0;
  padding-top: 0;
}

.listing-copy h4 {
  margin: 0 0 6px;
  font-size: 0.68rem;
  font-weight: 650;
  color: var(--mute-2, #a3a3a3);
  letter-spacing: 0.04em;
}

.listing-copy .section-body {
  margin: 0;
  color: var(--ink);
  word-break: break-word;
}

.listing-copy.is-title .section-body {
  font-size: 1.02rem;
  font-weight: 650;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.listing-copy.is-body .section-body {
  font-size: 0.875rem;
  font-weight: 450;
  line-height: 1.65;
  color: color-mix(in srgb, var(--ink) 88%, transparent);
}

.listing-copy.is-notes {
  margin-top: 4px;
  padding: 12px 12px 14px;
  border-top: 0;
  border-radius: var(--r-md);
  background: #f8fafc;
  border: 1px solid var(--line-2);
}

.listing-copy.is-notes h4 {
  color: #64748b;
}

.listing-copy.is-notes .section-body {
  font-size: 0.8rem;
  font-weight: 400;
  line-height: 1.6;
  color: var(--mute);
}
</style>
