import { ApiError } from '@/api/client'
import { getToken } from '@/api/http'
import type {
  Ad4EventName,
  Ad4SseEvent,
  ResumeGenerationRunOptions,
  StreamAgentRunOptions,
  StreamListingRunOptions,
  StreamPicklistRunOptions,
} from '@/types/business/agent'
import { isAd4EventName } from '@/types/business/agent'

const AGENT_RUN_PATH = '/api/v1/agent/runs'

/**
 * 通用 Generation Run SSE（计费 Skill / 无 skill 对话）。
 */
export async function* streamAgentRun(
  options: StreamAgentRunOptions,
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  const sceneId = options.sceneId?.trim()
  const sceneCode = options.sceneCode?.trim()
  if (!sceneId && !sceneCode) {
    throw new ApiError(400, '请先选择场景')
  }
  const text = options.text?.trim()
  if (!text) {
    throw new ApiError(400, '请先描述需求')
  }

  const token = getToken()
  if (!token) {
    throw new ApiError(401, '未授权，请先登录')
  }

  const response = await fetch(AGENT_RUN_PATH, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      text,
      sessionId: options.sessionId?.trim() || undefined,
      sceneId: sceneId || undefined,
      sceneCode: sceneCode || undefined,
      skillId: options.skillId?.trim() || undefined,
    }),
    signal: options.signal,
  })

  if (!response.ok) {
    throw await readApiError(response)
  }
  if (!response.body) {
    throw new ApiError(response.status, '服务未返回事件流')
  }

  yield* parseSseStream(response.body)
}

/**
 * 计费选品：走通用 /runs（ecommerce-picklist）。
 */
export async function* streamPicklistRun(
  options: StreamPicklistRunOptions,
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  yield* streamAgentRun({
    ...options,
    skillId: 'ecommerce-picklist',
  })
}

/**
 * 计费 Listing：走通用 /runs（ecommerce-skulist）。
 */
export async function* streamListingRun(
  options: StreamListingRunOptions,
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  yield* streamAgentRun({
    ...options,
    skillId: 'ecommerce-skulist',
  })
}

/**
 * ask_human 续跑：POST /runs/{runId}/resume，返回新 SSE。
 */
export async function* resumeGenerationRun(
  options: ResumeGenerationRunOptions,
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  const runId = options.runId?.trim()
  const toolCallId = options.toolCallId?.trim()
  if (!runId) {
    throw new ApiError(400, '缺少 runId')
  }
  if (!toolCallId) {
    throw new ApiError(400, '缺少 toolCallId')
  }

  const token = getToken()
  if (!token) {
    throw new ApiError(401, '未授权，请先登录')
  }

  const response = await fetch(`${AGENT_RUN_PATH}/${encodeURIComponent(runId)}/resume`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      toolCallId,
      optionId: options.optionId?.trim() || undefined,
      freeText: options.freeText?.trim() || undefined,
      confirmId: options.confirmId?.trim() || undefined,
    }),
    signal: options.signal,
  })

  if (!response.ok) {
    throw await readApiError(response)
  }
  if (!response.body) {
    throw new ApiError(response.status, '服务未返回事件流')
  }

  yield* parseSseStream(response.body)
}

async function readApiError(response: Response): Promise<ApiError> {
  try {
    const payload = (await response.json()) as { code?: number; message?: string }
    return new ApiError(
      payload.code || response.status,
      payload.message || '请求失败',
    )
  } catch {
    return new ApiError(response.status, '请求失败')
  }
}

async function* parseSseStream(
  body: ReadableStream<Uint8Array>,
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName: string | null = null
  let dataLines: string[] = []

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        if (line === '') {
          if (eventName != null) {
            const event = toAd4Event(eventName, dataLines.join('\n'))
            eventName = null
            dataLines = []
            if (event) {
              yield event
            }
          }
          continue
        }
        if (line.startsWith(':')) {
          continue
        }
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
    }

    if (eventName != null) {
      const event = toAd4Event(eventName, dataLines.join('\n'))
      if (event) {
        yield event
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function toAd4Event(name: string, rawData: string): Ad4SseEvent | null {
  if (!isAd4EventName(name)) {
    return null
  }
  let data: Record<string, unknown> = {}
  if (rawData) {
    try {
      const parsed = JSON.parse(rawData) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        data = parsed as Record<string, unknown>
      } else {
        data = { value: parsed }
      }
    } catch {
      data = { raw: rawData }
    }
  }
  return { name: name as Ad4EventName, data }
}
