import type { ComputerListItem, ComputerListLine } from '@/types/business/computerView'

const PRIORITY_MARK = '【优先试】'

export type ListingHandoffInput = {
  title: string
  href?: string
  id?: string
  niche?: string
  painPoint?: string
  angle?: string
}

/** 缺 title 或非 https href 或空 id → null（FE 主拦） */
export function buildListingHandoffText(input: ListingHandoffInput): string | null {
  const title = (input.title || '').replace(PRIORITY_MARK, '').trim()
  const href = (input.href || '').trim()
  const id = (input.id || '').trim()
  if (!title || !id) return null
  if (!/^https:\/\//i.test(href)) return null
  const refs = [
    input.niche?.trim() && `参考：${input.niche.trim()}`,
    input.painPoint?.trim() && `痛点：${input.painPoint.trim()}`,
    input.angle?.trim() && `角度：${input.angle.trim()}`,
  ].filter(Boolean)
  return [
    `请为商品「${title}」生成上架素材。`,
    `原链：${href}`,
    `来源选品条目：${id}`,
    ...refs,
  ].join('\n')
}

export function lineTextByKind(
  lines: ComputerListLine[] | undefined,
  kind: string,
): string | undefined {
  if (!lines?.length) return undefined
  const line = lines.find((l) => l.kind === kind)
  return line?.text
}

/** Workspace-side handoff; Computer only displays the item. */
export function listingHandoffTextForItem(
  item: ComputerListItem,
  itemIndex: number,
): string | null {
  const id = item.id?.trim() || `pl-${itemIndex + 1}`
  return buildListingHandoffText({
    title: item.title,
    href: item.href,
    id,
    niche: lineTextByKind(item.lines, 'niche'),
    painPoint: lineTextByKind(item.lines, 'painPoint'),
    angle: lineTextByKind(item.lines, 'angle'),
  })
}
