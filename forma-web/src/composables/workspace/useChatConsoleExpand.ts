import { ref, type Ref, type ComputedRef } from 'vue'
import type { WorkspaceChatMessage } from '@/types/business/workspaceChat'

export function useChatConsoleExpand(opts: {
  generationRunning: Ref<boolean> | ComputedRef<boolean>
  thinkingMessageId: Ref<string | null>
}) {
  const expandedStreamIds = ref(new Set<string>())
  const expandedStatusIds = ref(new Set<string>())
  /** 完成后：用户主动展开过程 */
  const expandedProcessLogIds = ref(new Set<string>())
  /** 运行中：用户主动展开过程（默认收起） */
  const expandedLiveProcessIds = ref(new Set<string>())

  function isProcessLogLive(m: WorkspaceChatMessage): boolean {
    return Boolean(opts.generationRunning.value && m.id === opts.thinkingMessageId.value)
  }

  function isProcessLogExpanded(m: WorkspaceChatMessage): boolean {
    if (!m.processEvents?.length) return false
    if (isProcessLogLive(m)) return expandedLiveProcessIds.value.has(m.id)
    return expandedProcessLogIds.value.has(m.id)
  }

  function shouldShowProcessEvents(m: WorkspaceChatMessage): boolean {
    return isProcessLogExpanded(m)
  }

  function canToggleProcessLog(m: WorkspaceChatMessage): boolean {
    return Boolean(m.processEvents?.length)
  }

  function toggleProcessLog(m: WorkspaceChatMessage) {
    if (isProcessLogLive(m)) {
      const next = new Set(expandedLiveProcessIds.value)
      if (next.has(m.id)) next.delete(m.id)
      else next.add(m.id)
      expandedLiveProcessIds.value = next
      return
    }
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
    expandedLiveProcessIds.value = new Set()
  }

  return {
    expandedStreamIds,
    expandedStatusIds,
    expandedProcessLogIds,
    expandedLiveProcessIds,
    isProcessLogLive,
    isProcessLogExpanded,
    shouldShowProcessEvents,
    canToggleProcessLog,
    toggleProcessLog,
    toggleStreamExpand,
    toggleStatusExpand,
    canExpandStatus,
    resetConsoleExpand,
  }
}
