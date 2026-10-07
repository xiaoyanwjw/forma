import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import { resumeGenerationRun } from '@/api/business/agent/agent'

describe('resumeGenerationRun', () => {
  afterEach(() => {
    clearToken()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('POSTs resume SSE with toolCallId and optionId', async () => {
    setToken('jwt-demo')
    const sse =
      'event: run_started\ndata: {"runId":"r1"}\n\n' +
      'event: artifact_ready\ndata: {"artifactRef":"sku-1","view":{"version":1,"blocks":[]}}\n\n' +
      'event: run_settled\ndata: {"runId":"r1","amount":1}\n\n'
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
    vi.stubGlobal('EventSource', vi.fn())

    const names: string[] = []
    for await (const event of resumeGenerationRun({
      runId: 'r1',
      toolCallId: 'ask-1',
      optionId: 'confirm_execute',
    })) {
      names.push(event.name)
    }

    expect(names).toEqual(['run_started', 'artifact_ready', 'run_settled'])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/agent/runs/r1/resume')
    expect(init.method).toBe('POST')
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer jwt-demo')
    expect(headers.get('Accept')).toBe('text/event-stream')
    expect(JSON.parse(String(init.body))).toEqual({
      toolCallId: 'ask-1',
      optionId: 'confirm_execute',
    })
  })
})
