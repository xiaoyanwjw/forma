import { describe, expect, it } from 'vitest'
import { listingHandoffTextForItem } from '@/utils/listingHandoff'
import { noteHandoffTextForItem } from '@/utils/xhsNoteHandoff'
import {
  XHS_BREAK_TOOLBAR,
  buildXhsBreakToolbarText,
  xhsSpec,
} from '@/views/business/scene/xiaohongshu/spec'
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

  it('maps remaining ecommerce panes and picklist item handoff', () => {
    const e = getSceneWorkspaceSpec('ecommerce')
    expect(e?.breadcrumb).toBe('电商开店')
    expect(e?.artifactTypes).toEqual(['picklist', 'sku'])
    expect(e?.paneBySkillId['ecommerce-skulist']).toBe('listing')
    expect(e?.paneByArtifactType.sku).toBe('listing')
    const handoff = e?.itemHandoffs?.[0]
    expect(handoff).toMatchObject({
      whenPane: 'picks',
      actionLabel: '做上架素材',
      targetSkillId: 'ecommerce-skulist',
    })
    expect(handoff?.buildText).toBe(listingHandoffTextForItem)
    expect(e).not.toHaveProperty('kindBySkillId')
    expect(e).not.toHaveProperty('whenKind')
  })

  it('maps xhs panes and topiclist item handoff; toolbar stays beside spec', () => {
    const x = getSceneWorkspaceSpec('xiaohongshu')
    expect(x).toBe(xhsSpec)
    expect(x?.breadcrumb).toBe('小红书种草')
    expect(x?.artifactTypes).toEqual(['xhs_topiclist', 'xhs_note', 'xhs_break'])
    expect(x?.paneBySkillId['xhs-topiclist']).toBe('topiclist')
    expect(x?.paneBySkillId['xhs-break']).toBe('break')
    expect(x?.paneByArtifactType.xhs_topiclist).toBe('topiclist')
    expect(x?.paneByArtifactType.xhs_break).toBe('break')
    expect(x?.toolbarHandoffs).toBeUndefined()
    const handoff = x?.itemHandoffs?.[0]
    expect(handoff).toMatchObject({
      whenPane: 'topiclist',
      actionLabel: '写成笔记',
      targetSkillId: 'xhs-note',
    })
    expect(handoff?.buildText).toBe(noteHandoffTextForItem)
    expect(XHS_BREAK_TOOLBAR).toEqual({
      whenPane: 'break',
      actionLabel: '按骨架写笔记',
      targetSkillId: 'xhs-note',
    })
  })
})

describe('buildXhsBreakToolbarText', () => {
  it('reads skeleton from view and product from lastPrompt when title has none', () => {
    const text = buildXhsBreakToolbarText({
      view: {
        title: '爆文拆解',
        blocks: [
          {
            type: 'markdown',
            text: '## 骨架\n场景痛点一句 → 方案物件一句',
          },
        ],
      },
      lastPrompt: '请拆解下面这篇笔记，并改写成我的商品「Mac Mini 拓展坞」：…',
    })
    expect(text).toContain('场景痛点一句')
    expect(text).toContain('Mac Mini 拓展坞')
  })
})
