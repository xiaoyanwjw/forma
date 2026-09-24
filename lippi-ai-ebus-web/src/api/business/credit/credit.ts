import { request } from '@/api/client'
import type { CreditBalance } from '@/types/business/credit'

export function getCredits() {
  return request<CreditBalance>('/api/v1/credits')
}
