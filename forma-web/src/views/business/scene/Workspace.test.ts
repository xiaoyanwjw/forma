import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import { flushUi, mountSceneWorkspace } from '@/views/business/scene/workspace/mountSceneWorkspace'

function setTextareaValue(el: HTMLTextAreaElement, value: string) {
  const proto = window.HTMLTextAreaElement.prototype
  const desc = Object.getOwnPropertyDescriptor(proto, 'value')
  desc?.set?.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

const ECOMMERCE = {
  bizId: 'a1000001-0001-4000-8000-000000000001',
  sceneCode: 'ecommerce',
  displayName: '电商开店',
  category: 'ecommerce',
  status: 'AVAILABLE',
  sortOrder: 1,
  summary: '选品与上架',
}

const XHS = {
  bizId: 'a1000001-0001-4000-8000-000000000003',
  sceneCode: 'xiaohongshu',
  displayName: '小红书种草',
  category: 'content',
  status: 'AVAILABLE',
  sortOrder: 2,
  summary: '笔记结构与种草表达',
}

const TECH_DIGEST = {
  bizId: 'a1000001-0001-4000-8000-000000000005',
  sceneCode: 'tech_digest',
  displayName: '科技前沿',
  category: 'tech',
  status: 'AVAILABLE',
  sortOrder: 2,
  summary: '丢产品页、AI 文章或技术文档链接：解析正文，一页摘要带走。',
}

const TECH_PRODUCT = {
  bizId: 'a1000001-0001-4000-8000-000000000007',
  sceneCode: 'tech_product',
  displayName: '产品雷达',
  category: 'tech',
  status: 'AVAILABLE',
  sortOrder: 1,
  summary: '贴一个产品官网：分层拆解定位、卖点与公开套餐信号，字段可核对。',
}

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

const TECH_DIGEST_SKILLS = {
  sceneCode: 'tech_digest',
  skills: [
    {
      skillId: 'tech-digest',
      label: '链接速读',
      examplePrompt:
        '请速读这个链接，我关心它适不适合小团队用：https://example.com/product',
      sortOrder: 1,
    },
  ],
}

const TECH_PRODUCT_SKILLS = {
  sceneCode: 'tech_product',
  skills: [
    {
      skillId: 'tech-competitor',
      label: '竞品分析',
      examplePrompt: '请拆解这个产品官网：https://www.notion.so',
      sortOrder: 1,
    },
    {
      skillId: 'tech-briefing',
      label: '产品早报',
      examplePrompt: '请出今天 Product Hunt 值得跟的产品早报',
      sortOrder: 2,
    },
  ],
}

const XHS_SKILLS = {
  sceneCode: 'xiaohongshu',
  skills: [
    {
      skillId: 'xhs-topiclist',
      label: '选题清单',
      examplePrompt: '请帮我生成「Mac Mini 桌搭」类小红书种草选题清单，面向「居家办公」。',
      sortOrder: 1,
    },
    {
      skillId: 'xhs-note',
      label: '笔记种草稿',
      examplePrompt: '请为商品「Mac Mini 拓展坞」写一篇小红书种草笔记，语气像真人分享。',
      sortOrder: 2,
    },
    {
      skillId: 'xhs-break',
      label: '爆文拆解',
      examplePrompt: '请拆解下面这篇笔记，并改写成我的商品「Mac Mini 拓展坞」。',
      sortOrder: 3,
    },
  ],
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
      for (const c of chunks) controller.enqueue(enc.encode(c))
      controller.close()
    },
  })
}

function sceneSkillsForUrl(url: string) {
  if (url.includes('xiaohongshu')) return XHS_SKILLS
  if (url.includes('tech_product')) return TECH_PRODUCT_SKILLS
  if (url.includes('tech_digest')) return TECH_DIGEST_SKILLS
  return ECOMMERCE_SKILLS
}

