import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import LandingPage from '@/views/marketing/LandingPage.vue'
import { AI_DISCLAIMER_SHORT } from '@/constants/compliance'

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
      { path: '/scenes', name: 'scenes', component: { template: '<div>scenes</div>' } },
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

  it('renders Manus-style centered prompt home', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const headline = mounted.root.querySelector('h1.headline')
    expect(headline?.textContent?.trim()).toBe('我能为你做什么？')
    expect(mounted.root.textContent).toMatch(/一起开始创作/)
    expect(mounted.root.textContent).toMatch(/选品清单/)
    expect(mounted.root.textContent).toMatch(/生成上架素材/)
    expect(mounted.root.querySelector('.prompt')).toBeTruthy()
    // Manus order: prompt first, pills below
    const stage = mounted.root.querySelector('.stage')
    const prompt = stage?.querySelector('.prompt')
    const quick = stage?.querySelector('.quick')
    expect(prompt && quick && (prompt.compareDocumentPosition(quick) & Node.DOCUMENT_POSITION_FOLLOWING)).toBeTruthy()
    expect(mounted.root.querySelector('ol.picks')).toBeNull()
  })

  it('shows AI compliance disclaimer in footer', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const foot = mounted.root.querySelector('[data-testid="ai-disclaimer-footer"]')
    expect(foot?.textContent).toContain(AI_DISCLAIMER_SHORT)
    expect(foot?.textContent).toMatch(/人工复核/)
    expect(foot?.textContent).toMatch(/不承诺销售效果/)
    expect(mounted.root.querySelector('h1.headline')?.textContent?.trim()).toBe('我能为你做什么？')
  })

  it('guest: prompt and pills navigate to login; register link works', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    const prompt = mounted.root.querySelector('.prompt') as HTMLButtonElement | null
    expect(prompt).toBeTruthy()
    expect(prompt?.tagName).toBe('BUTTON')

    prompt?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'login' })

    mounted.push.mockClear()
    const pills = Array.from(mounted.root.querySelectorAll('.pill'))
    expect(pills.length).toBeGreaterThanOrEqual(2)
    pills[0]?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'login' })

    mounted.push.mockClear()
    const registerBtn = Array.from(mounted.root.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === '注册',
    )
    registerBtn?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'register' })
  })

  it('logged-in: stays on landing; prompt goes to scenes', async () => {
    setToken('jwt-demo')
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.router.currentRoute.value.name).toBe('landing')
    expect(mounted.root.querySelector('h1.headline')?.textContent?.trim()).toBe('我能为你做什么？')

    const prompt = mounted.root.querySelector('.prompt')
    prompt?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushUi()
    expect(mounted.push).toHaveBeenCalledWith({ name: 'scenes' })
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

  it('narrow viewport still shows headline and prompt', async () => {
    const mounted = await mountLanding()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('h1.headline')).toBeTruthy()
    expect(mounted.root.querySelector('.prompt')).toBeTruthy()
    expect(mounted.root.querySelectorAll('.pill').length).toBeGreaterThanOrEqual(2)
  })
})
