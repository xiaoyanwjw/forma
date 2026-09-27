import { onUnmounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { streamPicklistRun } from '@/api/business/agent/agent'
import type {
  Ad4EventName,
  Ad4SseEvent,
  PicklistArtifactPayload,
  StreamPicklistRunOptions,
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

const CREDITS_CHANGED_EVENT = 'ebus:credits-changed'

/**
 * 计费选品：发起 SSE，收集事件，成功时解析 artifact_ready 成果。
 */
export function useAgentPicklistRun() {
  const running = ref(false)
  const error = ref('')
  const events = ref<Ad4SseEvent[]>([])
  const eventNames = ref<Ad4EventName[]>([])
  const artifact = ref<PicklistArtifactPayload | null>(null)
  const sessionId = ref<string | null>(null)
  const processEvents = ref<ProcessEvent[]>([])
  let abortController: AbortController | null = null
  /** Bumped on reset/newTask so late SSE events are ignored. */
  let runGeneration = 0

  function abort() {
    abortController?.abort()
    abortController = null
  }

  /** Abort in-flight run and clear session/artifact/error (新任务). */
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

  async function startPicklistRun(options: StreamPicklistRunOptions) {
    if (running.value) {
      return
    }
    const text = options.text?.trim()
    if (!text) {
      error.value = '请先描述选品需求'
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
      for await (const event of streamPicklistRun({
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
          artifact.value = toPicklistArtifact(event.data)
        }
        if (event.name === 'run_failed') {
          const reason = event.data.reason
          error.value = typeof reason === 'string' && reason.trim() ? reason.trim() : '选品生成失败'
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
      error.value = e instanceof ApiError ? e.message : '选品生成失败'
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
    startPicklistRun,
    abort,
    reset,
  }
}

export { CREDITS_CHANGED_EVENT }

function toPicklistArtifact(data: Record<string, unknown>): PicklistArtifactPayload | null {
  const itemsRaw = data.items
  if (!Array.isArray(itemsRaw) || itemsRaw.length === 0) {
    return null
  }
  const items = itemsRaw
    .map((row) => {
      if (!row || typeof row !== 'object') {
        return null
      }
      const o = row as Record<string, unknown>
      return {
        title: str(o.title),
        priceBand: str(o.priceBand),
        painPoint: str(o.painPoint),
        angle: str(o.angle),
        diff: str(o.diff),
        niche: str(o.niche),
        demand: str(o.demand),
        competition: str(o.competition),
        margin: str(o.margin),
        risk: str(o.risk),
      }
    })
    .filter((x): x is NonNullable<typeof x> => x != null && Boolean(x.title))

  if (items.length === 0) {
    return null
  }

  return {
    picklistId: str(data.picklistId) || str(data.artifactRef),
    runId: str(data.runId),
    templateId: str(data.templateId),
    disclaimer: str(data.disclaimer),
    assumptions: str(data.assumptions) || undefined,
    items,
    view: parseComputerDocument(data.view) ?? undefined,
  }
}

function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}
