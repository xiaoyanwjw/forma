import { getMe } from '@/api/identity/auth'
import { clearToken, setToken } from '@/api/http'
import type { Me } from '@/types/identity/auth'

/**
 * 登录成功后写 JWT 并自检 /me；/me 失败则清掉 Token，避免残留坏凭证。
 */
export async function afterLogin(token: string): Promise<Me> {
  setToken(token)
  try {
    return await getMe()
  } catch (e) {
    clearToken()
    throw e
  }
}
