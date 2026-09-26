import { afterEach, describe, expect, it } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import router from '@/router'
import CreditPlan from '@/views/business/credit/CreditPlan.vue'
import LandingPage from '@/views/marketing/LandingPage.vue'
import AgentDryRun from '@/views/agent/AgentDryRun.vue'
import SceneGallery from '@/views/business/scene/SceneGallery.vue'
import EcommerceWorkspacePlaceholder from '@/views/business/scene/EcommerceWorkspacePlaceholder.vue'
import HistoryPlaceholder from '@/views/business/history/HistoryPlaceholder.vue'

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

describe('router agent dry-run', () => {
  it('resolve name agent-dry-run points to AgentDryRun', async () => {
    const resolved = router.resolve({ name: 'agent-dry-run' })
    expect(resolved.path).toBe('/agent/dry-run')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(AgentDryRun)
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

  it('resolve /scenes/ecommerce points to EcommerceWorkspacePlaceholder', async () => {
    const resolved = router.resolve({ name: 'scene-ecommerce' })
    expect(resolved.path).toBe('/scenes/ecommerce')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(EcommerceWorkspacePlaceholder)
  })

  it('resolve /history points to HistoryPlaceholder', async () => {
    const resolved = router.resolve({ name: 'history' })
    expect(resolved.path).toBe('/history')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(HistoryPlaceholder)
  })
})
