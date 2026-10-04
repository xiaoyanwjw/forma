import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, getToken, setToken } from '@/api/http'
import SceneGallery from '@/views/business/scene/SceneGallery.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

const SIX_SCENES = [
  {
    bizId: 'a1000001-0001-4000-8000-000000000001',
    sceneCode: 'ecommerce',
    displayName: '电商开店',
    category: 'ecommerce',
    status: 'AVAILABLE',
    sortOrder: 1,
    summary: '选品与上架素材：带理由的候选清单，以及可直接用的主图和详情。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000003',
    sceneCode: 'xiaohongshu',
    displayName: '小红书种草',
    category: 'content',
    status: 'AVAILABLE',
    sortOrder: 2,
    summary: '笔记结构与种草表达：帮你写标题、正文与更像真人分享的草稿。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000002',
    sceneCode: 'short_video',
    displayName: '短视频带货',
    category: 'content',
    status: 'COMING_SOON',
    sortOrder: 3,
    summary: '脚本、镜头与带货选品：帮你定拍什么、怎么讲、带哪款货。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000005',
    sceneCode: 'tech_digest',
    displayName: '科技速读',
    category: 'tech',
    status: 'AVAILABLE',
    sortOrder: 1,
    summary: '丢产品页、AI 文章或技术文档链接：解析正文，一页摘要带走。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000006',
    sceneCode: 'sports_gear',
    displayName: '装备选购对比',
    category: 'sports',
    status: 'COMING_SOON',
    sortOrder: 5,
    summary: '跑鞋、球拍怎么选：对比表 + 一句话推荐，帮你少踩坑。',
  },
  {
    bizId: 'a1000001-0001-4000-8000-000000000004',
    sceneCode: 'weekend_trip',
    displayName: '周末行程',
    category: 'life',
    status: 'COMING_SOON',
    sortOrder: 6,
    summary: '半天到一天怎么玩：路线、时段和吃饭点，一页带走。',
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
        path: '/scenes/:sceneCode',
        name: 'scene-workspace',
        component: { template: '<div>workspace</div>' },
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

  it('renders 3 live + 3 soon cards in category-tab order; header scenes is current', async () => {
    setToken('jwt')
    const shuffled = [
      SIX_SCENES[2],
      SIX_SCENES[0],
      SIX_SCENES[5],
      SIX_SCENES[1],
      SIX_SCENES[4],
      SIX_SCENES[3],
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
    expect(mounted.root.textContent).toMatch(/选一个场景，开始创作/)
    expect(mounted.root.textContent).not.toMatch(/示意|近端|空壳/)

    const tabs = Array.from(mounted.root.querySelectorAll('.category-tab')).map((t) =>
      t.textContent?.trim(),
    )
    expect(tabs).toEqual(['全部', '科技', '电商', '内容', '体育', '生活'])
    expect(mounted.root.querySelector('.category-tab.on')?.textContent?.trim()).toBe('全部')

    const gridCards = Array.from(mounted.root.querySelectorAll('.scene-grid > .scene-card'))
    expect(gridCards.map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual([
      '科技速读',
      '电商开店',
      '小红书种草',
      '短视频带货',
      '装备选购对比',
      '周末行程',
    ])

    const live = mounted.root.querySelectorAll('.scene-card.live')
    const soon = mounted.root.querySelectorAll('.scene-card.soon')
    expect(live.length).toBe(3)
    expect(soon.length).toBe(3)
    expect(live[0]?.textContent).toMatch(/科技速读/)
    expect(live[0]?.textContent).toMatch(/可用/)
    expect(live[0]?.textContent).not.toMatch(/开始使用/)
    soon.forEach((card) => {
      expect(card.tagName).toBe('BUTTON')
      expect(card.textContent).toMatch(/即将推出/)
    })
    expect(mounted.root.querySelectorAll('a.scene-card.soon').length).toBe(0)

    const tech = live[0] as HTMLAnchorElement
    expect(tech.getAttribute('href')).toBe('/scenes/tech_digest')
    tech.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('scene-workspace')
    expect(mounted.router.currentRoute.value.params.sceneCode).toBe('tech_digest')
    expect(mounted.root.querySelector('.toast.show')).toBeNull()

    const firstSoon = soon[0] as HTMLButtonElement
    expect(firstSoon.textContent).toMatch(/短视频带货/)
    firstSoon.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.params.sceneCode).toBe('tech_digest')

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

  it('category tab filters cards; empty category shows empty copy', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(SIX_SCENES))
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

    const techTab = Array.from(mounted.root.querySelectorAll('.category-tab')).find(
      (t) => t.textContent?.trim() === '科技',
    ) as HTMLButtonElement
    techTab.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()

    expect(techTab.classList.contains('on')).toBe(true)
    const techCards = Array.from(mounted.root.querySelectorAll('.scene-grid > .scene-card'))
    expect(techCards.map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual(['科技速读'])
    expect(techCards[0]?.classList.contains('live')).toBe(true)
    expect((techCards[0] as HTMLAnchorElement).getAttribute('href')).toBe('/scenes/tech_digest')

    const contentTab = Array.from(mounted.root.querySelectorAll('.category-tab')).find(
      (t) => t.textContent?.trim() === '内容',
    ) as HTMLButtonElement
    contentTab.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(
      Array.from(mounted.root.querySelectorAll('.scene-grid > .scene-card')).map((c) =>
        c.querySelector('h2')?.textContent?.trim(),
      ),
    ).toEqual(['小红书种草', '短视频带货'])
  })

  it('coming-soon toast updates on another gray card and auto-dismisses', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(SIX_SCENES))
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
      expect(toast?.textContent).toMatch(/「装备选购对比」马上就来/)
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
          return Promise.resolve(okScenes(SIX_SCENES))
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
    const live = liveCards.find((c) => c.textContent?.includes('电商开店')) as HTMLAnchorElement | undefined
    expect(live?.getAttribute('href')).toBe('/scenes/ecommerce')
    live?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }))
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('scene-workspace')
    expect(mounted.router.currentRoute.value.params.sceneCode).toBe('ecommerce')
  })

  it('AVAILABLE xiaohongshu card navigates to Xiaohongshu workspace', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/scenes')) {
          return Promise.resolve(okScenes(SIX_SCENES))
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
    expect(mounted.router.currentRoute.value.name).toBe('scene-workspace')
    expect(mounted.router.currentRoute.value.params.sceneCode).toBe('xiaohongshu')
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
    expect(mounted.root.querySelector('.category-tabs')).toBeNull()
    expect(mounted.root.textContent).toMatch(/登录/)
    const goLogin = Array.from(mounted.root.querySelectorAll('button')).find((b) =>
      b.textContent?.trim() === '登录',
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
      vi.fn().mockImplementation(() => Promise.resolve(okScenes(SIX_SCENES))),
    )
    const mounted = await mountGallery()
    unmount = mounted.unmount
    await flushUi()
    expect(mounted.root.querySelector('.scene-grid')).toBeTruthy()
  })
})
