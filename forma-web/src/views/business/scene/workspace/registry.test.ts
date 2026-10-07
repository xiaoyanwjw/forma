import { describe, expect, it } from 'vitest'
import { techDigestSpec, techProductSpec } from '@/views/business/scene/tech/spec'
import { xhsSpec } from '@/views/business/scene/xiaohongshu/spec'
import { getSceneWorkspaceSpec } from '@/views/business/scene/workspace/registry'

describe('getSceneWorkspaceSpec', () => {
  it('resolves ecommerce and xiaohongshu specs with pane maps', () => {
    const e = getSceneWorkspaceSpec('ecommerce')
    expect(e?.paneBySkillId['ecommerce-picklist']).toBe('picks')
    expect(e?.paneByArtifactType.picklist).toBe('picks')
    const x = getSceneWorkspaceSpec('xiaohongshu')
    expect(x?.paneBySkillId['xhs-note']).toBe('note')
    expect(getSceneWorkspaceSpec('nope')).toBeNull()
  })

  it('maps remaining ecommerce panes', () => {
    const e = getSceneWorkspaceSpec('ecommerce')
    expect(e?.breadcrumb).toBe('电商开店')
    expect(e?.artifactTypes).toEqual(['picklist', 'sku'])
    expect(e?.paneBySkillId['ecommerce-skulist']).toBe('listing')
    expect(e?.paneByArtifactType.sku).toBe('listing')
    expect(e).not.toHaveProperty('itemHandoffs')
    expect(e).not.toHaveProperty('toolbarHandoffs')
    expect(e).not.toHaveProperty('kindBySkillId')
    expect(e).not.toHaveProperty('whenKind')
  })

  it('maps xhs panes without spec handoffs', () => {
    const x = getSceneWorkspaceSpec('xiaohongshu')
    expect(x).toBe(xhsSpec)
    expect(x?.breadcrumb).toBe('小红书种草')
    expect(x?.artifactTypes).toEqual(['xhs_topiclist', 'xhs_note', 'xhs_break'])
    expect(x?.paneBySkillId['xhs-topiclist']).toBe('topiclist')
    expect(x?.paneBySkillId['xhs-break']).toBe('break')
    expect(x?.paneByArtifactType.xhs_topiclist).toBe('topiclist')
    expect(x?.paneByArtifactType.xhs_break).toBe('break')
    expect(x).not.toHaveProperty('itemHandoffs')
    expect(x).not.toHaveProperty('toolbarHandoffs')
  })

  it('maps tech_digest digest pane', () => {
    const t = getSceneWorkspaceSpec('tech_digest')
    expect(t).toBe(techDigestSpec)
    expect(t?.breadcrumb).toBe('科技前沿')
    expect(t?.artifactTypes).toEqual(['tech_digest'])
    expect(t?.paneBySkillId['tech-digest']).toBe('digest')
    expect(t?.paneByArtifactType.tech_digest).toBe('digest')
    expect(t?.paneByArtifactType.digest).toBe('digest')
  })

  it('maps tech_product competitor pane', () => {
    const t = getSceneWorkspaceSpec('tech_product')
    expect(t).toBe(techProductSpec)
    expect(t?.breadcrumb).toBe('产品雷达')
    expect(t?.artifactTypes).toEqual(['tech_competitor'])
    expect(t?.paneBySkillId['tech-competitor']).toBe('competitor')
    expect(t?.paneByArtifactType.tech_competitor).toBe('competitor')
  })
})
