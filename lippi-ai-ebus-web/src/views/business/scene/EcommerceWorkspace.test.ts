import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import { processStreamText } from '@/composables/agent/agentProgress'
import { flushUi, mountSceneWorkspace } from '@/views/business/scene/workspace/mountSceneWorkspace'

const sessionCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'workspaceSession.css'),
  'utf8',
)

function mountWorkspace() {
  return mountSceneWorkspace('ecommerce')
}

const ECOMMERCE = {
  bizId: 'a1000001-0001-4000-8000-000000000001',
  sceneCode: 'ecommerce',
  displayName: '电商开店',
  status: 'AVAILABLE',
  sortOrder: 1,
  summary: '选品与上架',
}

/** Align with pi-extension scenes/.../launch.json */
const ECOMMERCE_SKILLS = {
  sceneCode: 'ecommerce',
  skills: [
    {
      skillId: 'ecommerce-picklist',
      label: '选品清单',
      examplePrompt: '请帮我生成「Mac Mini 配件」类选品清单',
      sortOrder: 1,
    },
    {
      skillId: 'ecommerce-skulist',
      label: '生成素材',
      examplePrompt: '请为商品「Mac Mini 拓展坞」生成上架素材。',
      sortOrder: 2,
    },
  ],
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

function sampleComputerView(
  items = SAMPLE_ITEMS,
  opts?: { omitItemIds?: boolean; omitItemHrefs?: boolean },
) {
  return {
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
        items: items.map((it, i) => ({
          ...(opts?.omitItemIds ? {} : { id: `pl-${i + 1}` }),
          ...(opts?.omitItemHrefs ? {} : { href: `https://item.example/${i + 1}` }),
          badge: it.title.startsWith('【优先试】') ? '优先试' : undefined,
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
  }
}

function artifactReadyData(view = sampleComputerView()) {
  return {
    artifactRef: 'pl-1',
    view,
  }
}

function okScenes(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** 测试夹具可传扁平 messages；包装成后端 turns 页结构。 */
function asTurnPage(items: unknown[], nextToken: string | null = null) {
  if (!items.length) {
    return { items: [], nextToken }
  }
  const first = items[0] as { messages?: unknown }
  if (first && Array.isArray(first.messages)) {
    return { items, nextToken }
  }
  return {
    items: [{ runId: null, userPrompt: null, at: null, messages: items }],
    nextToken,
  }
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

function sampleListingView() {
  return {
    version: 1,
    title: '硅胶沥水垫 · 上架素材',
    status: 'ready',
    blocks: [
      {
        type: 'media',
        role: 'hero',
        mediaObjectId: 'media-1',
        src: 'data:image/png;base64,AAAA',
        placeholder: '白底主图方案',
        alt: '主图',
      },
      { type: 'section', heading: '详情标题', body: '厨房硅胶沥水垫' },
      { type: 'section', heading: '详情正文', body: '易清洗防滑' },
      { type: 'section', heading: '展示说明', body: '主图突出颜色', tone: 'mute' },
    ],
  }
}

function listingArtifactReadyData(view = sampleListingView()) {
  return {
    artifactRef: 'sku-1',
    view,
  }
}

function samplePlanView() {
  return {
    version: 1,
    title: '硅胶沥水垫 · 策划分镜',
    status: 'ready',
    blocks: [
      {
        type: 'markdown',
        text:
          '## 成交方向\n痛点：台面长期积水\n\n## 主图分镜\n1. 主图：白底产品\n2. 对比：湿台面\n3. 场景：沥水收纳\n\n## 标题草稿\n硅胶沥水垫',
      },
    ],
  }
}

function listingPlanReadyData() {
  return {
    artifactRef: 'plan-1',
    view: samplePlanView(),
  }
}

function humanInputRequiredData() {
  return {
    question: '策划分镜已出。确认后将生成执行稿与生图 Prompt（再扣 1 积分）。也可补充需求让我改策划。',
    options: [
      { id: 'confirm_execute', label: '确认，出执行稿' },
      { id: 'supplement', label: '补充需求' },
    ],
    allowFreeText: true,
    toolCallId: 'ask-1',
    runId: 'r-l1',
  }
}

function mockCatalogAndCredits(opts?: {
  onPicklist?: () => Response
  onListing?: () => Response
  onResume?: (init?: RequestInit) => Response
  available?: number
  delayFeedbackGet?: Promise<void>
  sessions?: unknown[] | (() => unknown[])
  sessionsFailMessage?: string
  sessionMessages?: Record<string, unknown[]>
  latestArtifacts?: Record<string, unknown | null>
  skillCapsules?: unknown
}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes/') && url.includes('/skills')) {
      return okScenes(opts?.skillCapsules ?? ECOMMERCE_SKILLS)
    }
    if (url.includes('/api/v1/scenes')) {
      return okScenes([ECOMMERCE])
    }
    if (url.includes('/api/v1/sessions/') && url.includes('/messages')) {
      const id = url.split('/api/v1/sessions/')[1]?.split('/')[0] || ''
      const rows = opts?.sessionMessages?.[decodeURIComponent(id)] ?? []
      return okScenes(asTurnPage(rows))
    }
    if (url.includes('/api/v1/sessions/') && url.includes('/latest-artifact')) {
      const id = url.split('/api/v1/sessions/')[1]?.split('/')[0] || ''
      const art = opts?.latestArtifacts?.[decodeURIComponent(id)]
      return okScenes(art === undefined ? null : art)
    }
    if (url.includes('/api/v1/sessions')) {
      if (opts?.sessionsFailMessage) {
        return new Response(
          JSON.stringify({
            success: false,
            code: 500,
            message: opts.sessionsFailMessage,
          }),
          { status: 500, headers: { 'Content-Type': 'application/json' } },
        )
      }
      const list = typeof opts?.sessions === 'function' ? opts.sessions() : (opts?.sessions ?? [])
      return okScenes(list)
    }
    if (url.includes('/api/v1/credits')) {
      return creditsResponse(opts?.available ?? 14)
    }
    if (url.includes('/api/v1/feedbacks')) {
      const method = String(init?.method || 'GET').toUpperCase()
      if (method !== 'POST' && opts?.delayFeedbackGet) {
        await opts.delayFeedbackGet
      }
      let posted: { artifactId?: string; tag?: string; commentText?: string } = {}
      if (method === 'POST' && typeof init?.body === 'string') {
        try {
          posted = JSON.parse(init.body) as typeof posted
        } catch {
          posted = {}
        }
      }
      return new Response(
        JSON.stringify({
          success: true,
          code: 0,
          message: 'ok',
          data:
            method === 'GET'
              ? null
              : {
                  id: 'fb-1',
                  artifactId: posted.artifactId || 'pl-1',
                  tag: posted.tag || '质量差',
                  commentText: posted.commentText ?? null,
                  createdAt: '2026-09-28T00:00:00Z',
                },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      )
    }
    if (url.includes('/resume')) {
      if (opts?.onResume) {
        return opts.onResume(init)
      }
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r-l1","sessionId":"s1","holdId":"h2"}\n\n',
          `event: artifact_ready\ndata: ${JSON.stringify(listingArtifactReadyData())}\n\n`,
          'event: run_settled\ndata: {"runId":"r-l1","holdId":"h2","artifactRef":"sku-1","amount":1}\n\n',
        ]),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      )
    }
    if (url.includes('/api/v1/agent/runs') && !url.includes('/runs/empty')) {
      const body = typeof init?.body === 'string' ? init.body : ''
      if (body.includes('ecommerce-skulist')) {
        if (opts?.onListing) {
          return opts.onListing()
        }
        return new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r-l1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify(listingArtifactReadyData())}\n\n`,
            'event: run_settled\ndata: {"runId":"r-l1","holdId":"h1","artifactRef":"sku-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        )
      }
      if (opts?.onPicklist) {
        return opts.onPicklist()
      }
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
          'event: agent_started\ndata: {"label":"agent.start"}\n\n',
          'event: tool_started\ndata: {"toolName":"read_skill","toolCallId":"c1"}\n\n',
          'event: tool_finished\ndata: {"toolName":"read_skill","toolCallId":"c1","success":true,"output":"# Skill ecommerce-picklist"}\n\n',
          `event: message_delta\ndata: {"text":"${'x'.repeat(130)}"}\n\n`,
          'event: agent_ended\ndata: {"label":"agent.end"}\n\n',
          `event: artifact_ready\ndata: ${JSON.stringify(artifactReadyData())}\n\n`,
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

function setTextareaValue(el: HTMLTextAreaElement, value: string) {
  const proto = window.HTMLTextAreaElement.prototype
  const desc = Object.getOwnPropertyDescriptor(proto, 'value')
  desc?.set?.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

type FetchSpy = { mock: { calls: ReadonlyArray<unknown[]> } }

function billedRunApiHits(fetchMock: FetchSpy, skillId?: string) {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const u = String(input)
    if (!u.includes('/api/v1/agent/runs') || u.includes('/runs/empty')) {
      return false
    }
    const body = typeof (init as RequestInit | undefined)?.body === 'string' ? String((init as RequestInit).body) : ''
    if (body.includes('"dryRun":true')) return false
    if (skillId) return body.includes(`"skillId":"${skillId}"`)
    return true
  })
}

function picklistApiHits(fetchMock: FetchSpy) {
  return billedRunApiHits(fetchMock, 'ecommerce-picklist')
}

function listingApiHits(fetchMock: FetchSpy) {
  return billedRunApiHits(fetchMock, 'ecommerce-skulist')
}

function resumeApiHits(fetchMock: FetchSpy) {
  return fetchMock.mock.calls.filter(([input]) => String(input).includes('/resume'))
}

function emptyRunApiHits(fetchMock: FetchSpy) {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const u = String(input)
    if (!u.includes('/api/v1/agent/runs')) {
      return false
    }
    const body = typeof (init as RequestInit | undefined)?.body === 'string' ? String((init as RequestInit).body) : ''
    return body.includes('"dryRun":true') || u.includes('/runs/empty')
  })
}

describe('Workspace ecommerce default session shell', () => {
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

describe('Workspace ecommerce session shell', () => {
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

  async function enterViaSend(
    root: HTMLElement,
    text = '帮我做家居选品',
    opts?: { skillIndex?: number | null },
  ) {
    const skillIndex = opts && 'skillIndex' in opts ? opts.skillIndex : 0
    if (skillIndex != null) {
      const pills = root.querySelectorAll<HTMLButtonElement>(
        '[data-testid="session-quick-row"] .pill',
      )
      pills[skillIndex]?.click()
      await flushUi()
    }
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

  it('session picks capsule toggles selection and fills prompt without sending', async () => {
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
    expect(area.value).toMatch(/Mac Mini 配件/)
    expect(area.value).not.toMatch(/【/)
    expect(picks!.getAttribute('aria-pressed')).toBe('true')
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(picklistApiHits(fetchMock)).toHaveLength(0)

    const kept = area.value
    picks!.click()
    await flushUi()
    expect(picks!.getAttribute('aria-pressed')).toBe('false')
    expect(area.value).toBe(kept)
  })

  it('rejects send when prompt still contains example slots', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '请帮我生成「品类」类选品清单')
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
    expect(mounted.root.textContent).toMatch(/「」里的示例/)
  })

  it('sends picklist with skillId when capsule is selected even if text has 【优先试】', async () => {
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
    expect(area.value).toMatch(/Mac Mini 拓展坞/)
    expect(area.value).not.toMatch(/【/)
    expect(listing!.getAttribute('aria-pressed')).toBe('true')
    const slot = mounted.root.querySelector('.prompt-highlight .ph')
    expect(slot?.textContent).toBe('「Mac Mini 拓展坞」')
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(picklistApiHits(fetchMock)).toHaveLength(0)
    expect(listingApiHits(fetchMock)).toHaveLength(0)
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
      expect(computer?.textContent).toMatch(/picklist|选品清单/)
      expect(JSON.stringify(artifactReadyData())).toContain('"artifactRef":"pl-1"')
      expect(computer?.querySelector('[data-testid="git-view"]')).toBeTruthy()
      expect(computer?.querySelectorAll('[data-testid="git-view"]').length).toBe(1)
      expect(computer?.querySelector('.cv-note')?.textContent).toMatch(/非实时/)
      expect(computer?.querySelectorAll('.pick-disclaimer').length).toBe(0)
      expect(computer?.querySelectorAll('.pick-list li').length).toBe(8)
      expect(computer?.textContent).toMatch(/需求/)
      expect(computer?.textContent).toMatch(/候选1/)
      expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/已生成/)
      expect(mounted.root.querySelector('.chat-event-status')?.textContent).toMatch(/已生成/)
      // 成功后默认折叠过程；「执行过程 ›」可展开 TOOL/LLM
      expect(mounted.root.querySelector('[data-testid="process-events"]')).toBeNull()
      const processToggle = mounted.root.querySelector(
        '[data-testid="toggle-process-log"]',
      ) as HTMLButtonElement
      expect(processToggle?.getAttribute('aria-label')).toBe('执行过程')
      expect(processToggle?.textContent).toMatch(/执行过程/)
      expect(mounted.root.querySelector('[data-testid="card-result-actions"]')).toBeTruthy()
      expect(
        mounted.root
          .querySelector('[data-testid="card-result-actions"]')
          ?.querySelector('[data-testid="toggle-process-log"]'),
      ).toBeNull()
      processToggle.click()
      await flushUi()
      expect(mounted.root.querySelector('[data-testid="process-events"]')).toBeTruthy()
      expect(processToggle.getAttribute('aria-label')).toBe('收起执行过程')
      const eventTexts = [...mounted.root.querySelectorAll('.chat-event')].map((el) => el.textContent || '')
      expect(eventTexts.some((t) => t.includes('AGENT') && t.includes('开始执行'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('TOOL') && t.includes('读取技能说明'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('agent.start'))).toBe(false)
      expect(eventTexts.some((t) => t.includes('read_skill'))).toBe(false)
      expect(eventTexts.some((t) => /完成/.test(t))).toBe(false)
      expect(eventTexts.some((t) => t.includes('读取技能说明') && t.includes('[+]'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('LLM') && t.includes('模型输出'))).toBe(true)
      expect(eventTexts.some((t) => t.includes('AGENT') && t.includes('执行结束'))).toBe(true)
      // LLM body hidden until expand; expanded shows full raw stream
      expect(mounted.root.querySelector('.chat-stream-body')).toBeNull()
      const llmCard = mounted.root.querySelector('.chat-event-llm') as HTMLElement
      expect(llmCard).toBeTruthy()
      expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('[+]')
      llmCard.click()
      await flushUi()
      expect(llmCard.querySelector('.chat-stream-body')?.textContent).toBe('x'.repeat(130))
      expect(llmCard.querySelector('.chat-stream-toggle')?.textContent).toContain('[-]')
      // TOOL expand shows tool result body
      const toolCards = [...mounted.root.querySelectorAll('.chat-event-tool')] as HTMLElement[]
      const readSkill = toolCards.find((el) => el.textContent?.includes('读取技能说明'))
      expect(readSkill).toBeTruthy()
      expect(readSkill?.querySelector('.chat-stream-toggle')?.textContent).toContain('[+]')
      readSkill!.click()
      await flushUi()
      expect(readSkill?.querySelector('.chat-stream-body')?.textContent).toContain(
        '# Skill ecommerce-picklist',
      )
      expect(readSkill?.querySelector('.chat-stream-toggle')?.textContent).toContain('[-]')

      // STATUS toggles Computer preview: open → 关闭, close → 查看
      const status = mounted.root.querySelector('.chat-event-status') as HTMLElement
      const previewToggle = () => status.querySelector('.chat-stream-toggle')?.textContent || ''
      expect(status?.getAttribute('aria-disabled')).not.toBe('true')
      expect(status?.getAttribute('aria-expanded')).toBe('true')
      expect(previewToggle()).toBe('关闭')
      expect(status?.textContent).not.toMatch(/点击查看预览/)
      status.click()
      await flushUi()
      expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
      expect(status?.getAttribute('aria-expanded')).toBe('false')
      expect(previewToggle()).toBe('查看')
      status.click()
      await flushUi()
      expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(true)
      expect(status?.getAttribute('aria-expanded')).toBe('true')
      expect(previewToggle()).toBe('关闭')
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
            `event: artifact_ready\ndata: ${JSON.stringify(artifactReadyData())}\n\n`,
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
    expect(mounted.root.querySelector('[data-testid="process-events"]')).toBeNull()
    const processToggle = mounted.root.querySelector(
      '[data-testid="toggle-process-log"]',
    ) as HTMLButtonElement
    expect(processToggle).toBeTruthy()
    processToggle.click()
    await flushUi()
    const stream = mounted.root.querySelector('.chat-event-llm')
    expect(stream).toBeTruthy()
    expect(stream?.querySelector('.chat-stream-body')).toBeNull()
    ;(stream as HTMLElement).click()
    await flushUi()
    const body = stream?.querySelector('.chat-stream-body')?.textContent || ''
    expect(body).toContain(prose)
    expect(body).toContain('"templateId"')
    expect(body).toContain('\n')
    expect(body).not.toBe(fullStream)
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
            `event: artifact_ready\ndata: ${JSON.stringify(artifactReadyData())}\n\n`,
            'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    expect(mounted.root.querySelector('.chat-stream-body')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="process-events"]')).toBeNull()
    const processToggle = mounted.root.querySelector(
      '[data-testid="toggle-process-log"]',
    ) as HTMLButtonElement
    expect(processToggle).toBeTruthy()
    processToggle.click()
    await flushUi()
    expect(mounted.root.querySelector('.chat-event-llm')).toBeTruthy()
    expect(mounted.root.querySelector('.chat-stream-toggle')?.textContent).toContain('[+]')
    ;(mounted.root.querySelector('.chat-event-llm') as HTMLElement).click()
    await flushUi()
    expect(mounted.root.querySelector('.chat-stream-body')?.textContent).toBe(shortText)
  })

  it('does not hardcode picklist-specific step strings in chat source', () => {
    const vueSrc = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), 'Workspace.vue'),
      'utf8',
    )
    const consoleSrc = readFileSync(
      join(
        dirname(fileURLToPath(import.meta.url)),
        '../../../components/business/workspace/WorkspaceChatConsole.vue',
      ),
      'utf8',
    )
    expect(vueSrc).not.toMatch(/step-status/)
    expect(vueSrc).toMatch(/WorkspaceChatConsole/)
    expect(vueSrc).not.toContain(['useAgent', 'PicklistRun'].join(''))
    expect(vueSrc).not.toContain(['useAgent', 'ListingRun'].join(''))
    expect(vueSrc).toMatch(/useAgentSkillRun/)
    expect(vueSrc).toMatch(/resumeSkillRun/)
    expect(consoleSrc).toMatch(/chat-events/)
    expect(consoleSrc).toMatch(/tag-tool/)
    const runSrc = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), '../../../composables/agent/useAgentSkillRun.ts'),
      'utf8',
    )
    const src = `${vueSrc}\n${consoleSrc}\n${runSrc}`
    expect(src).not.toMatch(/正在读取技能|解析选品|生成候选清单|调用选品工具/)
    expect(vueSrc).not.toMatch(/FallbackPicklistCard|pick-list--legacy/)
    expect(runSrc).toMatch(/toGenerationArtifact/)
    expect(runSrc).toMatch(/artifactRef/)
  })

  it('ignores items-only artifact_ready without view and artifactRef', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify({
              artifactType: 'picklist',
              picklistId: 'pl-1',
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

    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/未收到可用清单/)
    expect(mounted.root.querySelector('.pick-list--legacy')).toBeNull()
    expect(mounted.root.querySelector('.pick-disclaimer')).toBeNull()
  })

  it('failed STATUS card expands error detail with tool and model dumps', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            'event: tool_started\ndata: {"toolName":"search_sku","toolCallId":"t1"}\n\n',
            'event: tool_finished\ndata: {"toolName":"search_sku","toolCallId":"t1","success":true,"output":"[{\\"title\\":\\"硅胶垫\\"}]"}\n\n',
            `event: message_delta\ndata: ${JSON.stringify({ text: '{"view":{"version":1,"title":"report","blocks":[]}}' })}\n\n`,
            'event: agent_ended\ndata: {"label":"agent.end"}\n\n',
            'event: run_failed\ndata: {"reason":"模型终态缺少 view 字段，无法生成成果视图。请展开「模型输出」核对 JSON。"}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)
    for (let i = 0; i < 8; i += 1) {
      if (mounted.root.textContent?.includes('缺少 view')) break
      await flushUi()
    }

    const status = mounted.root.querySelector('.chat-event-status') as HTMLElement
    expect(status).toBeTruthy()
    expect(status.classList.contains('is-failed')).toBe(true)
    expect(status.textContent).toMatch(/缺少 view/)
    expect(status.textContent).toMatch(/FAIL/)
    expect(status.textContent).toMatch(/\[\+\]/)
    expect(status.querySelector('.chat-stream-body')).toBeNull()
    status.click()
    await flushUi()
    const detail = status.querySelector('.chat-stream-body')?.textContent || ''
    expect(detail).toContain('【失败原因】')
    expect(detail).toContain('缺少 view')
    expect(detail).toContain('【模型输出】')
    expect(detail).toContain('search_sku')
    expect(detail).toContain('硅胶垫')
    expect(detail.indexOf('【模型输出】')).toBeLessThan(detail.indexOf('【工具'))
    expect(status.querySelector('.chat-stream-toggle')?.textContent).toContain('[-]')
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

  it('has no demo Computer preview controls', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    expect(mounted.root.querySelector('[data-demo]')).toBeNull()
  })

  it('free-text send omits skillId', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '随便聊聊天气', { skillIndex: null })
    const hits = billedRunApiHits(fetchMock)
    expect(hits.length).toBeGreaterThanOrEqual(1)
    const body = JSON.parse(String(hits[0]?.[1]?.body || '{}')) as { skillId?: string }
    expect(body.skillId).toBeUndefined()
    expect(mounted.root.textContent).not.toMatch(/请用上方胶囊/)
  })

  it('listing capsule streams billed skulist and shows live Computer (not demo)', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '请为商品硅胶沥水垫生成上架素材，优先适配淘宝。', {
      skillIndex: 1,
    })

    expect(listingApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    const body = listingApiHits(fetchMock)[0]?.[1] as RequestInit | undefined
    expect(String(body?.body || '')).toContain('ecommerce-skulist')
    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/已生成上架素材/)
    expect(mounted.root.textContent).toMatch(/厨房硅胶沥水垫/)
    expect(mounted.root.textContent).toMatch(/易清洗防滑/)
    expect(mounted.root.querySelector('.platform-switch')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="git-view"]')).toBeTruthy()
    expect(mounted.root.querySelector('.listing-copy.is-title')).toBeTruthy()
    expect(mounted.root.querySelector('[data-demo]')).toBeNull()
  })

  it('listing human_input_required shows confirm/supplement and resume confirm_execute', async () => {
    fetchMock = mockCatalogAndCredits({
      onListing: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r-l1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify(listingPlanReadyData())}\n\n`,
            `event: human_input_required\ndata: ${JSON.stringify(humanInputRequiredData())}\n\n`,
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '请为商品硅胶沥水垫生成上架素材，优先适配淘宝。', {
      skillIndex: 1,
    })

    expect(listingApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    expect(resumeApiHits(fetchMock)).toHaveLength(0)
    expect(mounted.root.querySelector('[data-testid="ask-human"]')).toBeTruthy()
    expect(mounted.root.querySelector('[data-testid="session-quick-row"]')).toBeNull()
    expect(mounted.root.textContent).toMatch(/确认，出执行稿/)
    expect(mounted.root.textContent).toMatch(/补充需求/)
    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/主图：白底产品/)
    expect(mounted.root.querySelector('.listing-hero-img')).toBeNull()

    const confirm = mounted.root.querySelector(
      '[data-testid="ask-human-confirm"]',
    ) as HTMLButtonElement
    confirm.click()
    await flushUi()
    await flushUi()

    expect(resumeApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    const [resumeUrl, resumeInit] = resumeApiHits(fetchMock)[0] as [string, RequestInit]
    expect(resumeUrl).toBe('/api/v1/agent/runs/r-l1/resume')
    expect(JSON.parse(String(resumeInit.body))).toMatchObject({
      toolCallId: 'ask-1',
      optionId: 'confirm_execute',
    })
    expect(mounted.root.querySelector('[data-testid="ask-human"]')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="session-quick-row"]')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/已生成上架素材/)
    expect(mounted.root.querySelector('.platform-switch')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="git-view"]')).toBeTruthy()
    expect(mounted.root.querySelector('.listing-copy.is-title')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/厨房硅胶沥水垫/)
  })

  it('listing insufficient credit shows upgrade hint without Computer', async () => {
    fetchMock = mockCatalogAndCredits({
      onListing: () =>
        new Response(JSON.stringify({ success: false, code: 402, message: '积分不足，请升级套餐' }), {
          status: 402,
          headers: { 'Content-Type': 'application/json' },
        }),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我写上架素材', { skillIndex: 1 })

    expect(listingApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/积分不足|升级/)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
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
    expect(sessionCss).toMatch(/\.session-list\s*\{[^}]*flex:\s*1/s)
    expect(sessionCss).toMatch(/\.session-list\s*\{[^}]*overflow:\s*auto/s)
  })

  it('shows retry/like/dislike under success STATUS card, not on Computer bar', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    expect(mounted.root.querySelector('[data-testid="result-actions"]')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="card-result-actions"]')).toBeTruthy()
    expect(
      mounted.root.querySelector('.chat-scroll [data-testid="card-result-actions"]'),
    ).toBeTruthy()
    expect(mounted.root.querySelector('.computer [data-testid="card-result-actions"]')).toBeNull()
    expect(mounted.root.querySelector('[data-testid="one-click-retry"]')).toBeTruthy()
    expect(mounted.root.querySelector('[data-testid="card-like"]')).toBeTruthy()
    expect(mounted.root.querySelector('[data-testid="card-dislike"]')).toBeTruthy()
  })

  it('like posts 质量好 without drawer; dislike opens drawer then posts 质量差', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    ;(mounted.root.querySelector('[data-testid="card-like"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    expect(mounted.root.querySelector('[data-testid="dislike-drawer"]')).toBeNull()
    const postHits = fetchMock.mock.calls.filter(([input, init]) => {
      const method = String((init as RequestInit | undefined)?.method || 'GET').toUpperCase()
      return String(input).includes('/api/v1/feedbacks') && method === 'POST'
    })
    expect(postHits.length).toBeGreaterThanOrEqual(1)
    const [, likeInit] = postHits[postHits.length - 1] as [string, RequestInit]
    expect(JSON.parse(String(likeInit.body))).toMatchObject({
      artifactId: 'pl-1',
      tag: '质量好',
    })

    ;(mounted.root.querySelector('[data-testid="card-dislike"]') as HTMLButtonElement).click()
    await flushUi()
    expect(mounted.root.querySelector('[data-testid="dislike-drawer"]')).toBeTruthy()

    const note = mounted.root.querySelector(
      '[data-testid="dislike-comment"]',
    ) as HTMLTextAreaElement
    setTextareaValue(note, '文案偏空')
    await flushUi()
    ;(mounted.root.querySelector('[data-testid="dislike-submit"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const afterDislike = fetchMock.mock.calls.filter(([input, init]) => {
      const method = String((init as RequestInit | undefined)?.method || 'GET').toUpperCase()
      return String(input).includes('/api/v1/feedbacks') && method === 'POST'
    })
    expect(afterDislike.length).toBeGreaterThan(postHits.length)
    const [, dislikeInit] = afterDislike[afterDislike.length - 1] as [string, RequestInit]
    expect(JSON.parse(String(dislikeInit.body))).toMatchObject({
      artifactId: 'pl-1',
      tag: '质量差',
      commentText: '文案偏空',
    })
  })

  it('shows retry/like/dislike only on the latest success STATUS card', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    expect(mounted.root.querySelectorAll('[data-testid="one-click-retry"]')).toHaveLength(1)

    ;(mounted.root.querySelector('[data-testid="one-click-retry"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    // 旧 STATUS 可有执行过程入口；重试/赞踩仅挂在最新一条
    expect(mounted.root.querySelectorAll('[data-testid="one-click-retry"]')).toHaveLength(1)
    expect(mounted.root.querySelectorAll('[data-testid="card-like"]')).toHaveLength(1)
    expect(mounted.root.querySelectorAll('[data-testid="toggle-process-log"]').length).toBeGreaterThanOrEqual(1)
    const statusCards = mounted.root.querySelectorAll('.chat-event-status.is-preview')
    expect(statusCards.length).toBeGreaterThanOrEqual(2)
    const lastStatus = statusCards[statusCards.length - 1]
    expect(lastStatus?.closest('.msg')?.querySelector('[data-testid="one-click-retry"]')).toBeTruthy()
  })

  it('keeps like highlight when delayed GET restore returns null', async () => {
    let releaseGet: () => void = () => {}
    const delayFeedbackGet = new Promise<void>((resolve) => {
      releaseGet = resolve
    })
    fetchMock = mockCatalogAndCredits({ delayFeedbackGet })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    const like = mounted.root.querySelector('[data-testid="card-like"]') as HTMLButtonElement
    expect(like).toBeTruthy()
    like.click()
    await flushUi()
    await flushUi()
    expect(like.classList.contains('is-on')).toBe(true)

    releaseGet()
    await flushUi()
    await flushUi()
    expect(
      (mounted.root.querySelector('[data-testid="card-like"]') as HTMLButtonElement).classList.contains(
        'is-on',
      ),
    ).toBe(true)
  })

  it('lists sessions in sidebar and switches workspace on click', async () => {
    const view = sampleComputerView()
    fetchMock.mockRestore()
    fetchMock = mockCatalogAndCredits({
      sessions: [
        {
          sessionId: 'sess-a',
          title: '旧会话甲',
          sceneCode: 'ecommerce',
          updatedAt: '2026-09-27T00:00:00Z',
        },
        {
          sessionId: 'sess-b',
          title: '旧会话乙',
          sceneCode: 'ecommerce',
          updatedAt: '2026-09-28T00:00:00Z',
        },
      ],
      sessionMessages: {
        'sess-b': [
          {
            role: 'user',
            content: '帮我找杯子',
            createdAt: '2026-09-28T08:00:00.000Z',
            seq: 1,
          },
          {
            role: 'assistant',
            content: "I'll load the skill instructions first.",
            createdAt: '2026-09-28T08:00:01.000Z',
            seq: 2,
          },
          {
            role: 'assistant',
            content: '```json\n{"view":{"version":1,"title":"dump","blocks":[]}}\n```',
            createdAt: '2026-09-28T08:00:10.000Z',
            seq: 6,
          },
          {
            role: 'user',
            content: '帮我找杯子',
            createdAt: '2026-09-28T08:01:00.000Z',
            seq: 7,
          },
          {
            role: 'assistant',
            content: '```json\n{"view":{"version":1,"title":"dump","blocks":[]}}\n```',
            createdAt: '2026-09-28T08:01:05.000Z',
            seq: 8,
          },
        ],
      },
      latestArtifacts: {
        'sess-b': {
          id: 'pl-switched',
          artifactType: 'picklist',
          sceneCode: 'ecommerce',
          title: '切换后的选品',
          createdAt: '2026-09-28T00:00:00Z',
          view,
          sessionId: 'sess-b',
        },
      },
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()

    const items = mounted.root.querySelectorAll('[data-testid="session-item"]')
    expect(mounted.root.querySelector('[data-testid="session-list"]')).toBeTruthy()
    expect(items.length).toBe(2)

    ;(items[1] as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const thread = mounted.root.querySelector('.chat-scroll')?.textContent || ''
    expect(thread).toContain('帮我找杯子')
    expect(thread).toMatch(/已生成选品成果/)
    expect(thread).not.toContain("I'll load")
    expect(thread).not.toContain('"blocks"')
    // 历史 STATUS 有「执行过程」入口，展开后可查看中间过程正文
    expect(mounted.root.querySelector('[data-testid="process-events"]')).toBeNull()
    const processToggle = mounted.root.querySelector(
      '[data-testid="toggle-process-log"]',
    ) as HTMLButtonElement
    expect(processToggle?.getAttribute('aria-label')).toBe('执行过程')
    processToggle.click()
    await flushUi()
    const processRows = mounted.root.querySelectorAll(
      '[data-testid="process-events"] .chat-event.expandable',
    )
    expect(processRows.length).toBeGreaterThan(0)
    ;(processRows[0] as HTMLElement).click()
    await flushUi()
    expect(
      mounted.root.querySelector('[data-testid="process-events"] .chat-stream-body')?.textContent,
    ).toMatch(/load the skill/i)
    const userBubbles = mounted.root.querySelectorAll('.msg.user .msg-text')
    expect(userBubbles.length).toBe(2)
    expect(userBubbles[0]?.textContent).toContain('帮我找杯子')
    expect(userBubbles[0]?.querySelector('.msg-time')).toBeNull()
    const agentStatuses = mounted.root.querySelectorAll('.chat-event-status.is-preview')
    expect(agentStatuses.length).toBe(2)
    expect(agentStatuses[0]?.querySelector('.chat-event-time')?.textContent).toMatch(/^\d{2}:\d{2}:\d{2}$/)
    expect(items[1]?.classList.contains('on')).toBe(true)
    expect(items[0]?.classList.contains('on')).toBe(false)
    // 侧栏切入：挂上成果但不自动展开 Computer
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
    expect(mounted.root.querySelector('[data-testid="one-click-retry"]')?.getAttribute('aria-label')).toBe(
      '重试',
    )
    ;(agentStatuses[1] as HTMLElement).click()
    await flushUi()
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(true)
    expect(mounted.root.querySelector('.computer-body')?.textContent).toContain('候选1')

    await enterViaSend(mounted.root, '继续这个选品')
    const hits = picklistApiHits(fetchMock)
    const last = hits[hits.length - 1] as [string, RequestInit]
    const body = JSON.parse(String(last[1].body)) as { sessionId?: string }
    expect(body.sessionId).toBe('sess-b')

    ;(mounted.root.querySelector('.side-new') as HTMLButtonElement).click()
    await flushUi()
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBe(0)
    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
    expect(
      Array.from(mounted.root.querySelectorAll('[data-testid="session-item"]')).every(
        (el) => !el.classList.contains('on'),
      ),
    ).toBe(true)
  })

  it('session switch supports load more older messages', async () => {
    const view = sampleComputerView()
    fetchMock.mockRestore()
    fetchMock = mockCatalogAndCredits({
      sessions: [
        {
          sessionId: 'sess-b',
          title: '旧会话乙',
          sceneCode: 'ecommerce',
          updatedAt: '2026-09-28T00:00:00Z',
        },
      ],
      latestArtifacts: {
        'sess-b': {
          id: 'pl-switched',
          artifactType: 'picklist',
          sceneCode: 'ecommerce',
          title: '切换后的选品',
          createdAt: '2026-09-28T00:00:00Z',
          view,
          sessionId: 'sess-b',
        },
      },
    })
    const baseImpl = fetchMock.getMockImplementation()!
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/api/v1/sessions/sess-b/messages')) {
        const u = new URL(url, 'http://local')
        const token = u.searchParams.get('nextToken')
        if (token === '7') {
          return okScenes(
            asTurnPage(
              [
                {
                  role: 'user',
                  content: '更早的提问',
                  createdAt: '2026-09-28T07:00:00.000Z',
                  seq: 1,
                },
                {
                  role: 'assistant',
                  content: '```json\n{"view":{"version":1,"title":"old","blocks":[]}}\n```',
                  createdAt: '2026-09-28T07:00:10.000Z',
                  seq: 6,
                },
              ],
              null,
            ),
          )
        }
        return okScenes(
          asTurnPage(
            [
              {
                role: 'user',
                content: '帮我找杯子',
                createdAt: '2026-09-28T08:01:00.000Z',
                seq: 7,
              },
              {
                role: 'assistant',
                content: '```json\n{"view":{"version":1,"title":"dump","blocks":[]}}\n```',
                createdAt: '2026-09-28T08:01:05.000Z',
                seq: 8,
              },
            ],
            '7',
          ),
        )
      }
      return baseImpl(input, init)
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()
    ;(mounted.root.querySelector('[data-testid="session-item"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    expect(mounted.root.querySelector('[data-testid="session-load-more"]')).toBeTruthy()
    ;(mounted.root.querySelector('[data-testid="session-load-more"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toContain('更早的提问')
    expect(mounted.root.querySelector('[data-testid="session-load-more"]')).toBeNull()
  })

  it('refreshes sidebar sessions after billed run succeeds', async () => {
    let afterBind = false
    fetchMock.mockRestore()
    fetchMock = mockCatalogAndCredits({
      sessions: () =>
        afterBind
          ? [
              {
                sessionId: 's1',
                title: '新计费会话',
                sceneCode: 'ecommerce',
                updatedAt: '2026-09-28T12:00:00Z',
              },
            ]
          : [],
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()
    expect(mounted.root.querySelectorAll('[data-testid="session-item"]').length).toBe(0)

    afterBind = true
    await enterViaSend(mounted.root, '帮我做家居选品')
    await flushUi()
    await flushUi()

    const items = mounted.root.querySelectorAll('[data-testid="session-item"]')
    expect(items.length).toBe(1)
    expect(items[0]?.textContent).toContain('新计费会话')
  })

  it('shows sessions load error hint instead of empty list', async () => {
    fetchMock.mockRestore()
    fetchMock = mockCatalogAndCredits({ sessionsFailMessage: '会话列表失败' })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()
    await flushUi()

    expect(mounted.root.querySelector('[data-testid="sessions-error"]')?.textContent).toContain(
      '会话列表失败',
    )
    expect(mounted.root.querySelectorAll('[data-testid="session-item"]').length).toBe(0)
  })

  it('session switch shows card actions and retry reuses sessionId', async () => {
    const view = sampleComputerView()
    fetchMock.mockRestore()
    fetchMock = mockCatalogAndCredits({
      sessions: [
        {
          sessionId: 'sess-a',
          title: '旧会话甲',
          sceneCode: 'ecommerce',
          updatedAt: '2026-09-27T00:00:00Z',
        },
        {
          sessionId: 'sess-b',
          title: '旧会话乙',
          sceneCode: 'ecommerce',
          updatedAt: '2026-09-28T00:00:00Z',
        },
      ],
      sessionMessages: {
        'sess-b': [
          { role: 'user', content: '帮我找杯子' },
          { role: 'assistant', content: '这是杯子建议' },
        ],
      },
      latestArtifacts: {
        'sess-b': {
          id: 'pl-switched',
          artifactType: 'picklist',
          sceneCode: 'ecommerce',
          title: '切换后的选品',
          createdAt: '2026-09-28T00:00:00Z',
          view,
          sessionId: 'sess-b',
        },
      },
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()

    const items = mounted.root.querySelectorAll('[data-testid="session-item"]')
    ;(items[1] as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const actions = mounted.root.querySelector('[data-testid="card-result-actions"]')
    expect(actions).toBeTruthy()
    const retry = mounted.root.querySelector(
      '[data-testid="one-click-retry"]',
    ) as HTMLButtonElement
    expect(retry.disabled).toBe(false)
    const before = picklistApiHits(fetchMock).length
    retry.click()
    await flushUi()
    await flushUi()

    const hits = picklistApiHits(fetchMock)
    expect(hits.length).toBeGreaterThan(before)
    const last = hits[hits.length - 1] as [string, RequestInit]
    const body = JSON.parse(String(last[1].body)) as { text?: string; sessionId?: string }
    expect(body.sessionId).toBe('sess-b')
    expect(body.text).toBe('帮我找杯子')
  })

  it('one-click retry reuses last prompt and session without newTask', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    expect(mounted.root.querySelector('[data-testid="card-result-actions"]')).toBeTruthy()
    const before = picklistApiHits(fetchMock).length
    ;(mounted.root.querySelector('[data-testid="one-click-retry"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const hits = picklistApiHits(fetchMock)
    expect(hits.length).toBeGreaterThan(before)
    const last = hits[hits.length - 1] as [string, RequestInit]
    const body = JSON.parse(String(last[1].body)) as { text?: string; sessionId?: string }
    expect(body.text).toBe('帮我做家居选品')
    expect(body.sessionId).toBe('s1')
    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelectorAll('.chat-scroll .msg').length).toBeGreaterThan(0)
  })

  it('quality feedback posts tag without calling credit write APIs', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '帮我做家居选品')

    const picklistBefore = picklistApiHits(fetchMock).length
    const listingBefore = listingApiHits(fetchMock).length

    ;(mounted.root.querySelector('[data-testid="card-dislike"]') as HTMLButtonElement).click()
    await flushUi()
    const note = mounted.root.querySelector(
      '[data-testid="dislike-comment"]',
    ) as HTMLTextAreaElement
    setTextareaValue(note, '文案偏空')
    await flushUi()

    ;(mounted.root.querySelector('[data-testid="dislike-submit"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const feedbackHits = fetchMock.mock.calls.filter(([input, init]) => {
      const method = String((init as RequestInit | undefined)?.method || 'GET').toUpperCase()
      return String(input).includes('/api/v1/feedbacks') && method === 'POST'
    })
    expect(feedbackHits.length).toBeGreaterThanOrEqual(1)
    const [, init] = feedbackHits[0] as [string, RequestInit]
    expect(JSON.parse(String(init.body))).toMatchObject({
      artifactId: 'pl-1',
      tag: '质量差',
      commentText: '文案偏空',
    })
    expect(mounted.root.querySelector('[data-testid="feedback-hint"]')?.textContent).toMatch(
      /已记录/,
    )
    expect(picklistApiHits(fetchMock).length).toBe(picklistBefore)
    expect(listingApiHits(fetchMock).length).toBe(listingBefore)
  })

  it('picklist handoff 做上架素材 starts same-session skulist with handoff text', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    expect(picklistApiHits(fetchMock).length).toBeGreaterThanOrEqual(1)
    const pickBody = JSON.parse(String(picklistApiHits(fetchMock)[0][1].body)) as {
      sessionId?: string
    }
    const listingBefore = listingApiHits(fetchMock).length

    const handoffBtn = mounted.root.querySelector(
      '.item-action-btn',
    ) as HTMLButtonElement
    expect(handoffBtn).toBeTruthy()
    expect(handoffBtn.disabled).toBe(false)
    handoffBtn.click()
    await flushUi()
    await flushUi()

    const hits = listingApiHits(fetchMock)
    expect(hits.length).toBeGreaterThan(listingBefore)
    const body = JSON.parse(String(hits[hits.length - 1][1].body)) as {
      text?: string
      sessionId?: string
      skillId?: string
    }
    expect(body.skillId).toBe('ecommerce-skulist')
    expect(body.text).toContain('原链：')
    expect(body.text).toContain('https://item.example/1')
    expect(body.text).toContain('来源选品条目：pl-1')
    expect(body.sessionId).toBe(pickBody.sessionId ?? 's1')
    expect(body.sessionId).toBe('s1')
    expect(mounted.root.textContent).toMatch(/来源选品条目：pl-1/)
  })

  it('picklist item without https href does not start skulist on 做上架素材 click', async () => {
    fetchMock = mockCatalogAndCredits({
      onPicklist: () =>
        new Response(
          sseBody([
            'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
            'event: agent_started\ndata: {"label":"agent.start"}\n\n',
            `event: artifact_ready\ndata: ${JSON.stringify(artifactReadyData(sampleComputerView(SAMPLE_ITEMS, { omitItemHrefs: true })))}\n\n`,
            'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"pl-1","amount":1}\n\n',
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
        ),
    })
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    const listingBefore = listingApiHits(fetchMock).length
    const handoffBtn = mounted.root.querySelector(
      '.item-action-btn',
    ) as HTMLButtonElement
    expect(handoffBtn).toBeTruthy()
    expect(handoffBtn.disabled).toBe(true)
    handoffBtn.click()
    await flushUi()
    await flushUi()

    expect(listingApiHits(fetchMock).length).toBe(listingBefore)
  })

  it('listing one-click retry reuses last prompt and session', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const listingText = '请为商品硅胶沥水垫生成上架素材，优先适配淘宝。'
    await enterViaSend(mounted.root, listingText, { skillIndex: 1 })

    expect(mounted.root.querySelector('[data-testid="card-result-actions"]')).toBeTruthy()
    const before = listingApiHits(fetchMock).length
    const picklistBefore = picklistApiHits(fetchMock).length
    ;(mounted.root.querySelector('[data-testid="one-click-retry"]') as HTMLButtonElement).click()
    await flushUi()
    await flushUi()

    const hits = listingApiHits(fetchMock)
    expect(hits.length).toBeGreaterThan(before)
    expect(picklistApiHits(fetchMock).length).toBe(picklistBefore)
    const last = hits[hits.length - 1] as [string, RequestInit]
    const body = JSON.parse(String(last[1].body)) as {
      text?: string
      sessionId?: string
      skillId?: string
    }
    expect(body.text).toBe(listingText)
    expect(body.sessionId).toBe('s1')
    expect(String(last[1].body || '')).toContain('ecommerce-skulist')
  })
})
