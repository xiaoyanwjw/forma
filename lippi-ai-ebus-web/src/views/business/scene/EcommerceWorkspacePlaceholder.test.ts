import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import EcommerceWorkspacePlaceholder from '@/views/business/scene/EcommerceWorkspacePlaceholder.vue'

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

describe('EcommerceWorkspacePlaceholder empty state', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-demo')
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
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
    const pills = [...mounted.root.querySelectorAll('.pill')].map((el) => el.textContent?.trim())
    expect(pills).toEqual(['选品清单', '生成上架素材'])
    expect(mounted.root.querySelector('textarea')?.getAttribute('placeholder')).toBe(
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

  it('fills templates into ask shell without navigating', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const pathBefore = mounted.router.currentRoute.value.fullPath

    const [picks, listing] = [...mounted.root.querySelectorAll<HTMLButtonElement>('.pill')]
    picks.click()
    await flushUi()
    const area = mounted.root.querySelector('textarea') as HTMLTextAreaElement
    expect(area.value).toContain('选品清单')
    expect(area.value).toContain('【品类】')

    listing.click()
    await flushUi()
    expect(area.value).toContain('生成上架素材')
    expect(area.value).toContain('【商品名称】')
    expect(mounted.router.currentRoute.value.fullPath).toBe(pathBefore)
  })

  it('keeps send and attach disabled (no generation in 2.5)', async () => {
    const mounted = await mountWorkspace()
    unmount = mounted.unmount
    const send = mounted.root.querySelector('button[aria-label="发送"]') as HTMLButtonElement
    const attach = mounted.root.querySelector('button[aria-label="附件"]') as HTMLButtonElement
    expect(send.disabled).toBe(true)
    expect(attach.disabled).toBe(true)
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
