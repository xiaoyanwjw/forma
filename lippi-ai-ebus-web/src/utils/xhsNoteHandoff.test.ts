import { describe, expect, it } from 'vitest'
import {
  buildXhsBreakNoteHandoffText,
  buildXhsNoteHandoffText,
  extractTargetProductFromPrompt,
  extractXhsBreakHandoffFromView,
  noteHandoffTextForItem,
} from '@/utils/xhsNoteHandoff'

describe('buildXhsNoteHandoffText', () => {
  it('includes topic id tp-n and title; href is optional', () => {
    const text = buildXhsNoteHandoffText({
      title: 'Mac Mini 桌搭第一篇',
      id: 'tp-1',
      hook: 'Mini 背后一串转接头',
      angle: '桌搭理线',
    })
    expect(text).toContain('tp-1')
    expect(text).toContain('Mac Mini 桌搭第一篇')
    expect(text).toMatch(/种草笔记/)
    expect(text).not.toMatch(/【占位】/)
  })

  it('returns null without title or topic id', () => {
    expect(buildXhsNoteHandoffText({ title: '', id: 'tp-1' })).toBeNull()
    expect(buildXhsNoteHandoffText({ title: '选题', id: '' })).toBeNull()
  })
})

describe('noteHandoffTextForItem', () => {
  it('reads hook/angle by kind and falls back to tp-n id', () => {
    const text = noteHandoffTextForItem(
      {
        title: 'Mac Mini 桌搭第一篇',
        href: 'https://www.xiaohongshu.com/explore/abc',
        lines: [
          { kind: 'hook', text: 'Mini 背后一串转接头' },
          { kind: 'angle', text: '桌搭理线' },
        ],
      },
      0,
    )
    expect(text).toContain('tp-1')
    expect(text).toContain('Mini 背后一串转接头')
  })
})

describe('buildXhsBreakNoteHandoffText', () => {
  const skeleton = '场景痛点一句 → 方案物件一句 → 2 个可拍使用动作'
  const targetProduct = 'Mac Mini 拓展坞'
  const rewrite = 'Mini 背后一串转接头。我没有换主机，只加了一块 Mac Mini 拓展坞。'
  const structure = '痛点开场（接口不够线乱）→ 方案（拓展坞）'

  it('includes skeleton and targetProduct from live break fields', () => {
    const text = buildXhsBreakNoteHandoffText({
      skeleton,
      rewrite,
      targetProduct,
      structure,
    })
    expect(text).toContain(skeleton)
    expect(text).toContain(targetProduct)
    expect(text).toContain(rewrite)
    expect(text).toContain(structure)
    expect(text).toMatch(/种草笔记/)
  })

  it('extracts skeleton and product from break view markdown', () => {
    const fields = extractXhsBreakHandoffFromView({
      title: 'Mac Mini 拓展坞 · 爆文拆解改写',
      blocks: [
        {
          type: 'markdown',
          text:
            `## 拆解要点\n${structure}\n\n## 骨架\n${skeleton}\n\n## 改写稿\n${rewrite}`,
        },
      ],
    })
    expect(fields.skeleton).toContain('场景痛点一句')
    expect(fields.targetProduct).toBe('Mac Mini 拓展坞')
    const text = buildXhsBreakNoteHandoffText(fields)
    expect(text).toContain(skeleton)
    expect(text).toContain(targetProduct)
  })

  it('reads 商品「」 from the billed break prompt', () => {
    expect(
      extractTargetProductFromPrompt(
        '请拆解下面这篇笔记，并改写成我的商品「Mac Mini 拓展坞」：…',
      ),
    ).toBe('Mac Mini 拓展坞')
  })
})
