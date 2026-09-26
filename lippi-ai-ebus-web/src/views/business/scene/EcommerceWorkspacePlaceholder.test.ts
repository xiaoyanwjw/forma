import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import EcommerceWorkspacePlaceholder from '@/views/business/scene/EcommerceWorkspacePlaceholder.vue'
import { DEMO_LISTING } from '@/views/business/scene/ecommerceDemoFixtures'

const sessionCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'ecommerceWorkspaceSession.css'),
  'utf8',
)

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

const ECOMMERCE = {
  bizId: 'a1000001-0001-4000-8000-000000000001',
  sceneCode: 'ecommerce',
  displayName: '电商开店',
  status: 'AVAILABLE',
  sortOrder: 1,
  summary: '选品与上架',
}

const SAMPLE_ITEMS = Array.from({ length: 8 }, (_, i) => ({
  title: `${i === 0 ? '【优先试】' : ''}候选${i + 1}`,
  priceBand: '19–39 元',
  reason: `痛点：场景不便；切入：刚需测款；差异：视觉点${i + 1}`,
  differentiation: `细分：细分${i % 3}；差异动作${i + 1}`,
  demand: '高｜需求稳',
  competition: '中｜可切入',
  margin: '中｜测款友好',
  risk: '低｜勿夸大',
}))

function okScenes(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function creditsResponse(available = 14) {
  return new Response(
    JSON.stringify({
      success: true,
      code: 0,
      message: 'ok',
      data: { tier: 'FREE', balance: 20, available, reserved: 0 },
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

function mockCatalogAndCredits(opts?: {
  onPicklist?: () => Response
  available?: number
}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes')) {
      return okScenes([ECOMMERCE])
    }
    if (url.includes('/api/v1/credits')) {
      return creditsResponse(opts?.available ?? 14)
    }
    if (url.includes('/api/v1/agent/runs/picklist')) {
      if (opts?.onPicklist) {
        return opts.onPicklist()
      }
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
          'event: tool_started\ndata: {"toolName":"read_skill"}\n\n',
          'event: tool_finished\ndata: {"toolName":"read_skill"}\n\n',
          `event: message_delta\ndata: {"text":"${'x'.repeat(130)}"}\n\n`,
          `event: artifact_ready\ndata: ${JSON.stringify({
            artifactType: 'picklist',
            picklistId: 'pl-1',
            runId: 'r1',
            templateId: 'domestic-generic-default',
            disclaimer: '基于通用电商知识推断，非实时平台数据',
            items: SAMPLE_ITEMS,
            view: {
              version: 1,
              title: '选品清单',
              status: '已结算',
              blocks: [
                {
                  type: 'note',
                  text: '基于通用电商知识推断，非实时平台数据',
                  tone: 'mute',
                },
                {
                  type: 'list',
                  ordered: true,
                  items: SAMPLE_ITEMS.map((it) => ({
                    badge: it.title.startsWith('【优先试】') ? '优先试' : undefined,
                    title: it.title.replace(/^【优先试】/, ''),
                    lines: [`价格带：${it.priceBand}`, it.reason, it.differentiation],
                    tags: [
                      `需求 ${it.demand}`,
                      `竞争 ${it.competition}`,
                      `利润 ${it.margin}`,
                      `风险 ${it.risk}`,
                    ],
                  })),
                },
              ],
            },
          })}\n\n`,
          'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
        ]),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      )
    }
    if (url.includes('/api/v1/agent/runs/empty')) {
      return new Response('should not empty', { status: 500 })
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
        path: '/scenes/ecommerce',
        name: 'scene-ecommerce',
        component: EcommerceWorkspacePlaceholder,
      },
      { path: '/history', name: 'history', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push({ name: 'scene-ecommerce' })
  await router.isReady()
  const app = createApp(EcommerceWorkspacePlaceholder)
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

function setTextareaValue(el: HTMLTextAreaElement, value: string) {
  const proto = window.HTMLTextAreaElement.prototype
  const desc = Object.getOwnPropertyDescriptor(proto, 'value')
  desc?.set?.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

function picklistApiHits(fetchMock: ReturnType<typeof vi.spyOn>) {
  return fetchMock.mock.calls.filter(([input]) => String(input).includes('/api/v1/agent/runs/picklist'))
}

function emptyRunApiHits(fetchMock: ReturnType<typeof vi.spyOn>) {
  return fetchMock.mock.calls.filter(([input]) => String(input).includes('/api/v1/agent/runs/empty'))
}

describe('EcommerceWorkspacePlaceholder default session shell', () => {
  let unmount: (() => void) | undefined
  let fetchMock: ReturnType<typeof mockCatalogAndCredits>

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    fetchMock = mockCatalogAndCredits()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    clearToken()
  })

  it('mounts session shell with empty thread, breadcrumb, no home, no secondary nav', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount

    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('h1')).toBeNull()
    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.sidebar')).toBeTruthy()
    expect(mounted.root.querySelector('.side-new')?.textContent).toContain('新任务')
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)

    const crumb = mounted.root.querySelector('.scene-switch')
    expect(crumb?.textContent).toContain('场景')
    expect(crumb?.textContent).toContain('电商开店')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')

    expect(mounted.root.querySelector('[data-nav="history"]')).toBeNull()
    expect(
      Array.from(mounted.root.querySelectorAll('a')).some((a) => a.textContent?.includes('套餐')),
    ).toBe(false)

    expect(picklistApiHits(fetchMock)).toHaveLength(0)
  })

  it('keeps attach disabled; session send enabled only with prompt text', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const send = mounted.root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    const attach = mounted.root.querySelector(
      '.session button[aria-label="附件"]',
    ) as HTMLButtonElement
    expect(attach.disabled).toBe(true)
    expect(send.disabled).toBe(true)

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    setTextareaValue(area, '帮我做家居选品')
    await flushUi()
    expect(send.disabled).toBe(false)
  })

  it('resolves scene bizId from catalog when available', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-biz-id')).toBe(
      ECOMMERCE.bizId,
    )
  })

  it('still shows session shell with sceneCode when catalog fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/scenes')) {
        return new Response(JSON.stringify({ success: false, code: 500, message: 'fail' }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/api/v1/credits')) {
        return creditsResponse()
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-biz-id')).toBeNull()
  })
})

