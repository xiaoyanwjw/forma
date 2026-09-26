<script setup lang="ts">
import { computed } from 'vue'
import type { Scene } from '@/types/business/scene'

const props = defineProps<{
  scene: Scene
  /** Available card navigates here; ignored for COMING_SOON */
  to?: { name: string } | string
}>()

const emit = defineEmits<{
  comingSoon: [scene: Scene]
}>()

const isLive = computed(() => props.scene.status === 'AVAILABLE')

function onComingSoonClick() {
  emit('comingSoon', props.scene)
}
</script>

<template>
  <RouterLink
    v-if="isLive && to"
    class="scene-card live"
    role="listitem"
    :to="to"
  >
    <div class="scene-card-top">
      <span class="scene-icon" aria-hidden="true">
        <slot name="icon" />
      </span>
      <span class="badge live">可用</span>
    </div>
    <h2>{{ scene.displayName }}</h2>
    <p>{{ scene.summary }}</p>
  </RouterLink>

  <div
    v-else-if="isLive"
    class="scene-card live inert"
    role="listitem"
    aria-disabled="true"
  >
    <div class="scene-card-top">
      <span class="scene-icon" aria-hidden="true">
        <slot name="icon" />
      </span>
      <span class="badge live">可用</span>
    </div>
    <h2>{{ scene.displayName }}</h2>
    <p>{{ scene.summary }}</p>
  </div>

  <button
    v-else
    type="button"
    class="scene-card soon"
    role="listitem"
    aria-disabled="true"
    :aria-label="`${scene.displayName}，即将推出`"
    @click="onComingSoonClick"
  >
    <div class="scene-card-top">
      <span class="scene-icon" aria-hidden="true">
        <slot name="icon" />
      </span>
      <span class="badge">即将推出</span>
    </div>
    <h2>{{ scene.displayName }}</h2>
    <p>{{ scene.summary }}</p>
    <span class="scene-card-cta">即将推出</span>
  </button>
</template>

<style scoped>
.scene-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: 20px;
  min-height: 156px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
  color: inherit;
  font: inherit;
}

a.scene-card.live {
  cursor: pointer;
}

a.scene-card.live:hover {
  border-color: #d4d4d4;
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.scene-card.live.inert {
  cursor: default;
  pointer-events: none;
}

.scene-card.soon {
  opacity: 0.58;
  filter: grayscale(0.25);
  cursor: pointer;
}

.scene-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.scene-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--line-2);
  border: 1px solid var(--line);
  display: grid;
  place-items: center;
  color: var(--ink);
}

.badge {
  font-size: 0.7rem;
  font-weight: 550;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--mute);
  background: var(--canvas);
}

.badge.live {
  color: var(--ok);
  border-color: var(--ok-line);
  background: var(--ok-bg);
}

.scene-card h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.scene-card p {
  margin: 0;
  color: var(--mute);
  font-size: 0.875rem;
  line-height: 1.45;
  flex: 1;
}

.scene-card-cta {
  font-size: 0.8125rem;
  font-weight: 550;
  color: var(--mute-2);
}
</style>
