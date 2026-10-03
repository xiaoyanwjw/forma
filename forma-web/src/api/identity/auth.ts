import { request } from '@/api/client'
import type {
  LoginRequest,
  LoginResult,
  Me,
  RegisterRequest,
  RegisterResult,
} from '@/types/identity/auth'

export function register(body: RegisterRequest) {
  return request<RegisterResult>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function login(body: LoginRequest) {
  return request<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function getMe() {
  return request<Me>('/api/v1/me')
}
