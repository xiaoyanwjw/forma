import { afterEach, describe, expect, it } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import router from '@/router'
import CreditPlan from '@/views/business/credit/CreditPlan.vue'
import LandingPage from '@/views/marketing/LandingPage.vue'
import SceneGallery from '@/views/business/scene/SceneGallery.vue'
import Workspace from '@/views/business/scene/Workspace.vue'
import HistoryView from '@/views/business/history/HistoryView.vue'
import AccountSettings from '@/views/identity/AccountSettings.vue'

describe('router root', () => {
  afterEach(() => {
    clearToken()
  })

  it('resolve / redirects to scenes', async () => {
    const root = router.options.routes.find((r) => r.path === '/')
    expect(root?.redirect).toEqual({ name: 'scenes' })
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('scenes')
    expect(router.currentRoute.value.path).toBe('/scenes')
  })

  it('resolve name landing points to LandingPage at /welcome', async () => {
    const resolved = router.resolve({ name: 'landing' })
    expect(resolved.path).toBe('/welcome')
    const loader = resolved.matched[0]?.components?.default
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(LandingPage)
  })

  it('navigating to / lands on scenes even with JWT', async () => {
    setToken('jwt-demo')
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('scenes')
    expect(router.currentRoute.value.path).toBe('/scenes')
    clearToken()
  })
})

describe('router credits', () => {
  it('resolve name credits points to CreditPlan', async () => {
    const resolved = router.resolve({ name: 'credits' })
    expect(resolved.path).toBe('/credits')
    const matched = resolved.matched[0]
    expect(matched).toBeTruthy()
    const loader = matched?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(CreditPlan)
  })

  it('resolve /credits points to CreditPlan', async () => {
    const resolved = router.resolve('/credits')
    expect(resolved.name).toBe('credits')
    const loader = resolved.matched[0]?.components?.default
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(CreditPlan)
  })
})

describe('router account settings', () => {
  it('resolve name me points to AccountSettings at /me', async () => {
    const resolved = router.resolve({ name: 'me' })
    expect(resolved.path).toBe('/me')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(AccountSettings)
  })
})

describe('router scene/history shell placeholders', () => {
  it('resolve /scenes points to SceneGallery', async () => {
    const resolved = router.resolve({ name: 'scenes' })
    expect(resolved.path).toBe('/scenes')
    const loader = resolved.matched[0]?.components?.default
    if (typeof loader === 'function') {
      const mod = await (loader as () => Promise<{ default: unknown }>)()
      expect(mod.default).toBe(SceneGallery)
    } else {
      expect(loader).toBe(SceneGallery)
    }
  })

  it('resolve /scenes/:sceneCode points both scenes at Workspace', async () => {
    const ecommerce = router.resolve('/scenes/ecommerce')
    const xiaohongshu = router.resolve('/scenes/xiaohongshu')
    expect(ecommerce.name).toBe('scene-workspace')
    expect(ecommerce.params.sceneCode).toBe('ecommerce')
    expect(xiaohongshu.name).toBe('scene-workspace')
    expect(xiaohongshu.params.sceneCode).toBe('xiaohongshu')
    expect(ecommerce.matched[0]).toBe(xiaohongshu.matched[0])
    const loader = ecommerce.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(Workspace)
  })

  it('resolve /history points to HistoryView', async () => {
    const resolved = router.resolve({ name: 'history' })
    expect(resolved.path).toBe('/history')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(HistoryView)
  })
})
