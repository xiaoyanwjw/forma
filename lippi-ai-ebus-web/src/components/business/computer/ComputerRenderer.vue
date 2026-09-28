<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  sanitizeHttpsHref,
} from '@/types/business/computerView'
import ListingPlatformPreview from './ListingPlatformPreview.vue'
import {
  LISTING_PLATFORM_SKINS,
  type ListingPlatformSkin,
} from './listingPlatform'

const props = defineProps<{
  document: ComputerDocument
}>()

const platformSkin = ref<ListingPlatformSkin>('adam')

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
/** Hide protocol placeholder `ready`; keep demo / settled labels. */
const documentStatus = computed(() => {
  const raw = props.document.status?.trim()
  if (!raw || raw === 'ready') return undefined
  return resolveComputerStatus(raw)
})

/** 上架素材预览才显示平台样式切换（选品 list 文档不显示） */
const isListingPreview = computed(() => {
  const key = props.document.title?.trim() || ''
  if (key === 'picklist' || key === 'report') return false
  if (key === 'listingPreview') return true
  if (/上架|listing/i.test(documentTitle.value)) return true
  const hasPickList = props.document.blocks.some((b) => b.type === 'list')
  if (hasPickList) return false
  const hasHero = props.document.blocks.some((b) => b.type === 'media')
  const hasListingCopy = props.document.blocks.some(
    (b) =>
      b.type === 'section' &&
      (b.heading === '详情标题' || b.heading === '详情正文' || b.heading === '展示说明'),
  )
  return hasHero && hasListingCopy
})

const platformSkins = LISTING_PLATFORM_SKINS

watch(
  () => props.document.title,
  () => {
    platformSkin.value = 'adam'
  },
)

/** 从双轨 blocks 抽出上架公共文案；切换平台只换预览壳 */
const listingContent = computed(() => {
  let heroPlan = ''
  let heroMounted = false
  let detailTitle = ''
  let detailBody = ''
  let displayNotes = ''
  let framePromptsSummary = ''
  const frames: string[] = []
  for (const block of visibleBlocks.value) {
    if (block.type === 'media') {
      heroPlan = mediaPlanText(block)
      heroMounted = Boolean(block.src || block.mediaObjectId)
    } else if (block.type === 'list' && isOrderedList(block)) {
      for (const item of block.items) {
        const title = (item.title || '').trim()
        if (title) frames.push(title)
      }
    } else if (block.type === 'section') {
      if (block.heading === '详情标题') detailTitle = block.body
      else if (block.heading === '详情正文') detailBody = block.body
      else if (block.heading === '展示说明') displayNotes = block.body
      else if (block.heading === '生图 Prompt') framePromptsSummary = block.body
    }
  }
  return {
    heroPlan,
    heroMounted,
    detailTitle,
    detailBody,
    displayNotes,
    frames,
    framePromptsSummary,
  }
})

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

function itemHref(item: { href?: string }): string | undefined {
  return sanitizeHttpsHref(item.href)
}

function itemTitle(item: { badge?: string; title: string }): string {
  if (item.badge || item.title.startsWith(PRIORITY_MARK)) {
    return item.title.startsWith(PRIORITY_MARK)
      ? item.title.slice(PRIORITY_MARK.length)
      : item.title
  }
  return item.title
}

function mediaPlanText(block: Extract<ComputerBlock, { type: 'media' }>): string {
  return (block.placeholder || block.alt || '').trim()
}
</script>

