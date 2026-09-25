import { onUnmounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { streamEmptyRun } from '@/api/business/agent/agent'
import type { Ad4EventName, Ad4SseEvent } from '@/types/business/agent'

/**
 * 空跑试跑：发起 SSE 并收集 AD-4 事件名列表。
 */
export function useAgentEmptyRun() {
  const running = ref(false)
  const error = ref('')
  const events = ref<Ad4SseEvent[]>([])
  const eventNames = ref<Ad4EventName[]>([])
  let abortController: AbortController | null = null

  function abort() {
    abortController?.abort()
    abortController = null
  }

  onUnmounted(() => {
    abort()
  })

  async function startEmptyRun(sessionId?: string) {
    if (running.value) {
      return
    }
    abort()
    abortController = new AbortController()
    const signal = abortController.signal
    running.value = true
    error.value = ''
    events.value = []
    eventNames.value = []
    try {
      for await (const event of streamEmptyRun({ sessionId, signal })) {
        events.value = [...events.value, event]
        eventNames.value = [...eventNames.value, event.name]
      }
    } catch (e) {
      if (signal.aborted) {
        return
      }
      error.value = e instanceof ApiError ? e.message : '空跑失败'
    } finally {
      running.value = false
    }
  }

  return {
    running,
    error,
    events,
    eventNames,
    startEmptyRun,
    abort,
  }
}
