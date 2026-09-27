import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import EcommerceWorkspacePlaceholder from '@/views/business/scene/EcommerceWorkspacePlaceholder.vue'
import { DEMO_LISTING } from '@/views/business/scene/ecommerceDemoFixtures'
import { processStreamText } from '@/composables/agent/agentProgress'

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
  painPoint: `场景不便${i + 1}`,
  angle: '刚需测款',
  diff: `视觉点${i + 1}`,
  niche: `细分${i % 3}`,
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
    if (url.includes('/api/v1/agent/runs') && !url.includes('/runs/empty')) {
      if (opts?.onPicklist) {
        return opts.onPicklist()
      }
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
          'event: agent_started\ndata: {"label":"agent.start"}\n\n',
          'event: tool_started\ndata: {"toolName":"read_skill"}\n\n',
          'event: tool_finished\ndata: {"toolName":"read_skill"}\n\n',
          `event: message_delta\ndata: {"text":"${'x'.repeat(130)}"}\n\n`,
          'event: agent_ended\ndata: {"label":"agent.end"}\n\n',
          `event: artifact_ready\ndata: ${JSON.stringify({
            artifactType: 'picklist',
            picklistId: 'pl-1',
            runId: 'r1',
            templateId: 'domestic-generic-default',
            disclaimer: '基于通用电商知识推断，非实时平台数据',
            items: SAMPLE_ITEMS,
            view: {
              version: 1,
              title: 'picklist',
              status: 'settled',
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
                    badge: it.title.startsWith('【优先试】') ? 'priority' : undefined,
                    title: it.title.replace(/^【优先试】/, ''),
                    lines: [
                      { kind: 'priceBand', text: it.priceBand, emphasis: 'price' },
                      { kind: 'painPoint', text: it.painPoint },
                      { kind: 'angle', text: it.angle },
                      { kind: 'diff', text: it.diff },
                      { kind: 'niche', text: it.niche },
                    ],
                    tags: [
                      { kind: 'demand', text: it.demand, tone: 'positive' },
                      { kind: 'competition', text: it.competition, tone: 'caution' },
                      { kind: 'margin', text: it.margin, tone: 'info' },
                      { kind: 'risk', text: it.risk, tone: 'safe' },
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
    if (url.includes('/api/v1/agent/runs/empty') || (url.includes('/api/v1/agent/runs') && false)) {
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
  return fetchMock.mock.calls.filter(([input, init]) => {
    const u = String(input)
    if (!u.includes('/api/v1/agent/runs') || u.includes('/runs/empty')) {
      return false
    }
    const body = typeof (init as RequestInit | undefined)?.body === 'string' ? String((init as RequestInit).body) : ''
    return !body.includes('"dryRun":true')
  })
}

function emptyRunApiHits(fetchMock: ReturnType<typeof vi.spyOn>) {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const u = String(input)
    if (!u.includes('/api/v1/agent/runs')) {
      return false
    }
    const body = typeof (init as RequestInit | undefined)?.body === 'string' ? String((init as RequestInit).body) : ''
    return body.includes('"dryRun":true') || u.includes('/runs/empty')
  })
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
    expect(area.value).toMatch(/厨房小件/)
    expect(area.value).toMatch(/19–39/)
    expect(area.value).not.toMatch(/【/)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
  })

  it('rejects send when prompt still contains template placeholders', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '请帮我生成【品类】类选品清单，客单价【最低价】–【最高价】元。')
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
    expect(mounted.root.textContent).toMatch(/【】里的占位/)
  })

  it('sends picklist intent when user text contains 【优先试】 but no template slots', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品，优先试【优先试】那一类')
    expect(picklistApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    expect(mounted.root.textContent).not.toMatch(/【】里的占位/)
  })

  it('aligns quick-row and prompt inside one composer column', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const composer = mounted.root.querySelector('.chat-input-wrap .chat-composer')
    expect(composer).toBeTruthy()
    expect(composer?.querySelector('.quick-row')).toBeTruthy()
    expect(composer?.querySelector('.prompt-box')).toBeTruthy()
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
    expect(area.value).toMatch(/硅胶沥水垫/)
    expect(area.value).not.toMatch(/【/)
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
      expect(url).toBe('/api/v1/agent/runs')
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
      expect(mounted.root.querySelector('.chat-event-status')?.textContent).toMatch(/已生成/)
      expect(mounted.root.querySelector('.chat-events')).toBeTruthy()
      const eventTexts = [...mounted.root.querySelectorAll('.chat-event')].map((el) => el.textContent || '')
      expect(eventTexts.some((t) => t.includes('AGENT') && t.includes('开始执行'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('TOOL') && t.includes('读取技能说明'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('agent.start'))).toBe(false)
      expect(eventTexts.some((t) => t.includes('read_skill'))).toBe(false)
      expect(eventTexts.some((t) => /完成/.test(t))).toBe(false)
      expect(eventTexts.some((t) => t.includes('✓'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('LLM') && t.includes('模型输出'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('AGENT') && t.includes('执行结束'))).toBe(true)
      // LLM body hidden until expand; expanded shows full raw stream
      expect(mounted.root.querySelector('.chat-stream-body')).toBeNull()
      const llmCard = mounted.root.querySelector('.chat-event-llm') as HTMLElement
      expect(llmCard).toBeTruthy()
      expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('展开')
      llmCard.click()
      await flushUi()
      expect(mounted.root.querySelector('.chat-stream-body')?.textContent).toBe('x'.repeat(130))
      expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('收起')
      // STATUS click opens Computer preview
      const status = mounted.root.querySelector('button.chat-event-status') as HTMLButtonElement
      expect(status?.disabled).toBe(false)
      expect(status?.textContent).toMatch(/查看/)
      expect(status?.textContent).not.toMatch(/点击查看预览/)
      status.click()
      await flushUi()
      expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(true)
      expect(creditEvents.length).toBeGreaterThanOrEqual(1)
    } finally {
      window.removeEventListener('ebus:credits-changed', onCredits)
    }
  })

  it('keeps LLM collapsed by default; expand shows full stream including JSON', async () => {
    const prose = '我先加载技能说明。'
    const artifactJson =
      '{"templateId":"domestic-generic-default","disclaimer":"x","items":[{"title":"a"}]}'
    const fullStream = `${prose} ${artifactJson}`
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            `event: message_delta\ndata: ${JSON.stringify({ text: fullStream })}\n\n`,
            'event: agent_ended\ndata: {"label":"agent.end"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify({
              artifactType: 'picklist',
              picklistId: 'pl-1',
              runId: 'r1',
              templateId: 'domestic-generic-default',
              disclaimer: '基于通用电商知识推断，非实时平台数据',
              items: SAMPLE_ITEMS,
              view: {
                version: 1,
                title: 'picklist',
                status: 'settled',
                blocks: [
                  { type: 'note', text: '基于通用电商知识推断，非实时平台数据', tone: 'mute' },
                  {
                    type: 'list',
                    ordered: true,
                    items: SAMPLE_ITEMS.slice(0, 2).map((it) => ({
                      title: it.title.replace(/^【优先试】/, ''),
                      lines: [{ kind: 'priceBand', text: it.priceBand, emphasis: 'price' }],
                      tags: [{ kind: 'demand', text: it.demand, tone: 'positive' }],
                    })),
                  },
                ],
              },
            })}\n\n`,
            'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)
    for (let i = 0; i < 8; i += 1) {
      if (mounted.root.textContent?.includes('已生成')) break
      await flushUi()
    }

    expect(mounted.root.textContent).toMatch(/已生成/)
    expect(processStreamText(fullStream)).toBe(prose)
    const stream = mounted.root.querySelector('.chat-event-llm')
    expect(stream).toBeTruthy()
    expect(stream?.querySelector('.chat-stream-body')).toBeNull()
    ;(stream as HTMLElement).click()
    await flushUi()
    const body = stream?.querySelector('.chat-stream-body')?.textContent || ''
    expect(body).toBe(fullStream)
    expect(body).toContain('templateId')
  })

  it('short message_delta stays collapsed until expand', async () => {
    const shortText = 'x'.repeat(120)
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            `event: message_delta\ndata: {"text":"${shortText}"}\n\n`,
            'event: agent_ended\ndata: {"label":"agent.end"}\n\n',
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

    expect(mounted.root.querySelector('.chat-stream-body')).toBeNull()
    expect(mounted.root.querySelector('.chat-event-llm')).toBeTruthy()
    expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('展开')
    ;(mounted.root.querySelector('.chat-event-llm') as HTMLElement).click()
    await flushUi()
    expect(mounted.root.querySelector('.chat-stream-body')?.textContent).toBe(shortText)
  })

  it('does not hardcode picklist-specific step strings in chat source', () => {
    const vueSrc = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), 'EcommerceWorkspacePlaceholder.vue'),
      'utf8',
    )
    expect(vueSrc).not.toMatch(/step-status/)
    expect(vueSrc).toMatch(/chat-events/)
    expect(vueSrc).toMatch(/tag-tool/)
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
