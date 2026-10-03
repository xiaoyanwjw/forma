import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import {
  changeAccountPassword,
  getAccountCreditUsage,
  getAccountProfile,
  updateAccountProfile,
} from '@/api/identity/account'

describe('account profile api', () => {
  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
  })

  it('getAccountProfile calls GET /api/v1/account/profile with JWT', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { userId: 'u-1', username: 'alice', email: 'a@example.com' },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const profile = await getAccountProfile()
    expect(profile.username).toBe('alice')
    expect(profile).not.toHaveProperty('displayName')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/account/profile')
    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-account')
  })

  it('updateAccountProfile PATCHes username only', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { userId: 'u-1', username: 'bob', email: 'a@example.com' },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const profile = await updateAccountProfile({ username: 'bob' })
    expect(profile.username).toBe('bob')
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(String(init.body))).toEqual({ username: 'bob' })
    const headers = init.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-account')
  })

  it('getAccountCreditUsage calls GET /api/v1/account/credits/usage with JWT', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            tier: 'FREE',
            available: 19,
            monthlyQuota: 20,
            used: 1,
            nextResetAt: '2026-10-24T10:00:00Z',
            entries: [
              {
                holdId: 'h1',
                title: '已扣分',
                amount: 1,
                delta: -1,
                occurredAt: '2026-09-25T01:00:00Z',
              },
            ],
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const usage = await getAccountCreditUsage()
    expect(usage.used).toBe(1)
    expect(usage.entries[0]?.title).toBe('已扣分')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/account/credits/usage')
    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-account')
  })

  it('changeAccountPassword PUTs old and new password', async () => {
    setToken('jwt-account')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, code: 200, message: '密码已更新', data: null }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    await changeAccountPassword({ oldPassword: 'secret12', newPassword: 'newpass99' })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/account/password')
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.method).toBe('PUT')
    expect(JSON.parse(String(init.body))).toEqual({
      oldPassword: 'secret12',
      newPassword: 'newpass99',
    })
    const headers = init.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt-account')
  })
})
