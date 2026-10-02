import { ref, type Ref, type ComputedRef } from 'vue'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'

export function useChatConsoleExpand(opts: {
  generationRunning: Ref<boolean> | ComputedRef<boolean>
  thinkingMessageId: Ref<string | null>
}) {
  const expandedStreamIds = ref(new Set<string>())
  const expandedStatusIds = ref(new Set<string>())
  const expandedProcessLogIds = ref(new Set<string>())

  function isProcessLogLive(m: WorkspaceChatMessage): boolean {
    return Boolean(opts.generationRunning.value && m.id === opts.thinkingMessageId.value)
  }

  function shouldShowProcessEvents(m: WorkspaceChatMessage): boolean {
    if (!m.processEvents?.length) return false
    if (isProcessLogLive(m) || m.failed) return true
    return expandedProcessLogIds.value.has(m.id)
  }

  function canToggleProcessLog(m: WorkspaceChatMessage): boolean {
    return Boolean(m.processEvents?.length) && !m.failed && !isProcessLogLive(m)
  }

  function toggleProcessLog(m: WorkspaceChatMessage) {
    const next = new Set(expandedProcessLogIds.value)
    if (next.has(m.id)) next.delete(m.id)
    else next.add(m.id)
    expandedProcessLogIds.value = next
  }

  function toggleStreamExpand(id: string) {
    const next = new Set(expandedStreamIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    expandedStreamIds.value = next
  }

  function toggleStatusExpand(id: string) {
    const next = new Set(expandedStatusIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    expandedStatusIds.value = next
  }

  function canExpandStatus(m: WorkspaceChatMessage): boolean {
    return Boolean(m.failed && (m.statusDetail || m.text))
  }

  function resetConsoleExpand() {
    expandedStreamIds.value = new Set()
    expandedStatusIds.value = new Set()
    expandedProcessLogIds.value = new Set()
  }

  return {
    expandedStreamIds,
    expandedStatusIds,
    expandedProcessLogIds,
    isProcessLogLive,
    shouldShowProcessEvents,
    canToggleProcessLog,
    toggleProcessLog,
    toggleStreamExpand,
    toggleStatusExpand,
    canExpandStatus,
    resetConsoleExpand,
  }
}
