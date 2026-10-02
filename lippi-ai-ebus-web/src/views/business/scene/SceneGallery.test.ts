import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, getToken, setToken } from '@/api/http'
import SceneGallery from '@/views/business/scene/SceneGallery.vue'
import EcommerceWorkspace from '@/views/business/scene/EcommerceWorkspace.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

const FOUR_SCENES = [
  {
    bizId: 'a1000001-0001-4000-8000-000000000001',
    sceneCode: 'ecommerce',
    displayName: '电商开店',
    status: 'AVAILABLE',
    sortOrder: 1,
    summary: '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000002',
    sceneCode: 'short_video',
    displayName: '短视频带货',
    status: 'COMING_SOON',
    sortOrder: 2,
    summary: '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000003',
    sceneCode: 'xiaohongshu',
    displayName: '小红书种草',
    status: 'COMING_SOON',
    sortOrder: 3,
    summary: '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000004',
    sceneCode: 'local_life',
    displayName: '本地生活',
    status: 'COMING_SOON',
    sortOrder: 4,
    summary: '到店、团购与周边生意：帮你整理套餐卖点与上架说法。',
  },
]

function okScenes(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function mountGallery(startPath = '/scenes') {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>register</div>' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/scenes', name: 'scenes', component: SceneGallery },
      {
        path: '/scenes/ecommerce',
        name: 'scene-ecommerce',
        component: EcommerceWorkspace,
      },
      {
        path: '/scenes/xiaohongshu',
        name: 'scene-xiaohongshu',
        component: { template: '<div>xhs</div>' },
      },
      { path: '/history', name: 'history', component: { template: '<div />' } },
    ],
  })
  await router.push(startPath)
  await router.isReady()
  const app = createApp(SceneGallery)
  app.use(router)
  app.mount(root)
  return {
    root,
    router,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

describe('SceneGallery', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
    unmount?.()
    unmount = undefined
  })

  it('renders 1 live + 3 soon cards sorted by sortOrder; header scenes is current', async () => {
    setToken('jwt')
    const shuffled = [
      FOUR_SCENES[2],
      FOUR_SCENES[0],
      FOUR_SCENES[3],
      FOUR_SCENES[1],
    ]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(shuffled))
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okScenes({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.reject(new Error(`unexpected ${url}`))
      }),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toMatch(/今天想做什么/)
    expect(mounted.root.textContent).toMatch(/积分在账号里通用/)
    expect(mounted.root.textContent).not.toMatch(/示意|近端|空壳/)

    const gridCards = Array.from(mounted.root.querySelectorAll('.scene-grid > .scene-card'))
    expect(gridCards.map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual([
      '电商开店',
      '短视频带货',
      '小红书种草',
      '本地生活',
    ])

    const live = mounted.root.querySelectorAll('.scene-card.live')
    const soon = mounted.root.querySelectorAll('.scene-card.soon')
    expect(live.length).toBe(1)
    expect(soon.length).toBe(3)
    expect(live[0]?.textContent).toMatch(/电商开店/)
    expect(live[0]?.textContent).toMatch(/可用/)
    expect(live[0]?.textContent).not.toMatch(/开始使用/)
    soon.forEach((card) => {
      expect(card.tagName).toBe('BUTTON')
      expect(card.textContent).toMatch(/即将推出/)
    })
    expect(mounted.root.querySelectorAll('a.scene-card.soon').length).toBe(0)

    const firstSoon = soon[0] as HTMLButtonElement
    firstSoon.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('scenes')

    const toast = mounted.root.querySelector('.toast.show')
    expect(toast).toBeTruthy()
    expect(toast?.getAttribute('role')).toBe('status')
    expect(toast?.getAttribute('aria-live')).toBe('polite')
    expect(toast?.getAttribute('aria-hidden')).toBe('false')
    expect(toast?.textContent).toMatch(/「短视频带货」马上就来/)
    expect(toast?.textContent).toMatch(/你也可以先从电商开店开始/)
    expect(toast?.querySelector('a')).toBeNull()

    const sceneNav = Array.from(mounted.root.querySelectorAll('a.nav-link')).find(
      (a) => a.textContent?.trim() === '场景',
    )
    expect(sceneNav?.classList.contains('on')).toBe(true)
    expect(sceneNav?.getAttribute('aria-current')).toBe('page')
    expect(mounted.root.querySelector('[aria-label="账户"]')).toBeTruthy()
  })

  it('coming-soon toast updates on another gray card and auto-dismisses', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(FOUR_SCENES))
        }
        return Promise.resolve(
          okScenes({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    vi.useFakeTimers()
    try {
      const soonCards = mounted.root.querySelectorAll('.scene-card.soon')
      ;(soonCards[0] as HTMLButtonElement).dispatchEvent(
        new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }),
      )
      await nextTick()
      await nextTick()
      expect(mounted.root.querySelector('.toast.show')?.textContent).toMatch(/「短视频带货」马上就来/)

      ;(soonCards[1] as HTMLButtonElement).dispatchEvent(
        new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }),
      )
      await nextTick()
      await nextTick()
      const toast = mounted.root.querySelector('.toast.show')
      expect(toast?.textContent).toMatch(/「小红书种草」马上就来/)
      expect(toast?.getAttribute('aria-hidden')).toBe('false')

      await vi.advanceTimersByTimeAsync(4500)
      await nextTick()
      expect(mounted.root.querySelector('.toast.show')).toBeNull()
      expect(mounted.root.querySelector('.toast')?.getAttribute('aria-hidden')).toBe('true')
    } finally {
      vi.useRealTimers()
    }
  })

  it('live card navigates to reserved ecommerce workspace', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(FOUR_SCENES))
        }
        return Promise.resolve(
          okScenes({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    const live = mounted.root.querySelector('a.scene-card.live') as HTMLAnchorElement | null
    expect(live?.getAttribute('href')).toBe('/scenes/ecommerce')
    live?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('scene-ecommerce')
  })

  it('AVAILABLE xiaohongshu card navigates to Xiaohongshu workspace', async () => {
    setToken('jwt')
    const scenes = FOUR_SCENES.map((s) =>
      s.sceneCode === 'xiaohongshu' ? { ...s, status: 'AVAILABLE' } : s,
    )
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(scenes))
        }
        return Promise.resolve(
          okScenes({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    const liveCards = Array.from(mounted.root.querySelectorAll('a.scene-card.live'))
    const xhs = liveCards.find((c) => c.textContent?.includes('小红书种草')) as
      | HTMLAnchorElement
      | undefined
    expect(xhs?.getAttribute('href')).toBe('/scenes/xiaohongshu')
    xhs?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('scene-xiaohongshu')
  })

  it('401 clears token and shows login guide without fake cards', async () => {
    setToken('stale-jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({ success: false, code: 401, message: '未授权，请先登录' }),
            { status: 401, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
      ),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    expect(getToken()).toBeNull()
    expect(mounted.root.querySelectorAll('.scene-card').length).toBe(0)
    expect(mounted.root.textContent).toMatch(/登录/)
    const goLogin = Array.from(mounted.root.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('去登录'),
    )
    expect(goLogin).toBeTruthy()
    goLogin?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('login')
  })

  it('non-401 API failure shows human error without fake cards', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(
            new Response(
              JSON.stringify({ success: false, code: 500, message: '服务暂时不可用' }),
              { status: 500, headers: { 'Content-Type': 'application/json' } },
            ),
          )
        }
        return Promise.resolve(
          okScenes({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }),
    )

    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelectorAll('.scene-card').length).toBe(0)
    expect(mounted.root.textContent).toMatch(/服务暂时不可用|加载不了/)
  })

  it('scene-grid uses single column rule at max-width 700px', async () => {
    const { readFileSync } = await import('node:fs')
    const { dirname, join } = await import('node:path')
    const { fileURLToPath } = await import('node:url')
    const source = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), 'SceneGallery.vue'),
      'utf8',
    )
    expect(source).toMatch(/@media\s*\(max-width:\s*700px\)/)
    expect(source).toMatch(/grid-template-columns:\s*1fr/)

    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(okScenes(FOUR_SCENES))),
    )
    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()
    expect(mounted.root.querySelector('.scene-grid')).toBeTruthy()
  })
})
