import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import Workspace from '@/views/business/scene/Workspace.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

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
  status: 'AVAILABLE',
  sortOrder: 1,
  summary: '选品与上架',
}

const XHS = {
  bizId: 'a1000001-0001-4000-8000-000000000003',
  sceneCode: 'xiaohongshu',
  displayName: '小红书种草',
  status: 'AVAILABLE',
  sortOrder: 3,
  summary: '笔记结构与种草表达',
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

function mockCatalog() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes/') && url.includes('/skills')) {
      const skills = url.includes('xiaohongshu') ? XHS_SKILLS : ECOMMERCE_SKILLS
      return okScenes(skills)
    }
    if (url.includes('/api/v1/scenes')) return okScenes([ECOMMERCE, XHS])
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

async function mountWorkspace(sceneCode: 'ecommerce' | 'xiaohongshu') {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/scenes', name: 'scenes', component: { template: '<div>gallery</div>' } },
      {
        path: '/scenes/:sceneCode',
        name: 'scene-workspace',
        component: Workspace,
      },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push(`/scenes/${sceneCode}`)
  await router.isReady()
  const app = createApp(Workspace)
  app.use(router)
  app.mount(root)
  await flushUi()
  await flushUi()
  return {
    root,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

type FetchSpy = { mock: { calls: ReadonlyArray<unknown[]> } }

function billedRunBodies(fetchMock: FetchSpy) {
  return fetchMock.mock.calls
    .filter(([input, init]) => {
      const url = String(input)
      if (!url.includes('/api/v1/agent/runs')) return false
      const body = typeof (init as RequestInit | undefined)?.body === 'string'
        ? String((init as RequestInit).body)
        : ''
      return !body.includes('"dryRun":true')
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
})
