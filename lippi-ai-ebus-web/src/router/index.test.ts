import { describe, expect, it } from 'vitest'
import router from '@/router'
import CreditPlan from '@/views/business/credit/CreditPlan.vue'

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
