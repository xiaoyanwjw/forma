import { createApp, nextTick, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, getToken, setToken } from '@/api/http'
import { getCredits } from '@/api/business/credit/credit'
import CreditPlan from '@/views/business/credit/CreditPlan.vue'
import ScenePlaceholder from '@/views/business/scene/ScenePlaceholder.vue'
import HistoryPlaceholder from '@/views/business/history/HistoryPlaceholder.vue'
import {
  CREDIT_PLAN_ROWS,
  INSUFFICIENT_CREDITS_HINT,
  creditTierLabel,
  formatNextResetAtShanghai,
} from '@/types/business/credit'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

function assertAppHeaderSlots(root: HTMLElement) {
  const header = root.querySelector('header.app-header')
  expect(header).toBeTruthy()
  expect(header?.textContent).toMatch(/Adam/)
  expect(header?.textContent).toMatch(/场景/)
  expect(header?.textContent).toMatch(/历史/)
  expect(header?.textContent).toMatch(/套餐/)
}

async function mountShell(component: Component, path: string) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/register', name: 'register', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: CreditPlan },
      { path: '/scenes', name: 'scenes', component: ScenePlaceholder },
      { path: '/history', name: 'history', component: HistoryPlaceholder },
    ],
  })
  await router.push(path)
  await router.isReady()
  const app = createApp(component)
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

async function mountCreditPlan() {
  return mountShell(CreditPlan, '/credits')
}

function okCredits(data: Record<string, unknown>) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('credits FE', () => {
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

  it('getCredits GETs /api/v1/credits with JWT', async () => {
    setToken('jwt-credits')
    const fetchMock = vi.fn().mockResolvedValue(
      okCredits({
        tier: 'FREE',
        available: 20,
        balance: 20,
        reserved: 0,
        nextResetAt: '2026-10-24T10:00:00Z',
        periodAnchorAt: '2026-09-24T10:00:00Z',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const data = await getCredits()
    const firstCall = fetchMock.mock.calls[0]
    expect(firstCall?.[0]).toBe('/api/v1/credits')
    const headers = firstCall?.[1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-credits')
    expect(data.available).toBe(20)
  })

  it('formatNextResetAtShanghai pins Asia/Shanghai readable string', () => {
    expect(formatNextResetAtShanghai('2026-10-24T10:00:00Z')).toBe('2026/10/24 18:00')
  })

  it('static pricing has three plan cards data', () => {
    expect(CREDIT_PLAN_ROWS.map((r) => r.tier)).toEqual(['FREE', 'PRO', 'PLUS'])
    expect(creditTierLabel('FREE')).toBe('免费')
  })

  it('CreditPlan: renders three plan cards and marks current tier', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 20,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    assertAppHeaderSlots(mounted.root)
    const cards = mounted.root.querySelectorAll('.plan-card')
    expect(cards).toHaveLength(3)
    const current = mounted.root.querySelector('.plan-card.current')
    expect(current?.textContent).toContain('免费')
    expect(current?.textContent).toContain('当前套餐')
    expect(mounted.root.textContent).not.toContain('立即购买')
  })

  it('CreditPlan: no JWT skips fetch and shows login guide', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(mounted.root.textContent).toContain('未登录')
    expect(mounted.root.textContent).toContain('去登录')
  })

  it('CreditPlan: 401 clears token and shows login guide', async () => {
    setToken('bad-jwt')
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
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(getToken()).toBeNull()
    expect(mounted.root.textContent).toContain('未授权，请先登录')
    expect(mounted.root.textContent).toContain('去登录')
  })

  it('CreditPlan: available===0 shows insufficient hint', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 0,
            balance: 0,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toContain(INSUFFICIENT_CREDITS_HINT)
  })

  it('CreditPlan: reserved>0 still shows available as primary', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 7,
            balance: 10,
            reserved: 3,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('.available')?.textContent).toBe('7')
    expect(mounted.root.textContent).toContain('预占中 3')
  })

  it('CreditPlan: formats nextResetAt in Asia/Shanghai', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          okCredits({
            tier: 'FREE',
            available: 20,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        ),
      ),
    )
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toContain('2026/10/24 18:00')
  })

  it('CreditPlan: null credits payload shows error', async () => {
    setToken('jwt')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data: null }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      ),
    )
    const mounted = await mountCreditPlan()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toContain('无法获取积分信息')
    expect(mounted.root.querySelector('.available')).toBeNull()
  })

  it('ScenePlaceholder mounts AppHeader with nav slots', async () => {
    const mounted = await mountShell(ScenePlaceholder, '/scenes')
    unmount = mounted.unmount
    await flushUi()
    assertAppHeaderSlots(mounted.root)
  })

  it('HistoryPlaceholder mounts AppHeader with nav slots', async () => {
    const mounted = await mountShell(HistoryPlaceholder, '/history')
    unmount = mounted.unmount
    await flushUi()
    assertAppHeaderSlots(mounted.root)
  })
})
