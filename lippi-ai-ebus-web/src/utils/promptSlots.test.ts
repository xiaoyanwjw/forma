import { describe, expect, it } from 'vitest'
import { hasPromptSlots, splitPromptSlots } from './promptSlots'

describe('splitPromptSlots', () => {
  it('wraps full 「…」 including quotes as one slot', () => {
    const segs = splitPromptSlots(
      '请帮我生成「Mac Mini 桌搭」类选题，面向「居家办公」。',
    )
    expect(segs).toEqual([
      { text: '请帮我生成', slot: false },
      { text: '「Mac Mini 桌搭」', slot: true },
      { text: '类选题，面向', slot: false },
      { text: '「居家办公」', slot: true },
      { text: '。', slot: false },
    ])
  })

  it('returns single text segment when no slots', () => {
    expect(splitPromptSlots('随便问问')).toEqual([{ text: '随便问问', slot: false }])
  })

  it('hasPromptSlots detects book-title quotes', () => {
    expect(hasPromptSlots('商品「A」')).toBe(true)
    expect(hasPromptSlots('无槽')).toBe(false)
  })
})
