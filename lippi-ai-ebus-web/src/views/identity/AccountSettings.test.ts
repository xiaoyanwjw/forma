import { createApp, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, getToken, setToken } from '@/api/http'
import AccountSettings from '@/views/identity/AccountSettings.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

function okProfile(data: Record<string, unknown>) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function failJson(status: number, message: string, code = status) {
  return new Response(JSON.stringify({ success: false, code, message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function mountAccount(startPath = '/me'): Promise<{
  root: HTMLElement
  router: Router
  unmount: () => void
}> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: AccountSettings },
      { path: '/credits', name: 'credits', component: { template: '<div>credits</div>' } },
      { path: '/scenes', name: 'scenes', component: { template: '<div />' } },
      { path: '/history', name: 'history', component: { template: '<div />' } },
    ],
  })
  await router.push(startPath)
  await router.isReady()
  const app: App = createApp(AccountSettings)
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

describe('AccountSettings', () => {
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

  it('defaults to profile section with three-way nav and AppHeader', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/account/profile')) {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
          )
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.querySelector('header.app-header')).toBeTruthy()
    const tabs = mounted.root.querySelectorAll('[role="tab"]')
    expect([...tabs].map((t) => t.textContent?.trim())).toEqual([
      '个人资料',
      '使用情况',
      '安全',
    ])
    expect(mounted.root.querySelector('#title-profile')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/个人资料/)
    expect(mounted.root.textContent).toMatch(/显示名称/)
    expect(mounted.root.textContent).toMatch(/chen@example.com/)
    expect(mounted.root.querySelector('#title-usage')?.closest('section')).toBeTruthy()
    const profileTab = mounted.root.querySelector('[data-section="profile"]')
    expect(profileTab?.getAttribute('aria-selected')).toBe('true')
  })

  it('shows login gate when no token', async () => {
    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toMatch(/未登录/)
    expect(mounted.root.textContent).toMatch(/去登录/)

    const goLogin = [...mounted.root.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === '去登录',
    )
    goLogin!.click()
    await flushUi()
    expect(mounted.router.currentRoute.value.name).toBe('login')
  })

  it('email is read-only; only display name is a text input', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/account/profile')) {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
          )
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const textInputs = [...mounted.root.querySelectorAll('input')].filter(
      (el) => (el as HTMLInputElement).type === 'text',
    )
    expect(textInputs).toHaveLength(1)
    expect(textInputs[0]?.id).toBe('display-name')
    expect(
      [...mounted.root.querySelectorAll('input')].some(
        (el) => (el as HTMLInputElement).value === 'chen@example.com',
      ),
    ).toBe(false)
  })

  it('non-401 profile load failure shows error and retry, keeps token', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/account/profile')) {
          return Promise.resolve(failJson(500, '服务暂时不可用', 500))
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    expect(getToken()).toBe('jwt-account')
    expect(mounted.root.textContent).toMatch(/服务暂时不可用/)
    expect(
      [...mounted.root.querySelectorAll('button')].some((b) => b.textContent?.trim() === '重试'),
    ).toBe(true)
  })

  it('rejects blank display name without PATCH', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (String(url).includes('/api/v1/account/profile')) {
        if (init?.method === 'PATCH') {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '不应到达', email: 'chen@example.com' }),
          )
        }
        return Promise.resolve(
          okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
        )
      }
      if (String(url).includes('/api/v1/credits')) {
        return Promise.resolve(
          okProfile({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }
      return Promise.resolve(failJson(404, 'not found'))
    })
    vi.stubGlobal('fetch', fetchMock)

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const input = mounted.root.querySelector('#display-name') as HTMLInputElement
    input.value = '   '
    input.dispatchEvent(new Event('input'))
    await flushUi()

    const saveBtn = [...mounted.root.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === '保存',
    )
    saveBtn!.click()
    await flushUi()

    const patchCalls = fetchMock.mock.calls.filter(
      (c) => String(c[0]).includes('/api/v1/account/profile') && c[1]?.method === 'PATCH',
    )
    expect(patchCalls).toHaveLength(0)
    expect(mounted.root.textContent).toMatch(/请填写显示名称/)
  })

  it('saves display name via PATCH username', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (String(url).includes('/api/v1/account/profile')) {
        if (init?.method === 'PATCH') {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '新名字', email: 'chen@example.com' }),
          )
        }
        return Promise.resolve(
          okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
        )
      }
      if (String(url).includes('/api/v1/credits')) {
        return Promise.resolve(
          okProfile({
            tier: 'FREE',
            available: 14,
            balance: 20,
            reserved: 0,
            nextResetAt: '2026-10-24T10:00:00Z',
            periodAnchorAt: '2026-09-24T10:00:00Z',
          }),
        )
      }
      return Promise.resolve(failJson(404, 'not found'))
    })
    vi.stubGlobal('fetch', fetchMock)

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const input = mounted.root.querySelector('#display-name') as HTMLInputElement
    expect(input.value).toBe('小陈')
    input.value = '新名字'
    input.dispatchEvent(new Event('input'))
    await flushUi()

    const saveBtn = [...mounted.root.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === '保存',
    )
    expect(saveBtn).toBeTruthy()
    saveBtn!.click()
    await flushUi()

    const patchCall = fetchMock.mock.calls.find(
      (c) => String(c[0]).includes('/api/v1/account/profile') && c[1]?.method === 'PATCH',
    )
    expect(patchCall).toBeTruthy()
    expect(JSON.parse(String(patchCall?.[1]?.body))).toEqual({ username: '新名字' })
    expect(mounted.root.textContent).toMatch(/显示名称已更新/)
    expect(mounted.root.querySelector('.profile-name')?.textContent).toBe('新名字')
  })

  it('shows ApiError message when save fails', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string, init?: RequestInit) => {
        if (String(url).includes('/api/v1/account/profile')) {
          if (init?.method === 'PATCH') {
            return Promise.resolve(failJson(409, '显示名称已被占用', 409))
          }
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
          )
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const input = mounted.root.querySelector('#display-name') as HTMLInputElement
    input.value = '占用名'
    input.dispatchEvent(new Event('input'))
    await flushUi()
    const saveBtn = [...mounted.root.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === '保存',
    )
    saveBtn!.click()
    await flushUi()

    expect(mounted.root.textContent).toMatch(/显示名称已被占用/)
  })

  it('clears token on 401 profile load', async () => {
    setToken('jwt-bad')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(failJson(401, '未登录或登录已过期', 401)),
      ),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    expect(getToken()).toBeNull()
    expect(mounted.root.textContent).toMatch(/未登录|登录已过期/)
  })

  it('switches sections via keyboard on tablist', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/account/profile')) {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
          )
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const tablist = mounted.root.querySelector('[role="tablist"]') as HTMLElement
    tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushUi()

    const usageTab = mounted.root.querySelector('[data-section="usage"]')
    expect(usageTab?.getAttribute('aria-selected')).toBe('true')
    expect(mounted.root.querySelector('#title-usage')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/即将完善/)
  })

  it('security section keeps logout that clears token', async () => {
    setToken('jwt-account')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (String(url).includes('/api/v1/account/profile')) {
          return Promise.resolve(
            okProfile({ userId: 'u-1', username: '小陈', email: 'chen@example.com' }),
          )
        }
        if (String(url).includes('/api/v1/credits')) {
          return Promise.resolve(
            okProfile({
              tier: 'FREE',
              available: 14,
              balance: 20,
              reserved: 0,
              nextResetAt: '2026-10-24T10:00:00Z',
              periodAnchorAt: '2026-09-24T10:00:00Z',
            }),
          )
        }
        return Promise.resolve(failJson(404, 'not found'))
      }),
    )

    const mounted = await mountAccount()
    unmount = mounted.unmount
    await flushUi()

    const securityTab = mounted.root.querySelector('[data-section="security"]') as HTMLButtonElement
    securityTab.click()
    await flushUi()

    const logoutBtn = mounted.root.querySelector('[data-action="logout"]') as HTMLButtonElement
    expect(logoutBtn).toBeTruthy()
    logoutBtn.click()
    await flushUi()

    expect(getToken()).toBeNull()
    expect(mounted.router.currentRoute.value.name).toBe('login')
  })
})
