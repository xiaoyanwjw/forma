import { onUnmounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { streamListingRun } from '@/api/business/agent/agent'
import type {
  Ad4EventName,
  Ad4SseEvent,
  GenerationArtifactPayload,
  StreamListingRunOptions,
} from '@/types/business/agent'
import { parseComputerDocument } from '@/types/business/computerView'
import {
  applyAgentEnded,
  applyAgentStarted,
  applyMessageDelta,
  applyToolFinished,
  applyToolStarted,
  type ProcessEvent,
} from '@/composables/agent/agentProgress'
import { CREDITS_CHANGED_EVENT } from '@/composables/agent/useAgentPicklistRun'

/**
 * 计费 Listing：发起 SSE，收集事件，成功时解析 artifact_ready 成果。
 */
export function useAgentListingRun() {
  const running = ref(false)
  const error = ref('')
  const events = ref<Ad4SseEvent[]>([])
  const eventNames = ref<Ad4EventName[]>([])
  const artifact = ref<GenerationArtifactPayload | null>(null)
  const sessionId = ref<string | null>(null)
  const processEvents = ref<ProcessEvent[]>([])
  let abortController: AbortController | null = null
  let runGeneration = 0

  function abort() {
    abortController?.abort()
    abortController = null
  }

  function reset() {
    abort()
    runGeneration += 1
    running.value = false
    error.value = ''
    events.value = []
    eventNames.value = []
    artifact.value = null
    sessionId.value = null
    processEvents.value = []
  }

  onUnmounted(() => {
    abort()
  })

  async function startListingRun(options: StreamListingRunOptions) {
    if (running.value) {
      return
    }
    const text = options.text?.trim()
    if (!text) {
      error.value = '请先描述要上架的商品'
      return
    }

    abort()
    abortController = new AbortController()
    const signal = abortController.signal
    const generation = ++runGeneration
    running.value = true
    error.value = ''
    events.value = []
    eventNames.value = []
    artifact.value = null
    processEvents.value = []

    try {
      for await (const event of streamListingRun({
        ...options,
        text,
        sessionId: options.sessionId ?? sessionId.value ?? undefined,
        signal,
      })) {
        if (generation !== runGeneration) {
          return
        }
        events.value = [...events.value, event]
        eventNames.value = [...eventNames.value, event.name]

        if (event.name === 'agent_started') {
          processEvents.value = applyAgentStarted(processEvents.value, event.data)
        }
        if (event.name === 'agent_ended') {
          processEvents.value = applyAgentEnded(processEvents.value, event.data)
        }
        if (event.name === 'tool_started') {
          processEvents.value = applyToolStarted(processEvents.value, event.data)
        }
        if (event.name === 'tool_finished') {
          processEvents.value = applyToolFinished(processEvents.value, event.data)
        }
        if (event.name === 'message_delta') {
          processEvents.value = applyMessageDelta(processEvents.value, event.data)
        }
        if (event.name === 'run_started') {
          const sid = event.data.sessionId
          if (typeof sid === 'string' && sid.trim()) {
            sessionId.value = sid.trim()
          }
        }
        if (event.name === 'artifact_ready') {
          artifact.value = toGenerationArtifact(event.data)
        }
        if (event.name === 'run_failed') {
          const reason = event.data.reason
          error.value = typeof reason === 'string' && reason.trim() ? reason.trim() : '上架素材生成失败'
          window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
        }
        if (event.name === 'run_settled') {
          window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
        }
      }
    } catch (e) {
      if (signal.aborted || generation !== runGeneration) {
        return
      }
      error.value = e instanceof ApiError ? e.message : '上架素材生成失败'
      window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
    } finally {
      if (generation === runGeneration) {
        running.value = false
      }
    }
  }

  return {
    running,
    error,
    events,
    eventNames,
    artifact,
    sessionId,
    processEvents,
    startListingRun,
    abort,
    reset,
  }
}

function toGenerationArtifact(data: Record<string, unknown>): GenerationArtifactPayload | null {
  const view = parseComputerDocument(data.view)
  const artifactRef = typeof data.artifactRef === 'string' ? data.artifactRef.trim() : ''
  if (!view || !artifactRef) return null
  return { artifactRef, view }
}
