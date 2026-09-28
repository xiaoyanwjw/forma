import { request } from '@/api/client'
import type { AccountProfile, UpdateAccountProfileRequest } from '@/types/identity/account'
import type { CreditUsage } from '@/types/business/credit'

export function getAccountProfile() {
  return request<AccountProfile>('/api/v1/account/profile')
}

export function updateAccountProfile(body: UpdateAccountProfileRequest) {
  return request<AccountProfile>('/api/v1/account/profile', {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

/** 账户页只读用量（摘要 + SETTLED 流水） */
export function getAccountCreditUsage() {
  return request<CreditUsage>('/api/v1/account/credits/usage')
}
