import { describe, expect, it } from 'vitest'
import { buildXhsNoteHandoffText } from '@/utils/xhsNoteHandoff'

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
