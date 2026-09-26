<script setup lang="ts">
import { computed } from 'vue'
import type { ComputerBlock, ComputerDocument } from '@/types/business/computerView'

const props = defineProps<{
  document: ComputerDocument
}>()

function isKnownBlock(block: ComputerBlock | { type: string }): block is ComputerBlock {
  return (
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

function padIndex(index: number): string {
  return String(index + 1).padStart(2, '0')
}

function isOrderedList(block: Extract<ComputerBlock, { type: 'list' }>): boolean {
  return block.ordered !== false
}
</script>

<template>
  <article class="comp-card">
    <div class="comp-card-head">
      <span>{{ document.title }}</span>
      <span v-if="document.status" class="status">{{ document.status }}</span>
    </div>
    <div class="comp-card-body">
      <template v-for="(block, index) in visibleBlocks" :key="index">
        <p
          v-if="block.type === 'note'"
          class="cv-note"
          :class="{ mute: block.tone === 'mute' }"
        >
          {{ block.text }}
        </p>

        <component
          :is="isOrderedList(block) ? 'ol' : 'ul'"
          v-else-if="block.type === 'list'"
          class="pick-list"
        >
          <li v-for="(item, itemIndex) in block.items" :key="item.title + '-' + itemIndex">
            <span class="n">{{ padIndex(itemIndex) }}</span>
            <div>
              <div class="t">
                <span v-if="item.badge" class="priority-tag">{{ item.badge }}</span>
                {{ item.title }}
              </div>
              <div v-for="(line, lineIndex) in item.lines || []" :key="lineIndex" class="r">
                {{ line }}
              </div>
              <div v-if="item.tags?.length" class="dims">
                <span v-for="(tag, tagIndex) in item.tags" :key="tagIndex">{{ tag }}</span>
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
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-2);
  align-items: start;
}

.pick-list li:last-child {
  border-bottom: 0;
}

.pick-list .n {
  font-size: 0.75rem;
  color: var(--mute);
  font-variant-numeric: tabular-nums;
  padding-top: 2px;
}

.pick-list .t {
  font-size: 0.875rem;
  font-weight: 600;
}

.pick-list .r {
  font-size: 0.8rem;
  color: var(--mute);
  margin-top: 2px;
}

.priority-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--ink);
  background: color-mix(in srgb, var(--accent, #c45c26) 18%, transparent);
  border-radius: 4px;
  vertical-align: 1px;
}

.dims {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 6px;
  font-size: 0.72rem;
  color: var(--mute);
  line-height: 1.4;
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