<template>
  <article class="comp-card">
    <div class="comp-card-head">
      <span class="comp-card-title">{{ documentTitle }}</span>
      <div class="comp-card-head-actions">
        <div
          v-if="isListingPreview"
          class="platform-switch"
          role="tablist"
          aria-label="预览平台效果"
        >
          <button
            v-for="skin in platformSkins"
            :key="skin.id"
            type="button"
            class="platform-btn"
            :class="[`platform-${skin.id}`, { active: platformSkin === skin.id }]"
            role="tab"
            :aria-selected="platformSkin === skin.id"
            :title="`${skin.label}效果`"
            :aria-label="`${skin.label}浏览器预期效果`"
            @click="platformSkin = skin.id"
          >
            <span class="platform-mark" aria-hidden="true">{{ skin.mark }}</span>
          </button>
        </div>
        <span v-else-if="documentStatus" class="status">{{ documentStatus }}</span>
      </div>
    </div>
    <div
      class="comp-card-body"
      :class="{ 'is-plat': isListingPreview && platformSkin !== 'adam' }"
    >
      <ListingPlatformPreview
        v-if="isListingPreview"
        :platform="platformSkin"
        :content="listingContent"
      />
      <template v-else>
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
                    <a
                      v-if="itemHref(item)"
                      class="item-title-link"
                      :href="itemHref(item)"
                      target="_blank"
                      rel="noopener noreferrer"
                    >{{ itemTitle(item) }}</a>
                    <template v-else>{{ itemTitle(item) }}</template>
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
            <div
              v-if="mediaPlanText(block)"
              class="listing-hero listing-hero-plan-card"
            >
              <div class="listing-hero-meta">
                <span class="listing-hero-kicker">主图方案</span>
                <span
                  v-if="block.src || block.mediaObjectId"
                  class="listing-hero-chip"
                >占位已挂载</span>
              </div>
              <p class="listing-hero-plan">{{ mediaPlanText(block) }}</p>
            </div>
            <img
              v-else-if="block.src"
              class="listing-hero listing-hero-img"
              :src="block.src"
              :alt="block.alt || ''"
            />
            <div v-else class="listing-hero listing-hero-empty" aria-hidden="true">
              主图位
            </div>
          </div>

          <div
            v-else-if="block.type === 'section'"
            class="listing-copy"
            :class="{
              'is-notes': block.tone === 'mute' || block.heading === '展示说明',
              'is-title': block.heading === '详情标题',
              'is-body': block.heading === '详情正文' || (block.heading !== '详情标题' && block.tone !== 'mute'),
            }"
          >
            <h4>{{ block.heading }}</h4>
            <p class="section-body">{{ block.body }}</p>
          </div>
        </template>
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
  padding: 10px 12px 10px 14px;
  border-bottom: 1px solid var(--line-2);
  font-size: 0.8125rem;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.comp-card-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comp-card-head-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comp-card-head .status {
  font-weight: 500;
  color: var(--mute);
  font-size: 0.75rem;
}

.platform-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 999px;
  background: var(--line-2, #f1f1f1);
}

.platform-btn {
  appearance: none;
  border: 0;
  margin: 0;
  padding: 0;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  cursor: pointer;
  background: transparent;
  color: var(--mute);
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.platform-btn:hover {
  background: rgba(255, 255, 255, 0.7);
  color: var(--ink);
}

.platform-btn.active {
  background: #fff;
  color: var(--ink);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.12);
}

.platform-mark {
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1;
}

.platform-btn.platform-taobao.active {
  color: #ff5000;
}

.platform-btn.platform-xianyu.active {
  color: #ffe60f;
  background: #1f2937;
}

.platform-btn.platform-douyin.active {
  color: #fff;
  background: #111;
  box-shadow: none;
}

.platform-btn.platform-adam.active {
  color: #0f172a;
}

.comp-card-body {
  padding: 14px;
}

.comp-card-body.is-plat {
  padding: 12px 12px 14px;
  background: #f3f4f6;
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

.pick-list .item-title-link {
  color: inherit;
  text-decoration: none;
}

.pick-list .item-title-link:hover {
  text-decoration: underline;
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
  gap: 12px;
  margin-bottom: 4px;
}

.listing-hero {
  width: 100%;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background:
    radial-gradient(ellipse 80% 55% at 20% 0%, rgba(15, 23, 42, 0.04), transparent 55%),
    linear-gradient(165deg, #f4f6f8 0%, #e8ecf1 100%);
  color: var(--mute);
}

.listing-hero-plan-card {
  aspect-ratio: auto;
  min-height: 148px;
  padding: 14px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-sizing: border-box;
}

.listing-hero-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.listing-hero-kicker {
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  color: #475569;
}

.listing-hero-chip {
  flex-shrink: 0;
  font-size: 0.65rem;
  font-weight: 600;
  color: #166534;
  background: #ecfdf3;
  border-radius: 999px;
  padding: 2px 8px;
}

.listing-hero-plan {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 500;
  line-height: 1.55;
  color: var(--ink);
  word-break: break-word;
}

.listing-hero-img {
  display: block;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  object-position: center;
  background: #f1f5f9;
  padding: 0;
}

.listing-hero-empty {
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
