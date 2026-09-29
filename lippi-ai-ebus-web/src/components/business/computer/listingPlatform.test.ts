import { describe, expect, it } from 'vitest'
import { buildStoryboardBeats, parseFramePromptEntries } from './listingPlatform'

describe('parseFramePromptEntries', () => {
  it('parses numbered prompts with optional negative lines', () => {
    const entries = parseFramePromptEntries(
      '1. white bg product\n   negative: clutter\n2. lifestyle scene\n3. detail',
    )
    expect(entries).toHaveLength(3)
    expect(entries[0]).toEqual({ prompt: 'white bg product', negative: 'clutter' })
    expect(entries[1]).toEqual({ prompt: 'lifestyle scene', negative: undefined })
    expect(entries[2].prompt).toBe('detail')
  })
})

describe('buildStoryboardBeats', () => {
  it('zips frame captions with prompts one-to-one', () => {
    const beats = buildStoryboardBeats(
      ['首图：白底', '图2：场景'],
      '1. white bg\n2. lifestyle',
    )
    expect(beats).toEqual([
      { caption: '首图：白底', prompt: 'white bg', negative: undefined },
      { caption: '图2：场景', prompt: 'lifestyle', negative: undefined },
    ])
  })
})
