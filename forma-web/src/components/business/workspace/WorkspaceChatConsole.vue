<script setup lang="ts">
import {
  canExpandProcessEvent,
  formatEventTime,
  formatStreamBodyForDisplay,
  processEventDisplayLabel,
  type ProcessEvent,
} from '@/composables/agent/agentProgress'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'

const props = defineProps<{
  message: WorkspaceChatMessage
  isRunning: boolean
  showProcessEvents: boolean
  canToggleProcess: boolean
  processExpanded: boolean
  expandedStreamIds: Set<string>
  expandedStatusIds: Set<string>
  canPreview: boolean
  previewOpen: boolean
  canExpandFail: boolean
}>()

const emit = defineEmits<{
  toggleProcess: []
  processEventClick: [event: ProcessEvent]
  statusClick: []
}>()

function eventTag(kind: ProcessEvent['kind']): string {
  if (kind === 'agent') return 'AGENT'
  if (kind === 'llm') return 'LLM'
  return 'TOOL'
}

function eventTitle(e: ProcessEvent): string {
  return processEventDisplayLabel(e.title)
}

function liveWorkingLabel(): string {
  const events = m().processEvents
  if (!events?.length) return 'Working'
  return processEventDisplayLabel(events[events.length - 1]!.title)
}

const m = () => props.message
</script>

<template>
  <div
    class="chat-console"
    aria-label="运行日志"
    :aria-busy="isRunning ? 'true' : undefined"
  >
    <div
      v-if="isRunning"
      class="chat-event chat-event-status is-working"
      data-testid="working-status"
      aria-disabled="true"
    >
      <span class="chat-event-time">{{
        formatEventTime(
          m().processEvents?.[m().processEvents.length - 1]?.at ??
            m().at ??
            Date.now(),
        )
      }}</span>
      <span class="chat-event-tag tag-status">RUN</span>
      <div class="chat-event-main">
        <div class="chat-event-head">
          <span class="chat-event-title chat-result-text">{{ liveWorkingLabel() }}</span>
        </div>
      </div>
    </div>
    <div
      v-else-if="m().text"
      class="chat-event chat-event-status"
      :class="{
        'is-preview': canPreview,
        'is-failed': canExpandFail,
        open: previewOpen || (canExpandFail && expandedStatusIds.has(m().id)),
        expandable: canPreview || canExpandFail,
      }"
      role="button"
      tabindex="0"
      :aria-disabled="!(canPreview || canExpandFail)"
      :aria-expanded="
        canPreview
          ? previewOpen
          : canExpandFail
            ? expandedStatusIds.has(m().id)
            : undefined
      "
      @click="emit('statusClick')"
      @keydown.enter.prevent="emit('statusClick')"
    >
      <span class="chat-event-time">{{
        formatEventTime(
          m().at ??
            m().processEvents?.[m().processEvents.length - 1]?.at ??
            Date.now(),
        )
      }}</span>
      <span class="chat-event-tag tag-status">{{ canExpandFail ? 'FAIL' : 'OK' }}</span>
      <div class="chat-event-main">
        <div class="chat-event-head">
          <span class="chat-event-title chat-result-text">{{ m().text }}</span>
          <span v-if="canPreview" class="chat-stream-toggle">{{
            previewOpen ? '关闭' : '查看'
          }}</span>
          <span v-else-if="canExpandFail" class="chat-stream-toggle">{{
            expandedStatusIds.has(m().id) ? '[-]' : '[+]'
          }}</span>
        </div>
      </div>
      <pre
        v-if="canExpandFail && expandedStatusIds.has(m().id)"
        class="chat-stream-body"
        @click.stop
      >{{ formatStreamBodyForDisplay(m().statusDetail || m().text) }}</pre>
    </div>
    <div
      v-if="canToggleProcess || $slots['after-status']"
      class="chat-console-meta"
      data-testid="chat-console-meta"
    >
      <button
        v-if="canToggleProcess"
        type="button"
        class="chat-process-toggle"
        :class="{ 'is-breathing': isRunning }"
        data-testid="toggle-process-log"
        :aria-label="processExpanded ? '收起执行过程' : '执行过程'"
        :title="processExpanded ? '收起执行过程' : '执行过程'"
        :aria-expanded="processExpanded"
        @click="emit('toggleProcess')"
      >
        执行过程 <span class="chev" aria-hidden="true">›</span>
      </button>
      <div v-if="$slots['after-status']" class="chat-console-actions">
        <slot name="after-status" />
      </div>
    </div>
    <div
      v-if="showProcessEvents"
      class="chat-events"
      aria-label="过程事件"
      data-testid="process-events"
    >
      <div
        v-for="e in m().processEvents"
        :key="e.id"
        class="chat-event"
        :class="{
          active: e.kind === 'tool' && !e.done,
          'chat-event-llm': e.kind === 'llm',
          'chat-event-tool': e.kind === 'tool',
          open: canExpandProcessEvent(e) && expandedStreamIds.has(e.id),
          expandable: canExpandProcessEvent(e),
        }"
        role="button"
        tabindex="0"
        @click="emit('processEventClick', e)"
        @keydown.enter.prevent="emit('processEventClick', e)"
      >
        <span class="chat-event-time">{{ formatEventTime(e.at) }}</span>
        <span
          class="chat-event-tag"
          :class="{
            'tag-agent': e.kind === 'agent',
            'tag-llm': e.kind === 'llm',
            'tag-tool': e.kind === 'tool',
          }"
        >{{ eventTag(e.kind) }}</span>
        <div class="chat-event-main">
          <div
            class="chat-event-head"
            :aria-expanded="
              canExpandProcessEvent(e) ? expandedStreamIds.has(e.id) : undefined
            "
          >
            <span class="chat-event-title">{{ eventTitle(e) }}</span>
            <span v-if="canExpandProcessEvent(e)" class="chat-stream-toggle">{{
              expandedStreamIds.has(e.id) ? '[-]' : '[+]'
            }}</span>
            <span
              v-else-if="e.kind === 'tool' && !e.done"
              class="chat-stream-toggle"
            >…</span>
          </div>
        </div>
        <pre
          v-if="canExpandProcessEvent(e) && expandedStreamIds.has(e.id)"
          class="chat-stream-body"
          @click.stop
        >{{ formatStreamBodyForDisplay(e.body || '') }}</pre>
      </div>
    </div>
  </div>
</template>
