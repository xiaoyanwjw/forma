import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import { streamEmptyRun } from '@/api/business/agent/agent'
import { AD4_EVENT_NAMES, type Ad4SseEvent } from '@/types/business/agent'

describe('streamEmptyRun', () => {
  afterEach(() => {
    clearToken()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('throws 401 without JWT and does not call EventSource', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const EventSourceMock = vi.fn()
    vi.stubGlobal('EventSource', EventSourceMock)

    await expect(async () => {
      const gen = streamEmptyRun()
      await gen.next()
    }).rejects.toMatchObject({ code: 401 })

    expect(fetchMock).not.toHaveBeenCalled()
    expect(EventSourceMock).not.toHaveBeenCalled()
  })

  it('uses fetch + ReadableStream + Bearer JWT and parses AD-4 events', async () => {
    setToken('jwt-demo')
    const sse =
      'event: run_started\ndata: {"runId":"r1"}\n\n' +
      'event: message_delta\ndata: {"text":"hi"}\n\n' +
      'event: run_failed\ndata: {"reason":"空跑无可用成果，预占已释放","emptyRun":true}\n\n'

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sse))
        controller.close()
      },
    })

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const EventSourceMock = vi.fn()
    vi.stubGlobal('EventSource', EventSourceMock)

    const collected: Ad4SseEvent[] = []
    for await (const event of streamEmptyRun()) {
      collected.push(event)
      expect(AD4_EVENT_NAMES).toContain(event.name)
    }

    expect(collected.map((e) => e.name)).toEqual([
      'run_started',
      'message_delta',
      'run_failed',
    ])
    const failed = collected.find((e) => e.name === 'run_failed')
    expect(failed?.data.emptyRun).toBe(true)
    expect(failed?.data.reason).toBe('空跑无可用成果，预占已释放')
    expect(EventSourceMock).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/agent/runs/empty')
    expect(init.method).toBe('POST')
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer jwt-demo')
    expect(headers.get('Accept')).toBe('text/event-stream')
  })

  it('builds URL with sessionId query when provided', async () => {
    setToken('jwt-demo')
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode('event: run_failed\ndata: {"emptyRun":true,"reason":"x"}\n\n'),
        )
        controller.close()
      },
    })
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    for await (const _ of streamEmptyRun({ sessionId: 'sess-42' })) {
      // drain
    }

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toBe('/api/v1/agent/runs/empty?sessionId=sess-42')
  })

  it('maps insufficient credits JSON error without opening EventSource', async () => {
    setToken('jwt-demo')
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ success: false, code: 402, message: '积分不足' }), {
          status: 402,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('EventSource', vi.fn())

    await expect(async () => {
      const gen = streamEmptyRun()
      await gen.next()
    }).rejects.toMatchObject({ code: 402, message: '积分不足' })
  })
})
