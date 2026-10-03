import { describe, expect, it } from 'vitest'
import {
  isArtifactDumpContent,
  isHitlOptionUserContent,
  isDisplayableTurnSuccess,
  keepReplayRow,
  logicalRunId,
  toReplayBubbles,
  toReplayBubblesFromTurns,
} from '@/utils/sessionReplay'

describe('sessionReplay', () => {
  it('treats v2 format/content dumps as artifact payloads', () => {
    expect(
      isArtifactDumpContent('{"view":{"format":"html","content":"<p>选题</p>"}}'),
    ).toBe(true)
    expect(
      isArtifactDumpContent('```json\n{"view":{"format":"markdown","content":"# 笔记"}}\n```'),
    ).toBe(true)
  })

  it('keepReplayRow keeps user/assistant including artifact dump', () => {
    expect(keepReplayRow({ role: 'user', content: '找杯子' })).toBe(true)
    expect(keepReplayRow({ role: 'assistant', content: '这是建议' })).toBe(true)
    expect(
      keepReplayRow({
        role: 'assistant',
        content: '```json\n{"view":{"version":1,"blocks":[]}}\n```',
      }),
    ).toBe(true)
    expect(keepReplayRow({ role: 'tool', content: 'noise' })).toBe(true)
    expect(keepReplayRow({ role: 'user', content: '   ' })).toBe(false)
  })

  it('recognizes HITL option user payloads and listing plan pointers', () => {
    expect(isHitlOptionUserContent('{"optionId":"confirm_execute"}')).toBe(true)
    expect(isHitlOptionUserContent('{"optionId":"supplement","freeText":"加对比图"}')).toBe(true)
    expect(isHitlOptionUserContent('请帮我生成选品清单')).toBe(false)
    expect(isDisplayableTurnSuccess('{"output":"final.json"}')).toBe(true)
    expect(isDisplayableTurnSuccess('{"output":"exec/final.json"}')).toBe(true)
    expect(isDisplayableTurnSuccess('{"output":"plan/final.json"}')).toBe(false)
  })

  it('toReplayBubbles collapses tool loop into user/agent pairs and attaches processEvents', () => {
    const dump = '```json\n{"view":{"version":1,"title":"x","blocks":[]}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成 Mac Mini 配件', createdAt: '2026-09-28T08:00:00Z' },
        {
          role: 'assistant',
          content: "I'll load the skill instructions first.",
          createdAt: '2026-09-28T08:00:01Z',
          toolCalls: [{ id: 'c1', toolName: 'read_skill' }],
        },
        { role: 'tool', content: '# skill', toolCallId: 'c1', createdAt: '2026-09-28T08:00:01.5Z' },
        {
          role: 'assistant',
          content: "I'll search the promotion pool once.",
          createdAt: '2026-09-28T08:00:02Z',
          toolCalls: [{ id: 'c2', toolName: 'search_sku' }],
        },
        { role: 'tool', content: '{"hits":[]}', toolCallId: 'c2', createdAt: '2026-09-28T08:00:03Z' },
        { role: 'assistant', content: dump, createdAt: '2026-09-28T08:00:10Z' },
        { role: 'user', content: '请帮我生成 Mac Mini 配件', createdAt: '2026-09-28T08:01:00Z' },
        { role: 'assistant', content: dump, createdAt: '2026-09-28T08:01:05Z' },
        { role: 'user', content: '请帮我生成 Mac Mini 配件', createdAt: '2026-09-28T08:02:00Z' },
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
    const proc = bubbles[1]?.processEvents || []
    expect(proc.some((e) => e.kind === 'tool' && e.title === 'read_skill' && e.done)).toBe(true)
    expect(proc.some((e) => e.kind === 'tool' && e.title === 'search_sku' && e.body?.includes('hits'))).toBe(
      true,
    )
    expect(proc.some((e) => e.kind === 'llm')).toBe(true)
    expect(bubbles[3]?.processEvents).toBeUndefined()
  })

  it('toReplayBubbles drops plain assistant text when no artifact dump', () => {
    const bubbles = toReplayBubbles([
      { role: 'user', content: '帮我找杯子' },
      { role: 'assistant', content: '这是杯子建议' },
    ])
    expect(bubbles).toEqual([{ role: 'user', content: '帮我找杯子', kind: 'text' }])
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

  it('toReplayBubbles prefers last dump when multiple dumps in one turn (HITL plan then final)', () => {
    const planDump =
      '```json\n{"view":{"version":1,"blocks":[{"type":"list","items":[{"title":"分镜"}]}]},"artifact":{"title":"策划"}}\n```'
    const finalDump =
      '```json\n{"view":{"version":1,"blocks":[{"type":"media"},{"type":"section","heading":"详情","body":"x"}]},"artifact":{"heroPlan":"主图","detailTitle":"t","detailBody":"b","displayNotes":"n","framePrompts":["p"]}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请为商品生成上架素材' },
        { role: 'assistant', content: planDump },
        { role: 'assistant', content: finalDump },
      ],
      'listing',
    )
    expect(bubbles).toHaveLength(2)
    expect(bubbles[1]?.content).toMatch(/已生成上架素材/)
    expect(bubbles[1]?.content).not.toMatch(/选品成果/)
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

  it('toReplayBubbles treats output pointer as success and keeps processEvents', () => {
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成「Mac Mini配件」类选品清单', createdAt: '2026-10-02T05:00:00Z' },
        {
          role: 'assistant',
          content: "I'll load the skill.",
          createdAt: '2026-10-02T05:00:01Z',
          toolCalls: [{ id: 'c1', toolName: 'read_skill' }],
        },
        { role: 'tool', content: '# skill', toolCallId: 'c1', createdAt: '2026-10-02T05:00:02Z' },
        {
          role: 'assistant',
          content: "I'll search once.",
          createdAt: '2026-10-02T05:00:03Z',
          toolCalls: [{ id: 'c2', toolName: 'search_sku' }],
        },
        { role: 'tool', content: '{"hits":[{"title":"坞"}]}', toolCallId: 'c2', createdAt: '2026-10-02T05:00:04Z' },
        {
          role: 'assistant',
          content: '{"output":"final.json"}',
          createdAt: '2026-10-02T05:00:10Z',
        },
      ],
      'listing',
    )
    expect(bubbles.map((b) => [b.role, b.kind])).toEqual([
      ['user', 'text'],
      ['assistant', 'artifact'],
    ])
    expect(bubbles[1]?.content).toMatch(/已生成选品成果/)
    expect(bubbles[1]?.content).not.toMatch(/上架素材/)
    const proc = bubbles[1]?.processEvents || []
    expect(proc.some((e) => e.kind === 'tool' && e.title === 'search_sku' && e.done)).toBe(true)
    expect(proc.some((e) => e.kind === 'tool' && e.title === 'read_skill')).toBe(true)
    expect(proc.some((e) => e.body?.includes('output'))).toBe(false)
  })

  it('toReplayBubbles clusters listing plan+exec by logical runId', () => {
    const bubbles = toReplayBubbles(
      [
        {
          role: 'user',
          content: '请帮我生成「Mac Mini配件」类选品清单',
          runId: 'pick-1',
        },
        {
          role: 'assistant',
          content: 'searching',
          toolCalls: [{ id: 'c1', toolName: 'search_sku' }],
          runId: 'pick-1',
        },
        { role: 'tool', content: '{"hits":[]}', toolCallId: 'c1', runId: 'pick-1' },
        { role: 'assistant', content: '{"output":"final.json"}', runId: 'pick-1' },
        {
          role: 'user',
          content: '请为商品「Mac Mini 拓展坞」生成上架素材。\n原链：https://item.example/1',
          runId: 'list-9:suspend',
        },
        {
          role: 'assistant',
          content: 'planning',
          toolCalls: [{ id: 'a1', toolName: 'ask_human' }],
          runId: 'list-9:suspend',
        },
        { role: 'tool', content: 'waiting', toolCallId: 'a1', runId: 'list-9:suspend' },
        { role: 'assistant', content: '{"output":"plan/final.json"}', runId: 'list-9:suspend' },
        { role: 'user', content: '{"optionId":"confirm_execute"}', runId: 'list-9:resume' },
        {
          role: 'assistant',
          content: 'writing exec',
          toolCalls: [{ id: 'w1', toolName: 'write_file' }],
          runId: 'list-9:resume',
        },
        { role: 'tool', content: 'ok', toolCallId: 'w1', runId: 'list-9:resume' },
        { role: 'assistant', content: '{"output":"exec/final.json"}', runId: 'list-9:resume' },
      ],
      'listing',
    )
    expect(bubbles.map((b) => [b.role, b.kind, b.content])).toEqual([
      ['user', 'text', '请帮我生成「Mac Mini配件」类选品清单'],
      ['assistant', 'artifact', '已生成选品成果，右侧 Computer 可查看。'],
      [
        'user',
        'text',
        '请为商品「Mac Mini 拓展坞」生成上架素材。\n原链：https://item.example/1',
      ],
      ['assistant', 'artifact', '已生成上架素材，右侧 Computer 可查看主图位与文案。'],
    ])
    expect(bubbles.some((b) => b.content.includes('optionId'))).toBe(false)
    expect(bubbles.filter((b) => b.kind === 'artifact')).toHaveLength(2)
    const listingProc = bubbles[3]?.processEvents || []
    expect(listingProc.some((e) => e.kind === 'tool' && e.title === 'ask_human')).toBe(true)
    expect(listingProc.some((e) => e.kind === 'tool' && e.title === 'write_file' && e.done)).toBe(
      true,
    )
  })

  it('logicalRunId strips HITL append suffixes', () => {
    expect(logicalRunId('abc:suspend')).toBe('abc')
    expect(logicalRunId('abc:resume')).toBe('abc')
    expect(logicalRunId('abc')).toBe('abc')
    expect(logicalRunId('')).toBeNull()
  })

  it('toReplayBubbles maps xhs artifactType dumps to topiclist/note/break status', () => {
    const topicDump =
      '```json\n{"artifactType":"xhs_topiclist","view":{"version":1,"blocks":[{"type":"list","items":[{"id":"tp-1"}]}]},"artifact":{"items":[{"id":"tp-1"}]}}\n```'
    const noteDump =
      '```json\n{"artifactType":"xhs_note","view":{"version":1,"blocks":[{"type":"markdown","text":"正文"}]},"artifact":{"titleOptions":["a"],"body":"b"}}\n```'
    const breakDump =
      '```json\n{"artifactType":"xhs_break","view":{"version":1,"blocks":[{"type":"markdown","text":"拆解"}]},"artifact":{"skeleton":"骨架","rewrite":"改写","sourceBody":"原文"}}\n```'
    const bubbles = toReplayBubbles(
      [
        { role: 'user', content: '请帮我生成 Mac Mini 桌搭选题清单' },
        { role: 'assistant', content: topicDump },
        { role: 'user', content: '请为商品写一篇种草笔记' },
        { role: 'assistant', content: noteDump },
        { role: 'user', content: '请拆解这篇笔记' },
        { role: 'assistant', content: breakDump },
      ],
      'note',
    )
    expect(bubbles.map((b) => [b.role, b.kind, b.content])).toEqual([
      ['user', 'text', '请帮我生成 Mac Mini 桌搭选题清单'],
      ['assistant', 'artifact', '已生成选题清单，右侧 Computer 可查看。'],
      ['user', 'text', '请为商品写一篇种草笔记'],
      ['assistant', 'artifact', '已生成笔记草稿，右侧 Computer 可查看。'],
      ['user', 'text', '请拆解这篇笔记'],
      ['assistant', 'artifact', '已生成爆文拆解，右侧 Computer 可查看。'],
    ])
  })

  it('toReplayBubblesFromTurns maps backend turns without flattening across runs', () => {
    const bubbles = toReplayBubblesFromTurns(
      [
        {
          runId: 'pick-1',
          userPrompt: '请帮我生成选品清单',
          messages: [
            { role: 'user', content: '请帮我生成选品清单', runId: 'pick-1' },
            { role: 'assistant', content: '{"output":"final.json"}', runId: 'pick-1' },
          ],
        },
        {
          runId: 'list-9',
          userPrompt: '请生成上架素材',
          messages: [
            { role: 'user', content: '请生成上架素材', runId: 'list-9:suspend' },
            { role: 'assistant', content: '{"output":"plan/final.json"}', runId: 'list-9:suspend' },
            { role: 'user', content: '{"optionId":"confirm_execute"}', runId: 'list-9:resume' },
            { role: 'assistant', content: '{"output":"exec/final.json"}', runId: 'list-9:resume' },
          ],
        },
      ],
      'picks',
    )
    expect(bubbles.map((b) => [b.role, b.content])).toEqual([
      ['user', '请帮我生成选品清单'],
      ['assistant', '已生成选品成果，右侧 Computer 可查看。'],
      ['user', '请生成上架素材'],
      ['assistant', '已生成上架素材，右侧 Computer 可查看主图位与文案。'],
    ])
  })
})
