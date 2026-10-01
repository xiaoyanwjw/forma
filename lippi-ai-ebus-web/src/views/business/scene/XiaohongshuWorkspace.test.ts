import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import XiaohongshuWorkspace from '@/views/business/scene/XiaohongshuWorkspace.vue'

const sessionCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'ecommerceWorkspaceSession.css'),
  'utf8',
)

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

const XHS = {
  bizId: 'a1000001-0001-4000-8000-000000000003',
  sceneCode: 'xiaohongshu',
  displayName: '小红书种草',
  status: 'AVAILABLE',
  sortOrder: 3,
  summary: '笔记结构与种草表达',
}

const TOPIC_TEMPLATE = '请帮我生成「厨房收纳」类小红书种草选题清单，面向租房党。'
const NOTE_TEMPLATE = '请为商品「硅胶沥水垫」写一篇小红书种草笔记，语气像真人分享。'
const BREAK_TEMPLATE =
  '请拆解下面这篇笔记（分享链接或正文），并改写成我的商品「硅胶沥水垫」：…'

function sampleTopiclistView() {
  return {
    version: 1,
    title: '选题清单',
    status: 'settled',
    blocks: [
      {
        type: 'note',
        text: '非实时平台全站行情',
        tone: 'mute',
      },
      {
        type: 'list',
        ordered: true,
        items: [
          {
            id: 'tp-1',
            href: 'https://www.xiaohongshu.com/explore/abc',
            badge: 'priority',
            title: '租房党厨房收纳第一篇',
            lines: [
              { kind: 'hook', text: '台面永远堆碗' },
              { kind: 'angle', text: '租房收纳' },
            ],
          },
        ],
      },
    ],
  }
}

function okScenes(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function creditsResponse() {
  return new Response(
    JSON.stringify({
      success: true,
      code: 0,
      message: 'ok',
      data: { tier: 'FREE', balance: 20, available: 14, reserved: 0 },
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  )
}

function sseBody(chunks: string[]) {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      const enc = new TextEncoder()
      for (const c of chunks) {
        controller.enqueue(enc.encode(c))
      }
      controller.close()
    },
  })
}

function mockCatalogAndCredits() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes')) {
      return okScenes([XHS])
    }
    if (url.includes('/api/v1/sessions/') && url.includes('/messages')) {
      return okScenes({ items: [], nextToken: null })
    }
    if (url.includes('/api/v1/sessions/') && url.includes('/latest-artifact')) {
      return okScenes(null)
    }
    if (url.includes('/api/v1/sessions')) {
      return okScenes([])
    }
    if (url.includes('/api/v1/credits')) {
      return creditsResponse()
    }
    if (url.includes('/api/v1/feedbacks')) {
      return okScenes(null)
    }
    if (url.includes('/api/v1/agent/runs')) {
      const body = typeof init?.body === 'string' ? init.body : ''
      const skillId = body.includes('xhs-note')
        ? 'xhs-note'
        : body.includes('xhs-break')
          ? 'xhs-break'
          : 'xhs-topiclist'
      const view =
        skillId === 'xhs-topiclist'
          ? sampleTopiclistView()
          : {
              version: 1,
              title: skillId === 'xhs-note' ? '笔记种草稿' : '爆文拆解',
              status: 'settled',
              blocks: [{ type: 'markdown', text: '## 草稿\n正文' }],
            }
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r1","sessionId":"s-xhs","holdId":"h1"}\n\n',
          `event: artifact_ready\ndata: ${JSON.stringify({ artifactRef: `${skillId}-1`, view })}\n\n`,
          `event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"${skillId}-1","amount":1}\n\n`,
        ]),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      )
    }
    void init
    return new Response('not found', { status: 404 })
  })
}

async function mountWorkspace() {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: { template: '<div />' } },
      { path: '/scenes', name: 'scenes', component: { template: '<div>gallery</div>' } },
      {
        path: '/scenes/xiaohongshu',
        name: 'scene-xiaohongshu',
        component: XiaohongshuWorkspace,
      },
      { path: '/history', name: 'history', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push({ name: 'scene-xiaohongshu' })
  await router.isReady()
  const app = createApp(XiaohongshuWorkspace)
  app.use(router)
  app.mount(root)
  await flushUi()
  return {
    root,
    router,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

type FetchSpy = { mock: { calls: ReadonlyArray<unknown[]> } }

function billedRunApiHits(fetchMock: FetchSpy, skillId?: string) {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const u = String(input)
    if (!u.includes('/api/v1/agent/runs') || u.includes('/runs/empty')) {
      return false
    }
    const body =
      typeof (init as RequestInit | undefined)?.body === 'string'
        ? String((init as RequestInit).body)
        : ''
    if (body.includes('"dryRun":true')) return false
    if (skillId) return body.includes(`"skillId":"${skillId}"`)
    return true
  })
}

