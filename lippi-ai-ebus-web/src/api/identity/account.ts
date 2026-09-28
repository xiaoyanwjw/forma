import { request } from '@/api/client'
import type { AccountProfile, UpdateAccountProfileRequest } from '@/types/identity/account'

export function getAccountProfile() {
  return request<AccountProfile>('/api/v1/account/profile')
}

export function updateAccountProfile(body: UpdateAccountProfileRequest) {
  return request<AccountProfile>('/api/v1/account/profile', {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}
