import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import AppHeader from '@/components/common/AppHeader.vue'

const tokensCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../styles/tokens.css'),
  'utf8',
)

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

function okCredits(data: Record<string, unknown> | null) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function mountHeader(props: Record<string, unknown> = {}) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/register', name: 'register', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/scenes', name: 'scenes', component: { template: '<div />' } },
      { path: '/history', name: 'history', component: { template: '<div />' } },
    ],
  })
  await router.push('/credits')
  await router.isReady()
  const app = createApp(AppHeader, props)
  app.use(router)
  app.mount(root)
  return {
    root,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

function injectTokensStylesheet(): HTMLStyleElement {
  const style = document.createElement('style')
  style.setAttribute('data-testid', 'tokens-css')
  style.textContent = tokensCss
  document.head.appendChild(style)
  return style
}

describe('AppHeader', () => {
  let unmount: (() => void) | undefined
  let tokensStyle: HTMLStyleElement | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    tokensStyle = injectTokensStylesheet()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
    tokensStyle?.remove()
    tokensStyle = undefined
  })

  it('renders full-bleed slots: logo, scenes, history, plans; right rail for logged-in', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountHeader({ activeNav: 'credits' })
    unmount = mounted.unmount
    await flushUi()

    const header = mounted.root.querySelector('header.app-header')
    expect(header).toBeTruthy()
    expect(header?.className).not.toMatch(/capsule|island|pill-bar/i)

    const style = window.getComputedStyle(header as Element)
    expect(style.maxWidth === '' || style.maxWidth === 'none' || style.maxWidth === '100%').toBe(
      true,
    )

    expect(mounted.root.textContent).toMatch(/Adam/)
    expect(mounted.root.textContent).toMatch(/场景/)
    expect(mounted.root.textContent).toMatch(/历史/)
    expect(mounted.root.textContent).toMatch(/套餐/)
    expect(mounted.root.textContent).toMatch(/升级/)
    expect(mounted.root.querySelector('[aria-label="账户"]')).toBeTruthy()
    const logo = mounted.root.querySelector('a.logo')
    expect(logo?.getAttribute('href')).toBe('/scenes')
    expect(logo?.getAttribute('aria-label')).toBe('Adam 场景')
  })

  it('activeNav applies .on and aria-current=page on the active control', async () => {
    const mounted = await mountHeader({ activeNav: 'credits' })
    unmount = mounted.unmount
    await flushUi()

    const plans = Array.from(mounted.root.querySelectorAll('a.nav-link')).find((a) =>
      a.textContent?.includes('套餐'),
    )
    expect(plans?.classList.contains('on')).toBe(true)
    expect(plans?.getAttribute('aria-current')).toBe('page')
  })

  it('scenes links to scenes gallery; history is not a navigable link', async () => {
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    const sceneLink = Array.from(mounted.root.querySelectorAll('a')).find((a) =>
      a.textContent?.includes('场景'),
    )
    expect(sceneLink?.getAttribute('href')).toBe('/scenes')

    const historyLink = Array.from(mounted.root.querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === '历史',
    )
    expect(historyLink).toBeUndefined()
    expect(mounted.root.querySelector('[data-nav="history"]')).toBeTruthy()
  })

  it('breadcrumb mode replaces scenes link with scene path', async () => {
    const mounted = await mountHeader({ sceneBreadcrumb: '  电商开店  ' })
    unmount = mounted.unmount
    await flushUi()

    const crumb = mounted.root.querySelector('[aria-label="面包屑"]')
    expect(crumb?.textContent).toMatch(/场景/)
    expect(crumb?.textContent).toMatch(/电商开店/)
    const back = crumb?.querySelector('a')
    expect(back?.getAttribute('href')).toBe('/scenes')
  })

  it('whitespace-only sceneBreadcrumb stays in default nav mode', async () => {
    const mounted = await mountHeader({ sceneBreadcrumb: '   ' })
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('[aria-label="面包屑"]')).toBeNull()
    expect(
      Array.from(mounted.root.querySelectorAll('a')).some((a) => a.textContent?.trim() === '场景'),
    ).toBe(true)
  })

  it('guest right rail shows login/register without fake credit balance', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(mounted.root.textContent).toMatch(/登录/)
    expect(mounted.root.textContent).toMatch(/注册/)
    expect(mounted.root.querySelector('.credits-chip')).toBeNull()
  })

  it('logged-in shows credit chip from API; API failure stays usable', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    const chip = mounted.root.querySelector('.credits-chip')
    expect(chip?.textContent).toMatch(/免费/)
    expect(chip?.textContent).toMatch(/14/)
    expect(mounted.root.textContent).not.toMatch(/示意|近端|空壳/)
  })

  it('null credits data shows 积分暂不可用', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(okCredits(null))),
    )
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('.credits-chip')?.textContent).toBe('积分暂不可用')
  })

  it('credit API failure does not crash header; shows soft hint', async () => {
    setToken('jwt')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')))
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('header.app-header')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/升级/)
    expect(mounted.root.querySelector('[aria-label="账户"]')).toBeTruthy()
    const chip = mounted.root.querySelector('.credits-chip')
    expect(chip?.textContent).toMatch(/积分/)
  })

  it('clearToken notifies auth and switches header to guest rail', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('[aria-label="账户"]')).toBeTruthy()

    clearToken()
    await flushUi()

    expect(mounted.root.querySelector('[aria-label="账户"]')).toBeNull()
    expect(mounted.root.textContent).toMatch(/登录/)
    expect(mounted.root.textContent).toMatch(/注册/)
    expect(mounted.root.querySelector('.credits-chip')).toBeNull()
  })

  it('applies design tokens at runtime including --canvas and :focus-visible', () => {
    const canvas = getComputedStyle(document.documentElement).getPropertyValue('--canvas').trim()
    expect(canvas.toLowerCase()).toBe('#fafafa')
    const font = getComputedStyle(document.documentElement).getPropertyValue('--font')
    expect(font).toMatch(/Instrument Sans/)

    const focusRules = Array.from(document.styleSheets).flatMap((sheet) => {
      try {
        return Array.from(sheet.cssRules)
          .filter((r): r is CSSStyleRule => r instanceof CSSStyleRule)
          .filter((r) => r.selectorText.includes('focus'))
          .map((r) => r.cssText)
      } catch {
        return []
      }
    })
    expect(focusRules.join('\n')).toMatch(/:focus-visible/)
    expect(focusRules.join('\n')).toMatch(/outline/)
  })

  it('header interactive controls can take keyboard focus', async () => {
    const mounted = await mountHeader()
    unmount = mounted.unmount
    await flushUi()

    const sceneLink = Array.from(mounted.root.querySelectorAll('a')).find((a) =>
      a.textContent?.includes('场景'),
    ) as HTMLElement | undefined
    expect(sceneLink).toBeTruthy()
    sceneLink!.focus()
    expect(document.activeElement).toBe(sceneLink)

    const plans = Array.from(mounted.root.querySelectorAll('a')).find((a) =>
      a.textContent?.includes('套餐'),
    ) as HTMLElement | undefined
    plans!.focus()
    expect(document.activeElement).toBe(plans)
  })
})
