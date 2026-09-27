<script setup lang="ts">
import { computed } from 'vue'
import type {
  ComputerBlock,
  ComputerDocument,
  ComputerListLine,
  ComputerNoteBlock,
  ComputerTag,
} from '@/types/business/computerView'
import {
  resolveComputerBadge,
  resolveComputerStatus,
  resolveComputerTitle,
  resolveLineLabel,
  resolveNoteText,
  resolveTagDisplay,
} from '@/types/business/computerView'

const props = defineProps<{
  document: ComputerDocument
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

const documentTitle = computed(() => resolveComputerTitle(props.document.title))
const documentStatus = computed(() =>
  props.document.status ? resolveComputerStatus(props.document.status) : undefined,
)

function isOrderedList(block: Extract<ComputerBlock, { type: 'list' }>): boolean {
  return block.ordered !== false
}

function tagPillClass(tag: ComputerTag): string[] {
  return ['dim-pill', `tone-${tag.tone || 'neutral'}`]
}

function isPriceLine(line: ComputerListLine): boolean {
  return line.emphasis === 'price' || line.kind === 'priceBand' || resolveLineLabel(line) === '价格带'
}

/** Resolve locale labels for protocol lines; no domain string parsing. */
function displayLines(lines: ComputerListLine[] | undefined): ComputerListLine[] {
  if (!lines?.length) return []
  return lines.map((line) => {
    const resolvedLabel = resolveLineLabel(line)
    return resolvedLabel ? { ...line, label: resolvedLabel } : { ...line }
  })
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
  return resolveNoteText(block)
}

const PRIORITY_MARK = '【优先试】'

function itemBadge(item: { badge?: string; title: string }): string | undefined {
  if (item.badge) {
    return resolveComputerBadge(item.badge)
  }
  if (item.title.startsWith(PRIORITY_MARK)) {
    return resolveComputerBadge('priority')
  }
  return undefined
}

function itemTitle(item: { badge?: string; title: string }): string {
  if (item.badge || item.title.startsWith(PRIORITY_MARK)) {
    return item.title.startsWith(PRIORITY_MARK)
      ? item.title.slice(PRIORITY_MARK.length)
      : item.title
  }
  return item.title
}
</script>

<template>
  <article class="comp-card">
    <div class="comp-card-head">
      <span>{{ documentTitle }}</span>
      <span v-if="documentStatus" class="status">{{ documentStatus }}</span>
    </div>
    <div class="comp-card-body">
      <template v-for="(block, index) in visibleBlocks" :key="index">
        <div
          v-if="block.type === 'markdown'"
          class="cv-markdown"
        >{{ block.text }}</div>
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
                  {{ itemTitle(item) }}
                </div>
                <div v-if="priceLine(item.lines)" class="item-price">{{ priceLine(item.lines)?.text }}</div>
              </div>
              <div v-if="factLines(item.lines).length" class="item-lines">
                <div
                  v-for="(line, lineIndex) in factLines(item.lines)"
                  :key="lineIndex"
                  :class="lineClass(line)"
                >
                  <span class="item-line-label">{{ line.label || '说明' }}</span>
                  <span class="item-line-text">{{ line.text }}</span>
                </div>
              </div>
              <div v-if="item.tags?.length" class="dims">
                <span
                  v-for="(tag, tagIndex) in item.tags"
                  :key="tagIndex"
                  :class="tagPillClass(tag)"
                >{{ resolveTagDisplay(tag) }}</span>
              </div>
            </div>
          </li>
        </component>

        <div v-else-if="block.type === 'media'" class="cv-media">
          <img
            v-if="block.src"
            class="listing-hero listing-hero-img"
            :src="block.src"
            :alt="block.alt || block.placeholder || ''"
          />
          <div v-else class="listing-hero" aria-hidden="true">
            {{ block.placeholder }}
          </div>
        </div>

        <div v-else-if="block.type === 'section'" class="listing-copy">
          <h4>{{ block.heading }}</h4>
          <p
            class="section-body"
            :class="{ mute: block.tone === 'mute' }"
          >
            {{ block.body }}
          </p>
        </div>
      </template>
    </div>
  </article>
</template>

<style scoped>
.comp-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.comp-card-head {
  padding: 12px 14px;
  border-bottom: 1px solid var(--line-2);
  font-size: 0.8125rem;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.comp-card-head .status {
  font-weight: 500;
  color: var(--mute);
  font-size: 0.75rem;
}

.comp-card-body {
  padding: 14px;
}

.cv-markdown {
  margin: 0 0 10px;
  font-size: 0.8125rem;
  color: var(--ink);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
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
  /* Override any page-level legacy grid (28px index column) */
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
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ink);
  min-width: 0;
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

.item-line.is-reason {
  align-items: start;
}

.item-line-label {
  font-size: 0.7rem;
  color: var(--mute-2, #a3a3a3);
}

.item-line-text {
  color: var(--mute);
  word-break: break-word;
}

.item-line.is-reason .item-line-text {
  color: var(--ink);
  opacity: 0.82;
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
  gap: 14px;
}

.listing-hero {
  aspect-ratio: 4 / 3;
  width: 100%;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background:
    radial-gradient(ellipse 70% 50% at 50% 78%, rgba(0, 0, 0, 0.07), transparent),
    linear-gradient(180deg, #f8f8f8, #ececec);
  display: grid;
  place-items: center;
  color: var(--mute);
  font-size: 0.8125rem;
  font-weight: 600;
}

.listing-hero-img {
  object-fit: cover;
  padding: 0;
}

.listing-copy h4 {
  margin: 0 0 4px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--mute);
  letter-spacing: 0.02em;
}

.listing-copy .section-body {
  margin: 0 0 12px;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--ink);
  line-height: 1.45;
}

.listing-copy .section-body.mute {
  font-size: 0.82rem;
  font-weight: normal;
  color: var(--mute);
  line-height: 1.55;
}
</style>
