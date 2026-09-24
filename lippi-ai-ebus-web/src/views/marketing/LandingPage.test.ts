import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import LandingPage from '@/views/marketing/LandingPage.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

async function mountLanding() {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: LandingPage },
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>register</div>' } },
      { path: '/credits', name: 'credits', component: { template: '<div>credits</div>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const push = vi.spyOn(router, 'push')
  const app = createApp(LandingPage)
  app.use(router)
  app.mount(root)
  return {
    root,
    router,
    push,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

describe('LandingPage', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
  })

  it('renders hero Adam and sample pick list', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const brand = mounted.root.querySelector('h1.brand')
    expect(brand?.textContent?.trim()).toBe('Adam')
    const picks = mounted.root.querySelectorAll('ol.picks li')
    expect(picks.length).toBeGreaterThanOrEqual(6)
    expect(mounted.root.textContent).toMatch(/选品清单/)
  })

  it('guest CTA: login and register buttons navigate', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const buttons = Array.from(mounted.root.querySelectorAll('button'))
    const labels = buttons.map((b) => b.textContent?.trim() ?? '')
    expect(labels).toContain('登录')
    expect(labels).toContain('注册')
    expect(labels.some((t) => t.includes('→'))).toBe(false)

    const loginBtn = buttons.find((b) => b.textContent?.trim() === '登录')
    const registerBtn = buttons.find((b) => b.textContent?.trim() === '注册')
    expect(loginBtn).toBeTruthy()
    expect(registerBtn).toBeTruthy()

    loginBtn?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'login' })

    mounted.push.mockClear()
    registerBtn?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'register' })
  })

  it('logged-in CTA goes to credits without redirecting away from landing', async () => {
    setToken('jwt-demo')
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.router.currentRoute.value.name).toBe('landing')
    const buttons = Array.from(mounted.root.querySelectorAll('button'))
    const creditsBtn = buttons.find((b) => b.textContent?.includes('套餐'))
    expect(creditsBtn).toBeTruthy()
    expect(buttons.some((b) => b.textContent?.trim() === '登录')).toBe(false)

    creditsBtn?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'credits' })
  })

  it('has no all-caps eyebrow and no arrow-stacked button labels', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const eyebrowish = Array.from(mounted.root.querySelectorAll('p, span, small, label')).filter(
      (el) => {
        const t = (el.textContent ?? '').trim()
        return t.length > 0 && t.length < 24 && t === t.toUpperCase() && /[A-Z]{3,}/.test(t)
      },
    )
    expect(eyebrowish).toHaveLength(0)

    const withArrow = Array.from(mounted.root.querySelectorAll('button')).filter((b) =>
      (b.textContent ?? '').includes('→'),
    )
    expect(withArrow).toHaveLength(0)
  })

  it('narrow viewport still shows brand and pick sheet (stack-friendly)', async () => {
    // happy-dom 默认窄视口；移动优先布局须同时露出品牌与清单交付物
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('.hero .brand')).toBeTruthy()
    expect(mounted.root.querySelector('aside.sheet ol.picks li')).toBeTruthy()
    expect(mounted.root.querySelectorAll('ol.picks li').length).toBeGreaterThanOrEqual(6)
  })
})