function mockCatalog(scenes: unknown[] = [ECOMMERCE, XHS, TECH_DIGEST, TECH_PRODUCT]) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes/') && url.includes('/skills')) {
      return okScenes(sceneSkillsForUrl(url))
    }
    if (url.includes('/api/v1/scenes')) return okScenes(scenes)
    if (url.includes('/api/v1/sessions')) return okScenes([])
    if (url.includes('/api/v1/credits')) return creditsResponse()
    if (url.includes('/api/v1/agent/runs')) {
      return new Response(
        sseBody([
          'event: run_started\ndata: {"runId":"r1","sessionId":"s1","holdId":"h1"}\n\n',
          'event: run_settled\ndata: {"runId":"r1","holdId":"h1","artifactRef":"a1","amount":1}\n\n',
        ]),
        { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
      )
    }
    return new Response('not found', { status: 404 })
  })
}

function mountWorkspace(
  sceneCode: 'ecommerce' | 'xiaohongshu' | 'tech_digest' | 'tech_product',
) {
  return mountSceneWorkspace(sceneCode)
}

type FetchSpy = { mock: { calls: ReadonlyArray<unknown[]> } }

function billedRunBodies(fetchMock: FetchSpy) {
  return fetchMock.mock.calls
    .filter(([input]) => {
      const url = String(input)
      if (!url.includes('/api/v1/agent/runs')) return false
      return true
    })
    .map(([, init]) => JSON.parse(String((init as RequestInit).body)) as {
      skillId?: string
      sceneCode?: string
      text?: string
    })
}

async function sendPrompt(root: HTMLElement, text: string, skillIndex: number | null) {
  if (skillIndex != null) {
    const pills = root.querySelectorAll<HTMLButtonElement>(
      '[data-testid="session-quick-row"] .pill',
    )
    pills[skillIndex]?.click()
    await flushUi()
  }
  const area = root.querySelector('textarea[aria-label="继续提问"]') as HTMLTextAreaElement
  setTextareaValue(area, text)
  await flushUi()
  const send = root.querySelector('.session button[aria-label="发送"]') as HTMLButtonElement
  expect(send.disabled).toBe(false)
  send.click()
  await flushUi()
  await flushUi()
}

