import { afterEach, describe, expect, it } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import router from '@/router'
import CreditPlan from '@/views/business/credit/CreditPlan.vue'
import LandingPage from '@/views/marketing/LandingPage.vue'
import AgentDryRun from '@/views/agent/AgentDryRun.vue'

describe('router landing', () => {
  afterEach(() => {
    clearToken()
  })

  it('resolve / points to LandingPage (not redirect to login)', async () => {
    const resolved = router.resolve('/')
    expect(resolved.name).toBe('landing')
    expect(resolved.path).toBe('/')
    const loader = resolved.matched[0]?.components?.default
    expect(typeof loader).toBe('function')
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(LandingPage)
  })

  it('resolve name landing points to LandingPage', async () => {
    const resolved = router.resolve({ name: 'landing' })
    expect(resolved.path).toBe('/')
    const loader = resolved.matched[0]?.components?.default
    const mod = await (loader as () => Promise<{ default: unknown }>)()
    expect(mod.default).toBe(LandingPage)
  })

  it('with JWT still stays on landing when navigating to /', async () => {
    setToken('jwt-demo')
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('landing')
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
