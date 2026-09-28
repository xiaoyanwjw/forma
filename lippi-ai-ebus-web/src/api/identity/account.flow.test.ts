import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import { getAccountProfile, updateAccountProfile } from '@/api/identity/account'

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
})
