import { describe, expect, it } from 'vitest'
import {
  MESSAGE_FOLD_THRESHOLD,
  appendMessageDelta,
  applyToolFinished,
  applyToolStarted,
  foldPreview,
  processStreamText,
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

  it('applyToolStarted appends running step; finished marks matching label done', () => {
    let steps = applyToolStarted([], { toolName: 'read_skill' })
    expect(steps).toHaveLength(1)
    expect(steps[0].done).toBe(false)
    expect(steps[0].label).toBe('read_skill')
    steps = applyToolFinished(steps, { toolName: 'read_skill' })
    expect(steps[0].done).toBe(true)
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

    expect(processStreamText('{"templateId":"x","items":[]}')).toBe('正在整理选品结果…')
  })
})