describe('EcommerceWorkspacePlaceholder session shell (3.4 picklist)', () => {
  let unmount: (() => void) | undefined
  let fetchMock: ReturnType<typeof mockCatalogAndCredits>

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    fetchMock = mockCatalogAndCredits()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    clearToken()
  })

  async function enterViaSend(root: HTMLElement, text = '帮我做家居选品') {
    const area = root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    setTextareaValue(area, text)
    await flushUi()
    const send = root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    expect(send.disabled).toBe(false)
    send.click()
    await flushUi()
    await flushUi()
  }

  it('session picks capsule fills prompt without sending or generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length
    const pathBefore = mounted.router.currentRoute.value.fullPath

    const picks = mounted.root.querySelectorAll<HTMLButtonElement>('.chat-input-wrap .pill')[0]
    picks!.click()
    await flushUi()

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    expect(area.value).toMatch(/选品清单/)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
  })

  it('session listing capsule fills prompt without sending or generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount

    const listing = mounted.root.querySelectorAll<HTMLButtonElement>('.chat-input-wrap .pill')[1]
    listing!.click()
    await flushUi()

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    expect(area.value).toMatch(/上架素材/)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
  })

  it('send picklist intent streams billing SSE and opens Computer with live list', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const creditEvents: Event[] = []
    const onCredits = (e: Event) => creditEvents.push(e)
    window.addEventListener('ebus:credits-changed', onCredits)
    try {
      await enterViaSend(mounted.root)

      const hits = picklistApiHits(fetchMock)
      expect(hits.length).toBeGreaterThanOrEqual(1)
      const [url, init] = hits[0] as [string, RequestInit]
      expect(url).toBe('/api/v1/agent/runs/picklist')
      expect(init.method).toBe('POST')
      const body = JSON.parse(String(init.body)) as { text?: string; sceneCode?: string }
      expect(body.text).toBe('帮我做家居选品')
      expect(body.sceneCode).toBe('ecommerce')
      expect(emptyRunApiHits(fetchMock)).toHaveLength(0)
      expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(true)
      const computer = mounted.root.querySelector('.computer')
      expect(computer?.textContent).toContain("Adam's Computer")
      expect(computer?.textContent).toMatch(/选品清单/)
      expect(computer?.querySelector('article.comp-card')).toBeTruthy()
      expect(computer?.querySelectorAll('.comp-card').length).toBe(1)
      expect(computer?.querySelector('.cv-note')?.textContent).toMatch(/非实时/)
      expect(computer?.querySelectorAll('.pick-disclaimer').length).toBe(0)
      expect(computer?.querySelectorAll('.pick-list li').length).toBe(8)
      expect(computer?.textContent).toMatch(/需求 /)
      expect(computer?.textContent).toMatch(/优先试/)
      expect(computer?.querySelector('.priority-tag')).toBeTruthy()
      expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/已生成/)
      expect(mounted.root.querySelector('.chat-steps')?.textContent).toContain('read_skill')
      expect(mounted.root.querySelector('.chat-steps')?.textContent).toContain('✓')
      const streamBody = mounted.root.querySelector('.chat-stream-body')
      expect(streamBody?.textContent?.endsWith('…')).toBe(true)
      expect(streamBody?.textContent).toBe(`${'x'.repeat(120)}…`)
      const toggle = mounted.root.querySelector('.chat-stream-toggle') as HTMLButtonElement
      expect(toggle).toBeTruthy()
      expect(toggle.textContent).toContain('展开')
      toggle.click()
      await flushUi()
      expect(mounted.root.querySelector('.chat-stream-body')?.textContent).toBe('x'.repeat(130))
      expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('收起')
      expect(creditEvents.length).toBeGreaterThanOrEqual(1)
    } finally {
      window.removeEventListener('ebus:credits-changed', onCredits)
    }
  })

  it('short message_delta has no fold toggle', async () => {
    const shortText = 'x'.repeat(120)
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            `event: message_delta\ndata: {"text":"${shortText}"}\n\n`,
            `event: artifact_ready\ndata: ${JSON.stringify({
              artifactType: 'picklist',
              picklistId: 'pl-1',
              runId: 'r1',
              templateId: 'domestic-generic-default',
              disclaimer: '基于通用电商知识推断，非实时平台数据',
              items: SAMPLE_ITEMS,
            })}\n\n`,
            'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    expect(mounted.root.querySelector('.chat-stream-toggle')).toBeNull()
    expect(mounted.root.querySelector('.chat-stream-body')?.textContent).toBe(shortText)
  })

  it('does not hardcode picklist-specific step strings in chat source', () => {
    const vueSrc = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), 'EcommerceWorkspacePlaceholder.vue'),
      'utf8',
    )
    const runSrc = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), '../../../composables/agent/useAgentPicklistRun.ts'),
      'utf8',
    )
    const src = `${vueSrc}\n${runSrc}`
    expect(src).not.toMatch(/正在读取技能|解析选品|生成候选清单|调用选品工具/)
  })

  it('falls back to old pick-list layout when artifact has items but no view', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify({
              artifactType: 'picklist',
              picklistId: 'pl-1',
              runId: 'r1',
              templateId: 'domestic-generic-default',
              disclaimer: '基于通用电商知识推断，非实时平台数据',
              items: SAMPLE_ITEMS,
            })}\n\n`,
            'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    const computer = mounted.root.querySelector('.computer')
    expect(computer?.querySelector('.cv-note')).toBeNull()
    expect(computer?.querySelector('.pick-disclaimer')?.textContent).toMatch(/非实时/)
    expect(computer?.querySelectorAll('.pick-list li').length).toBe(8)
    expect(computer?.textContent).toMatch(/优先试/)
  })

  it('run_failed also dispatches credits-changed', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: run_failed\ndata: {"reason":"选品成果不合格，请重试"}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const creditEvents: Event[] = []
    const onCredits = (e: Event) => creditEvents.push(e)
    window.addEventListener('ebus:credits-changed', onCredits)
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    try {
      await enterViaSend(mounted.root)
      expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/不合格|重试/)
      expect(creditEvents.length).toBeGreaterThanOrEqual(1)
    } finally {
      window.removeEventListener('ebus:credits-changed', onCredits)
    }
  })

  it('insufficient credit shows upgrade hint without Computer fake list', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(JSON.stringify({ success: false, code: 402, message: '积分不足，请升级套餐' }), {
          status: 402,
          headers: { 'Content-Type': 'application/json' },
        }),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    expect(picklistApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/积分不足|升级/)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
  })

  it('opens Computer with listing preview via demo control after non-picklist send', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '随便聊聊天气')

    expect(picklistApiHits(fetchMock)).toHaveLength(0)
    const openListing = mounted.root.querySelector(
      '[data-demo="open-listing"]',
    ) as HTMLButtonElement
    expect(openListing).toBeTruthy()
    openListing.click()
    await flushUi()

    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()
    const body = mounted.root.querySelector('.computer-body')
    expect(body?.textContent).toContain(DEMO_LISTING.title)
    expect(body?.textContent).toContain(DEMO_LISTING.body)
    expect(mounted.root.querySelector('.listing-stack')).toBeNull()
    expect(mounted.root.textContent).toMatch(/主图方案预览/)
    expect(mounted.root.textContent).toMatch(/详情标题/)
    expect(mounted.root.textContent).toMatch(DEMO_LISTING.title)
    expect(body?.querySelectorAll('.comp-card').length).toBe(1)
    expect(body?.querySelector('.section-body.mute')?.textContent).toContain(DEMO_LISTING.body)
  })

  it('new task clears thread and Computer but stays on session shell', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)
    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()

    ;(mounted.root.querySelector('.side-new') as HTMLButtonElement).click()
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
  })

  it('sidebar CSS hides at ≤860px breakpoint (narrow readable chat)', () => {
    expect(sessionCss).toMatch(/max-width:\s*860px/)
    expect(sessionCss).toMatch(/\.session\s+\.sidebar\s*\{[^}]*display:\s*none/s)
    expect(sessionCss).toMatch(/max-width:\s*1100px/)
  })
})