describe('Workspace skill send', () => {
  let unmount: (() => void) | undefined
  let fetchMock: ReturnType<typeof mockCatalog>

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    fetchMock = mockCatalog()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    clearToken()
  })

  it('posts skillId when capsule selected (ecommerce)', async () => {
    const mounted = await mountWorkspace('ecommerce')
    unmount = mounted.unmount
    expect(mounted.root.querySelector('[data-demo]')).toBeNull()
    await sendPrompt(mounted.root, '帮我做家居选品', 0)
    const bodies = billedRunBodies(fetchMock)
    expect(bodies.length).toBeGreaterThanOrEqual(1)
    expect(bodies[0]?.sceneCode).toBe('ecommerce')
    expect(bodies[0]?.skillId).toBe('ecommerce-picklist')
    expect(bodies[0]?.text).toBe('帮我做家居选品')
  })

  it('omits skillId when no capsule selected', async () => {
    const mounted = await mountWorkspace('ecommerce')
    unmount = mounted.unmount
    await sendPrompt(mounted.root, '随便聊聊天气', null)
    const bodies = billedRunBodies(fetchMock)
    expect(bodies.length).toBeGreaterThanOrEqual(1)
    expect(bodies[0]?.sceneCode).toBe('ecommerce')
    expect(bodies[0]?.skillId).toBeUndefined()
  })

  it('xiaohongshu topic capsule posts xhs-topiclist', async () => {
    const mounted = await mountWorkspace('xiaohongshu')
    unmount = mounted.unmount
    await sendPrompt(mounted.root, '帮我做桌搭选题', 0)
    const bodies = billedRunBodies(fetchMock)
    expect(bodies.length).toBeGreaterThanOrEqual(1)
    expect(bodies[0]?.sceneCode).toBe('xiaohongshu')
    expect(bodies[0]?.skillId).toBe('xhs-topiclist')
    expect(bodies[0]?.text).toBe('帮我做桌搭选题')
  })

  it('tech_digest shows 链接速读 capsule and posts tech-digest', async () => {
    const mounted = await mountWorkspace('tech_digest')
    unmount = mounted.unmount
    expect(mounted.root.querySelector('[data-demo]')).toBeNull()
    const pills = mounted.root.querySelectorAll('[data-testid="session-quick-row"] .pill')
    expect(pills.length).toBeGreaterThanOrEqual(1)
    expect(pills[0]?.textContent?.trim()).toBe('链接速读')
    await sendPrompt(mounted.root, '速读这个链接 https://example.com', 0)
    const bodies = billedRunBodies(fetchMock)
    expect(bodies.length).toBeGreaterThanOrEqual(1)
    expect(bodies[0]?.sceneCode).toBe('tech_digest')
    expect(bodies[0]?.skillId).toBe('tech-digest')
    expect(bodies[0]?.text).toBe('速读这个链接 https://example.com')
  })

  it('tech_product shows 竞品分析 + 产品早报 capsules; default posts tech-competitor', async () => {
    const mounted = await mountWorkspace('tech_product')
    unmount = mounted.unmount
    const pills = mounted.root.querySelectorAll('[data-testid="session-quick-row"] .pill')
    expect(pills.length).toBeGreaterThanOrEqual(2)
    expect(pills[0]?.textContent?.trim()).toBe('竞品分析')
    expect(pills[1]?.textContent?.trim()).toBe('产品早报')
    await sendPrompt(mounted.root, '拆解 https://www.notion.so', 0)
    const bodies = billedRunBodies(fetchMock)
    expect(bodies.length).toBeGreaterThanOrEqual(1)
    expect(bodies[0]?.sceneCode).toBe('tech_product')
    expect(bodies[0]?.skillId).toBe('tech-competitor')
  })

  it('tech_digest session replay STATUS can open Computer preview', async () => {
    const digestView = {
      version: 2,
      title: '科技速读',
      format: 'html',
      content: '<article><p>摘要正文</p></article>',
    }
    fetchMock.mockRestore()
    fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/scenes/') && url.includes('/skills')) {
        return okScenes(TECH_DIGEST_SKILLS)
      }
      if (url.includes('/api/v1/scenes')) return okScenes([TECH_DIGEST])
      if (url.includes('/api/v1/credits')) return creditsResponse()
      if (url.includes('/latest-artifact')) {
        return okScenes({
          id: 'td-1',
          artifactType: 'tech_digest',
          sceneCode: 'tech_digest',
          title: '科技速读',
          createdAt: '2026-10-04T12:00:00Z',
          view: digestView,
          sessionId: 'sess-td',
        })
      }
      if (url.includes('/messages')) {
        return okScenes({
          items: [
            {
              runId: 'run-td',
              userPrompt: '请速读这个链接 https://github.com/xiaoyanw/forma',
              messages: [
                {
                  role: 'user',
                  content: '请速读这个链接 https://github.com/xiaoyanw/forma',
                  createdAt: '2026-10-04T12:00:00Z',
                },
                {
                  role: 'assistant',
                  content:
                    '```json\n{"artifactType":"tech_digest","view":{"version":2,"title":"科技速读","format":"html","content":"<p>x</p>"},"artifact":{"source":"fetch","sourceUrl":"https://github.com/xiaoyanw/forma","excerpts":[{"heading":"h","quotes":["q"]}]}}\n```',
                  createdAt: '2026-10-04T12:00:10Z',
                },
              ],
            },
          ],
          nextToken: null,
        })
      }
      if (url.includes('/api/v1/sessions')) {
        return okScenes([
          {
            sessionId: 'sess-td',
            title: '请速读这个链接',
            sceneCode: 'tech_digest',
            updatedAt: '2026-10-04T12:00:00Z',
          },
        ])
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountWorkspace('tech_digest')
    unmount = mounted.unmount
    await flushUi()
    const item = mounted.root.querySelector('[data-testid="session-item"]') as HTMLButtonElement
    expect(item).toBeTruthy()
    item.click()
    await flushUi()
    await flushUi()

    const chat = mounted.root.querySelector('.chat-scroll')?.textContent || ''
    expect(chat).toMatch(/已生成速读摘要/)
    expect(chat).not.toMatch(/已生成选品成果/)
    const status = mounted.root.querySelector('.chat-event-status')
    expect(status?.classList.contains('is-preview')).toBe(true)
    expect(status?.querySelector('.chat-stream-toggle')?.textContent).toMatch(/查看|关闭/)
  })
})
