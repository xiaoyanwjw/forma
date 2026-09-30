import { describe, expect, it } from 'vitest'
import {
  MESSAGE_FOLD_THRESHOLD,
  appendMessageDelta,
  applyAgentEnded,
  applyAgentStarted,
  applyMessageDelta,
  applyToolFinished,
  applyToolStarted,
  buildFailureDetail,
  canExpandProcessEvent,
  foldPreview,
  formatEventTime,
  formatStreamBodyForDisplay,
  processStreamText,
  processEventDisplayLabel,
  toolDisplayLabel,
  toolEventLabel,
} from './agentProgress'

describe('agentProgress', () => {
  it('toolEventLabel prefers toolName then name then payload string', () => {
    expect(toolEventLabel({ toolName: 'read_skill' })).toBe('read_skill')
    expect(toolEventLabel({ name: 'echo' })).toBe('echo')
    expect(toolEventLabel({ payload: 'do-thing' })).toBe('do-thing')
    expect(toolEventLabel({})).toBe('tool')
  })

  it('toolDisplayLabel maps known tools to Chinese; unknown keeps raw', () => {
    expect(toolDisplayLabel('read_skill')).toBe('读取技能说明')
    expect(toolDisplayLabel('echo')).toBe('执行辅助工具')
    expect(toolDisplayLabel('tool')).toBe('执行工具')
    expect(toolDisplayLabel('custom_foo')).toBe('custom_foo')
  })

  it('processEventDisplayLabel translates agent/message wire titles', () => {
    expect(processEventDisplayLabel('agent.start')).toBe('开始执行')
    expect(processEventDisplayLabel('message')).toBe('模型输出')
    expect(processEventDisplayLabel('agent.end')).toBe('执行结束')
    expect(processEventDisplayLabel('read_skill')).toBe('读取技能说明')
  })

  it('builds agent.start → tool → message → agent.end in order', () => {
    let events = applyAgentStarted([], { label: 'agent.start' })
    expect(events).toHaveLength(1)
    expect(events[0]?.kind).toBe('agent')
    expect(events[0]?.title).toBe('agent.start')

    events = applyToolStarted(events, { toolName: 'read_skill', toolCallId: 'c1' })
    expect(events[1]?.kind).toBe('tool')
    expect(events[1]?.title).toBe('read_skill')
    expect(events[1]?.done).toBe(false)
    expect(formatEventTime(events[1]!.at)).toMatch(/^\d{2}:\d{2}:\d{2}$/)

    events = applyToolFinished(events, {
      toolName: 'read_skill',
      toolCallId: 'c1',
      success: true,
      output: '# Skill body',
    })
    expect(events[1]?.done).toBe(true)
    expect(events[1]?.body).toBe('# Skill body')
    expect(events[1]?.success).toBe(true)
    expect(canExpandProcessEvent(events[1]!)).toBe(true)

    events = applyMessageDelta(events, { text: 'hello' })
    events = applyMessageDelta(events, { text: ' world' })
    expect(events[2]?.kind).toBe('llm')
    expect(events[2]?.title).toBe('message')
    expect(events[2]?.body).toBe('hello world')

    events = applyToolStarted(events, { toolName: 'search_sku', toolCallId: 'c2' })
    events = applyToolFinished(events, {
      toolName: 'search_sku',
      toolCallId: 'c2',
      success: true,
      output: '{"hits":[]}',
    })
    events = applyMessageDelta(events, { text: '{"view":{"version":1,"title":"清单","blocks":[]}}' })
    expect(events.filter((e) => e.kind === 'llm')).toHaveLength(2)
    expect(events[events.length - 1]?.body).toContain('"title":"清单"')
    expect(events[2]?.body).toBe('hello world')

    events = applyAgentEnded(events, { label: 'agent.end' })
    expect(events[events.length - 1]?.kind).toBe('agent')
    expect(events.map((e) => e.kind)).toEqual(['agent', 'tool', 'llm', 'tool', 'llm', 'agent'])
  })

  it('buildFailureDetail leads with reason and model output before tools', () => {
    let events = applyToolStarted([], { toolName: 'search_sku', toolCallId: 't1' })
    events = applyToolFinished(events, {
      toolName: 'search_sku',
      toolCallId: 't1',
      success: true,
      output: '[{"title":"垫"}]',
    })
    events = applyMessageDelta(events, { text: '{"view":{"version":1}}' })
    const detail = buildFailureDetail('模型终态缺少 view 字段，无法生成成果视图。请展开「模型输出」核对 JSON。', events)
    expect(detail).toContain('【失败原因】')
    expect(detail).toContain('缺少 view')
    expect(detail.indexOf('【模型输出】')).toBeLessThan(detail.indexOf('【工具'))
    expect(detail).toContain('search_sku')
    expect(detail).toContain('垫')
    expect(detail).toContain('"version"')
  })

  it('appendMessageDelta concatenates text or delta fields', () => {
    expect(appendMessageDelta('', { text: '你好' })).toBe('你好')
    expect(appendMessageDelta('你好', { delta: '世界' })).toBe('你好世界')
  })

  it('foldPreview uses 120 threshold', () => {
    const short = 'a'.repeat(120)
    expect(foldPreview(short).needsFold).toBe(false)
    expect(foldPreview(short).preview).toBe(short)
    const long = 'a'.repeat(121)
    const folded = foldPreview(long)
    expect(folded.needsFold).toBe(true)
    expect(folded.preview).toBe('a'.repeat(MESSAGE_FOLD_THRESHOLD) + '…')
  })

  it('processStreamText strips fenced/raw JSON and keeps prose', () => {
    const prose = '我先加载技能说明。'
    const withFence =
      prose + '\n```json\n{"templateId":"domestic-generic-default","items":[]}\n```'
    expect(processStreamText(withFence)).toBe(prose)

    const withRaw =
      prose + ' {"templateId":"domestic-generic-default","disclaimer":"x","items":[{"title":"a"}]}'
    expect(processStreamText(withRaw)).toBe(prose)

    expect(processStreamText('{"templateId":"x","items":[]}')).toBe('')
  })

  it('processStreamText keeps braces in prose and only strips a trailing JSON object', () => {
    const artifact = '{"templateId":"domestic-generic-default","items":[]}'
    expect(processStreamText(`关注{价格}后给出清单 ${artifact}`)).toBe('关注{价格}后给出清单')
  })

  it('processStreamText keeps truncated mid-stream brace instead of wiping prose', () => {
    expect(processStreamText('我先整理{')).toBe('我先整理{')
  })

  it('processStreamText strips unclosed ```json fence to EOS', () => {
    const prose = "I'll load the skill instructions first."
    const open =
      prose + ' ```json {"templateId":"domestic-generic-default","items":[{"title":"a"}]}'
    expect(processStreamText(open)).toBe(prose)
  })

  it('formatStreamBodyForDisplay pretty-prints JSON and trailing objects', () => {
    expect(formatStreamBodyForDisplay('{"a":1,"b":[2]}')).toBe('{\n  "a": 1,\n  "b": [\n    2\n  ]\n}')
    expect(formatStreamBodyForDisplay('先搜一下 {"a":1}')).toBe('先搜一下\n\n{\n  "a": 1\n}')
    expect(formatStreamBodyForDisplay('plain note')).toBe('plain note')
  })
})