describe('XiaohongshuWorkspace', () => {
  let unmount: (() => void) | undefined
  let fetchMock: ReturnType<typeof mockCatalogAndCredits>
  let styleEl: HTMLStyleElement | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    fetchMock = mockCatalogAndCredits()
    styleEl = document.createElement('style')
    styleEl.textContent = sessionCss
    document.head.appendChild(styleEl)
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    styleEl?.remove()
    clearToken()
  })

  it('shows three capsules and binds sceneCode xiaohongshu', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount

    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe(
      'xiaohongshu',
    )
    const pills = Array.from(
      mounted.root.querySelectorAll<HTMLButtonElement>('[data-testid="session-quick-row"] .pill'),
    )
    expect(pills.map((p) => p.textContent?.trim())).toEqual([
      '选题清单',
      '笔记种草稿',
      '爆文拆解',
    ])
  })

  it('fills sendable example prompts when capsules are clicked', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const area = () =>
      mounted.root.querySelector('textarea[aria-label="继续提问"]') as HTMLTextAreaElement
    const pills = mounted.root.querySelectorAll<HTMLButtonElement>(
      '[data-testid="session-quick-row"] .pill',
    )
    const callsBefore = fetchMock.mock.calls.length

    pills[0]!.click()
    await flushUi()
    expect(area().value).toBe(TOPIC_TEMPLATE)
    expect(area().value).not.toMatch(/【占位】/)

    pills[1]!.click()
    await flushUi()
    expect(area().value).toBe(NOTE_TEMPLATE)

    pills[2]!.click()
    await flushUi()
    expect(area().value).toBe(BREAK_TEMPLATE)

    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
  })

  it('posts sceneCode xiaohongshu and skillId xhs-topiclist when sending topic capsule', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const pills = mounted.root.querySelectorAll<HTMLButtonElement>(
      '[data-testid="session-quick-row"] .pill',
    )
    pills[0]!.click()
    await flushUi()
    const send = mounted.root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    send.click()
    await flushUi()
    await flushUi()

    const hits = billedRunApiHits(fetchMock, 'xhs-topiclist')
    expect(hits.length).toBeGreaterThanOrEqual(1)
    const body = JSON.parse(String((hits[0] as [string, RequestInit])[1].body)) as {
      sceneCode?: string
      skillId?: string
      text?: string
    }
    expect(body.sceneCode).toBe('xiaohongshu')
    expect(body.skillId).toBe('xhs-topiclist')
    expect(body.text).toBe(TOPIC_TEMPLATE)
  })

  it('posts xhs-note and xhs-break skillIds for the other capsules', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const pills = mounted.root.querySelectorAll<HTMLButtonElement>(
      '[data-testid="session-quick-row"] .pill',
    )
    const send = () =>
      mounted.root.querySelector('.session button[aria-label="发送"]') as HTMLButtonElement

    pills[1]!.click()
    await flushUi()
    send().click()
    await flushUi()
    await flushUi()
    const noteBody = JSON.parse(
      String((billedRunApiHits(fetchMock, 'xhs-note').at(-1) as [string, RequestInit])[1].body),
    ) as { skillId?: string; sceneCode?: string }
    expect(noteBody.skillId).toBe('xhs-note')
    expect(noteBody.sceneCode).toBe('xiaohongshu')

    pills[2]!.click()
    await flushUi()
    send().click()
    await flushUi()
    await flushUi()
    const breakBody = JSON.parse(
      String((billedRunApiHits(fetchMock, 'xhs-break').at(-1) as [string, RequestInit])[1].body),
    ) as { skillId?: string }
    expect(breakBody.skillId).toBe('xhs-break')
  })

  it('topiclist 写成笔记 starts xhs-note with tp-n in the same session', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const pills = mounted.root.querySelectorAll<HTMLButtonElement>(
      '[data-testid="session-quick-row"] .pill',
    )
    pills[0]!.click()
    await flushUi()
    ;(mounted.root.querySelector('.session button[aria-label="发送"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const btn = mounted.root.querySelector('.note-handoff-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.disabled).toBe(false)
    btn.click()
    await flushUi()
    await flushUi()

    const hits = billedRunApiHits(fetchMock, 'xhs-note')
    expect(hits.length).toBeGreaterThanOrEqual(1)
    const body = JSON.parse(String((hits.at(-1) as [string, RequestInit])[1].body)) as {
      text?: string
      sessionId?: string
      skillId?: string
      sceneCode?: string
    }
    expect(body.skillId).toBe('xhs-note')
    expect(body.sceneCode).toBe('xiaohongshu')
    expect(body.sessionId).toBe('s-xhs')
    expect(body.text).toMatch(/tp-1/)
  })
})
