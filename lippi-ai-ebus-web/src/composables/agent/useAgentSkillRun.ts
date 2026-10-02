import { onUnmounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { streamAgentRun } from '@/api/business/agent/agent'
import type {
  Ad4EventName,
  Ad4SseEvent,
  GenerationArtifactPayload,
  StreamAgentRunOptions,
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
 * 通用计费 Skill SSE（小红书三胶囊共用；无 HITL）。
 */
export function useAgentSkillRun() {
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

  async function startSkillRun(
    options: StreamAgentRunOptions & { text: string; skillId?: string },
  ) {
    if (running.value) {
      return
    }
    const text = options.text?.trim()
    const skillId = options.skillId?.trim() || undefined
    if (!text) {
      error.value = '请先描述需求'
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
      for await (const event of streamAgentRun({
        ...options,
        text,
        skillId,
        dryRun: false,
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
          error.value = typeof reason === 'string' && reason.trim() ? reason.trim() : '生成失败'
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
      error.value = e instanceof ApiError ? e.message : '生成失败'
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
    startSkillRun,
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
