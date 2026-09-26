import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken } from '@/api/http'
import AuthLogin from '@/views/identity/AuthLogin.vue'
import AuthRegister from '@/views/identity/AuthRegister.vue'
import { AI_DISCLAIMER_SHORT } from '@/constants/compliance'

vi.mock('@/api/identity/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
}))

vi.mock('@/api/identity/afterLogin', () => ({
  afterLogin: vi.fn(),
}))

import { login, register } from '@/api/identity/auth'
import { afterLogin } from '@/api/identity/afterLogin'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

async function mountWithRouter(Component: typeof AuthLogin | typeof AuthRegister, startPath: string) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: startPath },
      { path: '/login', name: 'login', component: AuthLogin },
      { path: '/register', name: 'register', component: AuthRegister },
      { path: '/credits', name: 'credits', component: { template: '<div>credits</div>' } },
      { path: '/scenes', name: 'scenes', component: { template: '<div>scenes</div>' } },
      { path: '/me', name: 'me', component: { template: '<div>me</div>' } },
    ],
  })
  await router.push(startPath)
  await router.isReady()
  const push = vi.spyOn(router, 'push')
  const app = createApp(Component)
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

async function fillAndSubmit(root: HTMLElement, values: Record<string, string>, opts?: { agree?: boolean }) {
  const inputs = Array.from(root.querySelectorAll('input'))
  const labels = Array.from(root.querySelectorAll('label'))
  for (const label of labels) {
    const text = label.textContent ?? ''
    const input = label.querySelector('input')
    if (!input || input.type === 'checkbox') continue
    for (const [key, value] of Object.entries(values)) {
      if (text.includes(key)) {
        input.value = value
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
    }
  }
  // fallback by order if labels unexpected
  const textInputs = inputs.filter((i) => i.type !== 'checkbox')
  if (textInputs.length >= Object.keys(values).length) {
    const ordered = Object.values(values)
    ordered.forEach((value, i) => {
      const input = textInputs[i]
      if (input && !input.value) {
        input.value = value
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
    })
  }
  if (opts?.agree) {
    const checkbox = root.querySelector('input[type="checkbox"]') as HTMLInputElement | null
    if (checkbox && !checkbox.checked) {
      checkbox.checked = true
      checkbox.dispatchEvent(new Event('change', { bubbles: true }))
      checkbox.dispatchEvent(new Event('input', { bubbles: true }))
    }
  }
  const form = root.querySelector('form')
  form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flushUi()
}

describe('auth landing pushes scenes', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    vi.mocked(login).mockResolvedValue({
      token: 'jwt-demo',
      userId: 'u-1',
      username: 'alice',
      email: 'a@example.com',
    })
    vi.mocked(register).mockResolvedValue({
      userId: 'u-1',
      username: 'alice',
      email: 'a@example.com',
    })
    vi.mocked(afterLogin).mockResolvedValue({
      userId: 'u-1',
      username: 'alice',
      email: 'a@example.com',
    })
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
  })

  it('AuthLogin onSubmit pushes { name: scenes }', async () => {
    const mounted = await mountWithRouter(AuthLogin, '/login')
    unmount = mounted.unmount
    await flushUi()

    await fillAndSubmit(mounted.root, { 账号: 'alice', 密码: 'secret12' })

    expect(login).toHaveBeenCalled()
    expect(afterLogin).toHaveBeenCalledWith('jwt-demo')
    expect(mounted.push).toHaveBeenCalledWith({ name: 'scenes' })
  })

  it('AuthRegister without disclaimer does not call register', async () => {
    const mounted = await mountWithRouter(AuthRegister, '/register')
    unmount = mounted.unmount
    await flushUi()

    expect(mounted.root.textContent).toContain(AI_DISCLAIMER_SHORT)

    await fillAndSubmit(mounted.root, {
      用户名: 'alice',
      邮箱: 'a@example.com',
      密码: 'secret12',
    })

    expect(register).not.toHaveBeenCalled()
    expect(mounted.root.textContent).toMatch(/确认/)
    expect(mounted.push).not.toHaveBeenCalledWith({ name: 'scenes' })
  })

  it('AuthRegister onSubmit pushes { name: scenes }', async () => {
    const mounted = await mountWithRouter(AuthRegister, '/register')
    unmount = mounted.unmount
    await flushUi()

    await fillAndSubmit(
      mounted.root,
      {
        用户名: 'alice',
        邮箱: 'a@example.com',
        密码: 'secret12',
      },
      { agree: true },
    )

    expect(register).toHaveBeenCalledWith({
      username: 'alice',
      email: 'a@example.com',
      password: 'secret12',
      agreedToAiDisclaimer: true,
    })
    expect(login).toHaveBeenCalled()
    expect(afterLogin).toHaveBeenCalledWith('jwt-demo')
    expect(mounted.push).toHaveBeenCalledWith({ name: 'scenes' })
  })
})
