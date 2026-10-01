import { describe, expect, it } from 'vitest'
import {
  buildXhsBreakNoteHandoffText,
  buildXhsNoteHandoffText,
  extractTargetProductFromPrompt,
  extractXhsBreakHandoffFromView,
} from '@/utils/xhsNoteHandoff'

describe('buildXhsNoteHandoffText', () => {
  it('includes topic id tp-n and title; href is optional', () => {
    const text = buildXhsNoteHandoffText({
      title: '租房党厨房收纳第一篇',
      id: 'tp-1',
      hook: '台面永远堆碗',
      angle: '租房收纳',
    })
    expect(text).toContain('tp-1')
    expect(text).toContain('租房党厨房收纳第一篇')
    expect(text).toMatch(/种草笔记/)
    expect(text).not.toMatch(/【占位】/)
  })

  it('returns null without title or topic id', () => {
    expect(buildXhsNoteHandoffText({ title: '', id: 'tp-1' })).toBeNull()
    expect(buildXhsNoteHandoffText({ title: '选题', id: '' })).toBeNull()
  })
})

describe('buildXhsBreakNoteHandoffText', () => {
  const skeleton = '场景痛点一句 → 方案物件一句 → 2 个可拍使用动作'
  const targetProduct = '硅胶沥水垫'
  const rewrite = '洗完碗水槽边那圈又湿了。我没有换橱柜，只垫了一块硅胶沥水垫。'
  const structure = '痛点开场（台面积水）→ 低成本方案（一块垫）'

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
      title: '硅胶沥水垫 · 爆文拆解改写',
      blocks: [
        {
          type: 'markdown',
          text:
            `## 拆解要点\n${structure}\n\n## 骨架\n${skeleton}\n\n## 改写稿\n${rewrite}`,
        },
      ],
    })
    expect(fields.skeleton).toContain('场景痛点一句')
    expect(fields.targetProduct).toBe('硅胶沥水垫')
    const text = buildXhsBreakNoteHandoffText(fields)
    expect(text).toContain(skeleton)
    expect(text).toContain(targetProduct)
  })

  it('reads 商品「」 from the billed break prompt', () => {
    expect(
      extractTargetProductFromPrompt(
        '请拆解下面这篇笔记，并改写成我的商品「硅胶沥水垫」：…',
      ),
    ).toBe('硅胶沥水垫')
  })
})
