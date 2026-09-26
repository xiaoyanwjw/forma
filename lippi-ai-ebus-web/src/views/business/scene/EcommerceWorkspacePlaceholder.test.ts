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

function okScenes(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function mockCatalogAndCredits() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/api/v1/scenes')) {
      return okScenes([ECOMMERCE])
    }
    if (url.includes('/api/v1/credits')) {
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

function generationApiHits(fetchMock: ReturnType<typeof vi.spyOn>) {
  return fetchMock.mock.calls.filter(([input]) => {
    const url = String(input)
    return (
      url.includes('/api/v1/agent') ||
      url.includes('empty-run') ||
      url.includes('emptyRun') ||
      url.includes('/api/v1/generation')
    )
  })
}

describe('EcommerceWorkspacePlaceholder empty state', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    mockCatalogAndCredits()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    clearToken()
  })

  it('shows empty-state copy, capsules, ask shell, and scene breadcrumb', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount

    expect(mounted.root.querySelector('h1')?.textContent).toBe('我能为你做什么？')
    const pills = [...mounted.root.querySelectorAll('.home .pill')].map((el) =>
      el.textContent?.trim(),
    )
    expect(pills).toEqual(['选品清单', '生成上架素材'])
    expect(mounted.root.querySelector('.home textarea')?.getAttribute('placeholder')).toBe(
      '分配一个任务或提问任何问题',
    )

    const crumb = mounted.root.querySelector('.scene-switch')
    expect(crumb?.getAttribute('aria-label')).toBe('面包屑')
    expect(crumb?.textContent?.replace(/\s+/g, ' ').trim()).toContain('场景')
    expect(crumb?.textContent).toContain('电商开店')
    const sceneLink = crumb?.querySelector('a')
    expect(sceneLink?.getAttribute('href')).toBe('/scenes')

    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')
  })

  it('keeps attach disabled; send enabled only with prompt text', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const send = mounted.root.querySelector(
      '.home button[aria-label="发送"]',
    ) as HTMLButtonElement
    const attach = mounted.root.querySelector(
      '.home button[aria-label="附件"]',
    ) as HTMLButtonElement
    expect(attach.disabled).toBe(true)
    expect(send.disabled).toBe(true)

    const area = mounted.root.querySelector('.home textarea') as HTMLTextAreaElement
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

  it('still shows empty state with sceneCode when catalog fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/scenes')) {
        return new Response(JSON.stringify({ success: false, code: 500, message: 'fail' }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/api/v1/credits')) {
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
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('h1')?.textContent).toBe('我能为你做什么？')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-biz-id')).toBeNull()
  })
})

