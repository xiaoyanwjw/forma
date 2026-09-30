import { describe, expect, it } from 'vitest'
import { isArtifactDumpContent, keepReplayRow, toReplayBubbles } from '@/utils/sessionReplay'

describe('sessionReplay', () => {
  it('keepReplayRow keeps user/assistant including artifact dump', () => {
    expect(keepReplayRow({ role: 'user', content: '找杯子' })).toBe(true)
    expect(keepReplayRow({ role: 'assistant', content: '这是建议' })).toBe(true)
    expect(
      keepReplayRow({
        role: 'assistant',
        content: '```json\n{"view":{"version":1,"blocks":[]}}\n```',
      }),
    ).toBe(true)
    expect(keepReplayRow({ role: 'tool', content: 'noise' })).toBe(false)
    expect(keepReplayRow({ role: 'user', content: '   ' })).toBe(false)
  })

  it('toReplayBubbles collapses tool loop into user/agent pairs', () => {
    const dump = '```json\n{"view":{"version":1,"title":"x","blocks":[]}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成厨房小件', createdAt: '2026-09-28T08:00:00Z' },
        {
          role: 'assistant',
          content: "I'll load the skill instructions first.",
          createdAt: '2026-09-28T08:00:01Z',
        },
        { role: 'tool', content: '# skill' },
        {
          role: 'assistant',
          content: "I'll search the promotion pool once.",
          createdAt: '2026-09-28T08:00:02Z',
        },
        { role: 'tool', content: '{"hits":[]}' },
        { role: 'assistant', content: dump, createdAt: '2026-09-28T08:00:10Z' },
        { role: 'user', content: '请帮我生成厨房小件', createdAt: '2026-09-28T08:01:00Z' },
        { role: 'assistant', content: dump, createdAt: '2026-09-28T08:01:05Z' },
        { role: 'user', content: '请帮我生成厨房小件', createdAt: '2026-09-28T08:02:00Z' },
        { role: 'assistant', content: dump, createdAt: '2026-09-28T08:02:05Z' },
      ],
      'picks',
    )

    expect(bubbles.map((b) => [b.role, b.kind])).toEqual([
      ['user', 'text'],
      ['assistant', 'artifact'],
      ['user', 'text'],
      ['assistant', 'artifact'],
      ['user', 'text'],
      ['assistant', 'artifact'],
    ])
    expect(bubbles[0]?.at).toBe(Date.parse('2026-09-28T08:00:00Z'))
    expect(bubbles[1]?.at).toBe(Date.parse('2026-09-28T08:00:10Z'))
    expect(bubbles[1]?.content).toMatch(/已生成选品成果/)
    expect(bubbles.some((b) => b.content.includes("I'll load"))).toBe(false)
    expect(bubbles.some((b) => isArtifactDumpContent(b.content))).toBe(false)
  })

  it('toReplayBubbles keeps plain assistant text when no artifact dump', () => {
    const bubbles = toReplayBubbles([
      { role: 'user', content: '帮我找杯子' },
      { role: 'assistant', content: '这是杯子建议' },
    ])
    expect(bubbles).toEqual([
      { role: 'user', content: '帮我找杯子', kind: 'text' },
      { role: 'assistant', content: '这是杯子建议', kind: 'text' },
    ])
  })

  it('toReplayBubbles labels each dump by content, not only latest artifactKind', () => {
    const pickDump =
      '```json\n{"view":{"version":1,"blocks":[{"type":"list","items":[{"id":"pl-1"}]}]},"artifact":{"items":[{"id":"pl-1","niche":"手机","sourceUrl":"https://x"}]}}\n```'
    const listDump =
      '```json\n{"view":{"version":1,"blocks":[{"type":"media"}]},"artifact":{"heroPlan":"x","detailTitle":"t","detailBody":"b","displayNotes":"n","framePrompts":["p"]}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成手机选品清单' },
        { role: 'assistant', content: pickDump },
        {
          role: 'user',
          content: '请为选品「荣耀」生成上架素材\nsourceUrl: https://item.taobao.com/1',
        },
        { role: 'assistant', content: listDump },
      ],
      'listing',
    )
    expect(bubbles.map((b) => [b.role, b.kind, b.content])).toEqual([
      ['user', 'text', '请帮我生成手机选品清单'],
      ['assistant', 'artifact', '已生成选品成果，右侧 Computer 可查看。'],
      [
        'user',
        'text',
        '请为选品「荣耀」生成上架素材\nsourceUrl: https://item.taobao.com/1',
      ],
      ['assistant', 'artifact', '已生成上架素材，右侧 Computer 可查看主图位与文案。'],
    ])
  })

  it('toReplayBubbles does not relabel picklist dump as listing when handoff user is missing', () => {
    const pickDump =
      '```json\n{"view":{"version":1,"blocks":[{"type":"list"}]},"artifact":{"items":[{"niche":"香薰","sourceUrl":"https://x"}]}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成香薰选品清单' },
        { role: 'assistant', content: pickDump },
      ],
      'listing',
    )
    expect(bubbles).toHaveLength(2)
    expect(bubbles[1]?.content).toMatch(/已生成选品成果/)
    expect(bubbles[1]?.content).not.toMatch(/上架素材/)
  })
})
