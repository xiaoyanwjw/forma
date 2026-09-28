/** 套餐档位（与账本 API `tier` 对齐） */
export type CreditTier = 'FREE' | 'PRO' | 'PLUS'

/** GET /api/v1/credits 响应 */
export interface CreditBalance {
  tier: CreditTier | string
  available: number
  balance: number
  reserved: number
  /** ISO-8601 UTC Instant */
  nextResetAt: string
  periodAnchorAt: string
}

/** GET /api/v1/account/credits/usage 流水行 */
export interface CreditUsageEntry {
  holdId: string
  title: string
  amount: number
  /** 负值，如 −1 */
  delta: number
  /** ISO-8601 UTC Instant（结算时间） */
  occurredAt: string
}

/** GET /api/v1/account/credits/usage 响应 */
export interface CreditUsage {
  tier: CreditTier | string
  available: number
  monthlyQuota: number
  used: number
  nextResetAt: string
  entries: CreditUsageEntry[]
}

/** 价目表一行（前端静态；无定价 API） */
export interface CreditPlanRow {
  tier: CreditTier
  label: string
  monthlyQuota: number
  monthlyPriceLabel: string
}

/** 三档价目：标签 / 额度 / 月费文案 */
export const CREDIT_PLAN_ROWS: readonly CreditPlanRow[] = [
  { tier: 'FREE', label: '免费', monthlyQuota: 20, monthlyPriceLabel: '¥0' },
  { tier: 'PRO', label: 'Pro', monthlyQuota: 200, monthlyPriceLabel: '待定' },
  { tier: 'PLUS', label: 'Plus', monthlyQuota: 600, monthlyPriceLabel: '待定' },
]

export const CREDIT_TIER_LABELS: Record<CreditTier, string> = {
  FREE: '免费',
  PRO: 'Pro',
  PLUS: 'Plus',
}

/** 积分不足时常驻人话（无支付按钮承诺） */
export const INSUFFICIENT_CREDITS_HINT =
  '积分不足。可升级套餐，或等到下次重置后再用。'

export function creditTierLabel(tier: string): string {
  if (tier === 'FREE' || tier === 'PRO' || tier === 'PLUS') {
    return CREDIT_TIER_LABELS[tier]
  }
  return tier
}

/** 将 UTC ISO 格式化为东八区可读本地时间（稳定形如 2026/10/24 18:00） */
export function formatNextResetAtShanghai(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? ''
  return `${get('year')}/${get('month')}/${get('day')} ${get('hour')}:${get('minute')}`
}

/** 流水 delta 展示（如 −1） */
export function formatCreditDelta(delta: number): string {
  if (delta < 0) {
    return `−${Math.abs(delta)}`
  }
  if (delta > 0) {
    return `+${delta}`
  }
  return '0'
}
