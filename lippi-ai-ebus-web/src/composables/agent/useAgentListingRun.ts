import { onUnmounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { resumeGenerationRun, streamListingRun } from '@/api/business/agent/agent'
import type {
  Ad4EventName,
  Ad4SseEvent,
  AskHumanOption,
  GenerationArtifactPayload,
  HumanInputRequiredPayload,
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

const DEFAULT_ASK_OPTIONS: AskHumanOption[] = [
  { id: 'confirm_execute', label: '确认，出执行稿' },
  { id: 'supplement', label: '补充需求' },
]

/**
 * 计费 Listing：首段 SSE 可在 human_input_required 结束；resume 再读新 SSE。
 * 同 Run 两次 artifact_ready 时保留最新 view。
 */
export function useAgentListingRun() {
  const running = ref(false)
  const error = ref('')
  const events = ref<Ad4SseEvent[]>([])
  const eventNames = ref<Ad4EventName[]>([])
  const artifact = ref<GenerationArtifactPayload | null>(null)
  const sessionId = ref<string | null>(null)
  const runId = ref<string | null>(null)
  const pendingHuman = ref<HumanInputRequiredPayload | null>(null)
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
    runId.value = null
    pendingHuman.value = null
    processEvents.value = []
  }

  onUnmounted(() => {
    abort()
  })

  function applyEvent(event: Ad4SseEvent) {
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
      const rid = event.data.runId
      if (typeof rid === 'string' && rid.trim()) {
        runId.value = rid.trim()
      }
    }
    if (event.name === 'artifact_ready') {
      const next = toGenerationArtifact(event.data)
      if (next) {
        artifact.value = next
        window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
      }
    }
    if (event.name === 'human_input_required') {
      const prompt = toHumanInputRequired(event.data, runId.value)
      if (prompt) {
        pendingHuman.value = prompt
        if (prompt.runId) {
          runId.value = prompt.runId
        }
      }
    }
    if (event.name === 'run_failed') {
      const reason = event.data.reason
      error.value = typeof reason === 'string' && reason.trim() ? reason.trim() : '上架素材生成失败'
      pendingHuman.value = null
      window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
    }
    if (event.name === 'run_settled') {
      pendingHuman.value = null
      window.dispatchEvent(new CustomEvent(CREDITS_CHANGED_EVENT))
    }
  }

  async function consumeStream(stream: AsyncGenerator<Ad4SseEvent, void, undefined>, generation: number) {
    for await (const event of stream) {
      if (generation !== runGeneration) {
        return
      }
      applyEvent(event)
    }
  }

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
    pendingHuman.value = null
    processEvents.value = []

    try {
      await consumeStream(
        streamListingRun({
          ...options,
          text,
          sessionId: options.sessionId ?? sessionId.value ?? undefined,
          signal,
        }),
        generation,
      )
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

  async function resumeListingRun(options: { optionId?: string; freeText?: string }) {
    const prompt = pendingHuman.value
    const resumeRunId = (prompt?.runId || runId.value || '').trim()
    const toolCallId = (prompt?.toolCallId || '').trim()
    if (running.value || !resumeRunId || !toolCallId) {
      return
    }

    abort()
    abortController = new AbortController()
    const signal = abortController.signal
    const generation = ++runGeneration
    running.value = true
    error.value = ''
    pendingHuman.value = null

    try {
      await consumeStream(
        resumeGenerationRun({
          runId: resumeRunId,
          toolCallId,
          optionId: options.optionId,
          freeText: options.freeText,
          signal,
        }),
        generation,
      )
    } catch (e) {
      if (signal.aborted || generation !== runGeneration) {
        return
      }
      error.value = e instanceof ApiError ? e.message : '上架素材续跑失败'
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
    runId,
    pendingHuman,
    processEvents,
    startListingRun,
    resumeListingRun,
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

function toHumanInputRequired(
  data: Record<string, unknown>,
  fallbackRunId: string | null,
): HumanInputRequiredPayload | null {
  const toolCallId = typeof data.toolCallId === 'string' ? data.toolCallId.trim() : ''
  if (!toolCallId) return null
  const question = typeof data.question === 'string' ? data.question.trim() : ''
  const runFromEvent = typeof data.runId === 'string' ? data.runId.trim() : ''
  return {
    question:
      question ||
      '策划分镜已出。确认后将生成执行稿与生图 Prompt（再扣 1 积分）。也可补充需求让我改策划。',
    options: parseAskHumanOptions(data.options),
    allowFreeText: data.allowFreeText !== false,
    toolCallId,
    runId: runFromEvent || fallbackRunId || undefined,
  }
}

function parseAskHumanOptions(raw: unknown): AskHumanOption[] {
  if (!Array.isArray(raw)) {
    return DEFAULT_ASK_OPTIONS
  }
  const out: AskHumanOption[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const rec = item as Record<string, unknown>
    const id = typeof rec.id === 'string' ? rec.id.trim() : ''
    const label = typeof rec.label === 'string' ? rec.label.trim() : ''
    if (!id) continue
    out.push({ id, label: label || id })
  }
  return out.length ? out : DEFAULT_ASK_OPTIONS
}
