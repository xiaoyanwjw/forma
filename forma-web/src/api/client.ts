import type { ApiResponse } from '@/types/identity/auth'
import { getToken } from '@/api/http'

export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

function isAuthPublicPath(path: string): boolean {
  return path === '/api/v1/auth/register' || path === '/api/v1/auth/login'
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token && !isAuthPublicPath(path)) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...init, headers })
  let payload: ApiResponse<T> | null = null
  try {
    payload = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError(response.status, '服务响应异常')
  }

  if (payload == null || typeof payload.success !== 'boolean') {
    throw new ApiError(response.status, '服务响应异常')
  }

  if (!response.ok || !payload.success) {
    throw new ApiError(payload.code || response.status, payload.message || '请求失败')
  }
  return payload.data
}
