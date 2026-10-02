/** Ecommerce workspace Computer pane kinds. */
export type EcommerceComputerKind = 'picks' | 'listing' | null

export type EcommerceComputerKindLive = Exclude<EcommerceComputerKind, null>

export const ECOM_SKILL_BY_KIND: Record<EcommerceComputerKindLive, string> = {
  picks: 'ecommerce-picklist',
  listing: 'ecommerce-skulist',
}

export function ecommerceKindFromSkillId(
  skillId?: string | null,
): EcommerceComputerKind {
  const id = (skillId || '').trim()
  if (id === ECOM_SKILL_BY_KIND.picks) return 'picks'
  if (id === ECOM_SKILL_BY_KIND.listing) return 'listing'
  return null
}

export function ecommerceKindFromArtifactType(artifactType?: string | null): EcommerceComputerKind {
  const t = (artifactType || '').trim().toLowerCase()
  if (t === 'picklist' || t === 'picks') return 'picks'
  if (t === 'sku' || t === 'listing' || t === 'skulist') return 'listing'
  return null
}

export function previewEcommerceKindFromStatus(text: string): EcommerceComputerKind {
  if (/上架素材|主图位/.test(text)) return 'listing'
  if (/选品/.test(text)) return 'picks'
  return null
}
