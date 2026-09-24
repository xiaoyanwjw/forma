import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, getToken } from '@/api/http'
import { afterLogin } from '@/api/identity/afterLogin'
import { login, register } from '@/api/identity/auth'
import { ApiError, request } from '@/api/client'

describe('auth FE walkthrough helpers', () => {
  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
  })

  it('afterLogin sets JWT then loads /me (page path helper)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: { userId: 'u-1', username: 'alice', email: 'a@example.com' },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const me = await afterLogin('jwt-demo')
    expect(getToken()).toBe('jwt-demo')
    expect(me.userId).toBe('u-1')
    const meCall = fetchMock.mock.calls[0]
    expect(meCall?.[0]).toBe('/api/v1/me')
    const meHeaders = meCall?.[1]?.headers as Headers
    expect(meHeaders.get('Authorization')).toBe('Bearer jwt-demo')
  })

  it('afterLogin clears JWT when /me fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ success: false, code: 401, message: '未授权，请先登录' }),
          { status: 401, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    await expect(afterLogin('bad-jwt')).rejects.toBeInstanceOf(ApiError)
    expect(getToken()).toBeNull()
  })

  it('does not attach Authorization on auth login/register paths', async () => {
    setStaleToken()
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { token: 'new', userId: 'u', username: 'a', email: 'a@e.com' },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    await login({ account: 'a', password: 'secret12' })
    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers
    expect(headers.get('Authorization')).toBeNull()
  })

  it('login then afterLogin stores JWT and loads /me', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: { token: 'jwt-demo', userId: 'u-1', username: 'alice', email: 'a@example.com' },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: { userId: 'u-1', username: 'alice', email: 'a@example.com' },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const result = await login({ account: 'alice', password: 'secret12' })
    await afterLogin(result.token)
    expect(getToken()).toBe('jwt-demo')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/login')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/me')
  })

  it('register posts username+email+password+disclaimer', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { userId: 'u-2' } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await register({
      username: 'bob',
      email: 'bob@example.com',
      password: 'secret12',
      agreedToAiDisclaimer: true,
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/register')
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.agreedToAiDisclaimer).toBe(true)
  })

  it('surfaces human error message when login fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ success: false, code: 401, message: '用户名或密码错误' }),
          { status: 401, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    await expect(login({ account: 'x', password: 'y' })).rejects.toMatchObject({
      message: '用户名或密码错误',
    })
    expect(getToken()).toBeNull()
  })

  it('throws ApiError when JSON body is null', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('null', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      ),
    )
    await expect(request('/api/v1/me')).rejects.toMatchObject({
      message: '服务响应异常',
    })
  })
})

function setStaleToken() {
  localStorage.setItem('ebus_jwt', 'stale-jwt')
}