describe('EcommerceWorkspacePlaceholder session shell (3.3)', () => {
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
    const area = root.querySelector('.home textarea') as HTMLTextAreaElement
    setTextareaValue(area, text)
    await flushUi()
    const send = root.querySelector('.home button[aria-label="发送"]') as HTMLButtonElement
    expect(send.disabled).toBe(false)
    send.click()
    await flushUi()
  }

  it('enters session from send: sidebar + chat demo messages, Computer hidden, breadcrumb unchanged', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length

    await enterViaSend(mounted.root)

    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.sidebar')).toBeTruthy()
    expect(mounted.root.querySelector('.side-new')?.textContent).toContain('新任务')
    expect(mounted.root.querySelectorAll('.side-item').length).toBe(1)
    expect(mounted.root.querySelector('.side-item')?.textContent).toBeTruthy()

    const thread = mounted.root.querySelector('.chat-scroll')
    expect(thread?.textContent).toContain('帮我做家居选品')
    expect(thread?.querySelector('.msg.user')).toBeTruthy()
    expect(thread?.querySelector('.msg.agent')).toBeTruthy()

    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)

    const crumb = mounted.root.querySelector('.scene-switch')
    expect(crumb?.textContent).toContain('场景')
    expect(crumb?.textContent).toContain('电商开店')
    expect(mounted.root.querySelector('.shell')?.getAttribute('data-scene-code')).toBe('ecommerce')

    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('enters session from picks capsule without generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length
    const pathBefore = mounted.router.currentRoute.value.fullPath

    const picks = mounted.root.querySelectorAll<HTMLButtonElement>('.home .pill')[0]
    picks!.click()
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/选品|清单/)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('enters session from listing capsule without generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const callsBefore = fetchMock.mock.calls.length
    const pathBefore = mounted.router.currentRoute.value.fullPath

    const listing = mounted.root.querySelectorAll<HTMLButtonElement>('.home .pill')[1]
    listing!.click()
    await flushUi()

    expect(mounted.root.querySelector('.session')).toBeTruthy()
    expect(mounted.root.querySelector('.home')).toBeNull()
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toMatch(/上架|素材/)
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('sendFromSession appends demo messages without generation API', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    const beforeUsers = mounted.root.querySelectorAll('.chat-scroll .msg.user').length
    const beforeAgents = mounted.root.querySelectorAll('.chat-scroll .msg.agent').length
    const callsBefore = fetchMock.mock.calls.length

    const area = mounted.root.querySelector(
      'textarea[aria-label="继续提问"]',
    ) as HTMLTextAreaElement
    setTextareaValue(area, '再问一句演示')
    await flushUi()
    const send = mounted.root.querySelector(
      '.session button[aria-label="发送"]',
    ) as HTMLButtonElement
    expect(send.disabled).toBe(false)
    send.click()
    await flushUi()

    expect(mounted.root.querySelectorAll('.chat-scroll .msg.user').length).toBe(beforeUsers + 1)
    expect(mounted.root.querySelectorAll('.chat-scroll .msg.agent').length).toBe(beforeAgents + 1)
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toContain('再问一句演示')
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('opens Computer with picklist fixture via demo control; closes without killing chat', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    const openPicks = mounted.root.querySelector(
      '[data-demo="open-picks"]',
    ) as HTMLButtonElement
    expect(openPicks).toBeTruthy()
    openPicks.click()
    await flushUi()

    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(true)
    const computer = mounted.root.querySelector('.computer')
    expect(computer?.textContent).toContain("Adam's Computer")
    expect(computer?.textContent).toMatch(/选品清单/)
    expect(computer?.querySelectorAll('.pick-list li').length).toBeGreaterThanOrEqual(3)

    const closeBtn = mounted.root.querySelector('#close-computer') as HTMLButtonElement
    closeBtn.click()
    await flushUi()

    expect(mounted.root.querySelector('.workspace')?.classList.contains('split')).toBe(false)
    expect(mounted.root.querySelector('.chat-scroll')?.textContent).toContain('帮我做家居选品')
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('opens Computer with listing preview via demo control', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root, '请生成上架素材')

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
    expect(body?.querySelector('.listing-copy, .listing-hero, .comp-card')).toBeTruthy()
    expect(generationApiHits(fetchMock)).toHaveLength(0)
  })

  it('new task returns to empty state, clears thread, closes Computer', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    await enterViaSend(mounted.root)

    ;(mounted.root.querySelector('[data-demo="open-picks"]') as HTMLButtonElement).click()
    await flushUi()
    expect(mounted.root.querySelector('.workspace.split')).toBeTruthy()

    ;(mounted.root.querySelector('.side-new') as HTMLButtonElement).click()
    await flushUi()

    expect(mounted.root.querySelector('.home')).toBeTruthy()
    expect(mounted.root.querySelector('.session')).toBeNull()
    expect(mounted.root.querySelector('h1')?.textContent).toBe('我能为你做什么？')
    expect(mounted.root.querySelector('.chat-scroll')).toBeNull()
    expect(mounted.root.querySelector('.workspace.split')).toBeNull()
  })

  it('sidebar CSS hides at ≤860px breakpoint (narrow readable chat)', () => {
    expect(sessionCss).toMatch(/max-width:\s*860px/)
    expect(sessionCss).toMatch(/\.session\s+\.sidebar\s*\{[^}]*display:\s*none/s)
    expect(sessionCss).toMatch(/max-width:\s*1100px/)
  })
})
