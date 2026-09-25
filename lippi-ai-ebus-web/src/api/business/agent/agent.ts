import { ApiError } from '@/api/client'
import { getToken } from '@/api/http'
import type { Ad4EventName, Ad4SseEvent } from '@/types/business/agent'
import { isAd4EventName } from '@/types/business/agent'

const EMPTY_RUN_PATH = '/api/v1/agent/runs/empty'

export interface StreamEmptyRunOptions {
  sessionId?: string
  signal?: AbortSignal
}

/**
 * 启动空跑 SSE：fetch + ReadableStream + JWT（禁止 EventSource）。
 * 不经 {@code request().json()}。
 */
export async function* streamEmptyRun(
  options: StreamEmptyRunOptions = {},
): AsyncGenerator<Ad4SseEvent, void, undefined> {
  const token = getToken()
  if (!token) {
    throw new ApiError(401, '未授权，请先登录')
  }

  const url = options.sessionId
    ? `${EMPTY_RUN_PATH}?sessionId=${encodeURIComponent(options.sessionId)}`
    : EMPTY_RUN_PATH

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
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
