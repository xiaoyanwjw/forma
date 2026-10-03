import { createApp, type App } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ad4SseEvent } from '@/types/business/agent'
import { useAgentSkillRun } from './useAgentSkillRun'

vi.mock('@/api/business/agent/agent', () => ({
  streamAgentRun: vi.fn(),
  resumeGenerationRun: vi.fn(),
}))

import { resumeGenerationRun, streamAgentRun } from '@/api/business/agent/agent'

function sse(name: Ad4SseEvent['name'], data: Record<string, unknown> = {}): Ad4SseEvent {
  return { name, data }
}

async function* eventsOf(...items: Ad4SseEvent[]) {
  for (const item of items) {
    yield item
  }
}

function withSetup<T>(factory: () => T): { result: T; unmount: () => void } {
  let result!: T
  const app: App = createApp({
    setup() {
      result = factory()
      return () => null
    },
  })
  app.mount(document.createElement('div'))
  return {
    result,
    unmount: () => app.unmount(),
  }
}

describe('useAgentSkillRun HITL', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    vi.mocked(streamAgentRun).mockReset()
    vi.mocked(resumeGenerationRun).mockReset()
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
  })

  it('sets pendingHuman on human_input_required and clears on resume settle', async () => {
    vi.mocked(streamAgentRun).mockImplementation(() =>
      eventsOf(
        sse('run_started', { runId: 'r-l1', sessionId: 's1' }),
        sse('human_input_required', {
          toolCallId: 'ask-1',
          runId: 'r-l1',
          question: '策划分镜已出。确认后将生成执行稿。',
          options: [
            { id: 'confirm_execute', label: '确认，出执行稿' },
            { id: 'supplement', label: '补充需求' },
          ],
          allowFreeText: true,
        }),
      ),
    )
    vi.mocked(resumeGenerationRun).mockImplementation(() => eventsOf(sse('run_settled')))

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    const { startSkillRun, pendingHuman, resumeSkillRun, running, runId } = mounted.result

    await startSkillRun({
      text: '生成上架',
      skillId: 'ecommerce-skulist',
      sceneCode: 'ecommerce',
    })
    expect(pendingHuman.value?.toolCallId).toBeTruthy()
    expect(pendingHuman.value?.toolCallId).toBe('ask-1')
    expect(runId.value).toBe('r-l1')
    expect(running.value).toBe(false)

    await resumeSkillRun({ optionId: 'confirm_execute' })
    expect(pendingHuman.value).toBeNull()
    expect(running.value).toBe(false)
    expect(resumeGenerationRun).toHaveBeenCalledWith(
      expect.objectContaining({
        runId: 'r-l1',
        toolCallId: 'ask-1',
        optionId: 'confirm_execute',
      }),
    )
  })

  it('resets runId at the start of each startSkillRun', async () => {
    vi.mocked(streamAgentRun)
      .mockImplementationOnce(() =>
        eventsOf(sse('run_started', { runId: 'r-old', sessionId: 's1' }), sse('run_settled')),
      )
      .mockImplementationOnce(() => eventsOf(sse('run_settled')))

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    await mounted.result.startSkillRun({
      text: '第一轮',
      skillId: 'ecommerce-skulist',
      sceneCode: 'ecommerce',
    })
    expect(mounted.result.runId.value).toBe('r-old')

    await mounted.result.startSkillRun({
      text: '第二轮',
      skillId: 'ecommerce-picklist',
      sceneCode: 'ecommerce',
    })
    expect(mounted.result.runId.value).toBeNull()
    expect(mounted.result.pendingHuman.value).toBeNull()
  })

  it('keeps skillId optional on startSkillRun', async () => {
    vi.mocked(streamAgentRun).mockImplementation(() =>
      eventsOf(sse('run_started', { runId: 'r-free', sessionId: 's2' }), sse('run_settled')),
    )

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    await mounted.result.startSkillRun({
      text: '随便聊聊',
      sceneCode: 'ecommerce',
    })

    expect(streamAgentRun).toHaveBeenCalledWith(
      expect.objectContaining({
        text: '随便聊聊',
        sceneCode: 'ecommerce',
        skillId: undefined,
        dryRun: false,
      }),
    )
    expect(mounted.result.pendingHuman.value).toBeNull()
    expect(mounted.result.running.value).toBe(false)
  })

  it('dispatches credits-changed on usable artifact_ready during HITL pause', async () => {
    const creditsSpy = vi.fn()
    window.addEventListener('forma:credits-changed', creditsSpy)
    vi.mocked(streamAgentRun).mockImplementation(() =>
      eventsOf(
        sse('run_started', { runId: 'r-l1', sessionId: 's1' }),
        sse('artifact_ready', {
          artifactRef: 'plan-1',
          view: {
            version: 1,
            title: '硅胶沥水垫 · 策划分镜',
            status: 'ready',
            blocks: [{ type: 'markdown', text: '## 成交方向' }],
          },
        }),
        sse('human_input_required', {
          toolCallId: 'ask-1',
          runId: 'r-l1',
          question: '策划分镜已出。确认后将生成执行稿。',
        }),
      ),
    )
    vi.mocked(resumeGenerationRun).mockImplementation(() =>
      eventsOf(
        sse('artifact_ready', {
          artifactRef: 'plan-2',
          view: {
            version: 1,
            title: '硅胶沥水垫 · 改策划',
            status: 'ready',
            blocks: [{ type: 'markdown', text: '## 补充后分镜' }],
          },
        }),
        sse('human_input_required', {
          toolCallId: 'ask-2',
          runId: 'r-l1',
        }),
      ),
    )

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    const { startSkillRun, pendingHuman, resumeSkillRun, artifact } = mounted.result

    await startSkillRun({
      text: '生成上架',
      skillId: 'ecommerce-skulist',
      sceneCode: 'ecommerce',
    })

    expect(artifact.value?.artifactRef).toBe('plan-1')
    expect(pendingHuman.value?.toolCallId).toBe('ask-1')
    expect(creditsSpy).toHaveBeenCalledTimes(1)
    expect(resumeGenerationRun).not.toHaveBeenCalled()

    await resumeSkillRun({ optionId: 'supplement', freeText: '再强调沥水' })
    expect(artifact.value?.artifactRef).toBe('plan-2')
    expect(pendingHuman.value?.toolCallId).toBe('ask-2')
    expect(creditsSpy).toHaveBeenCalledTimes(2)

    window.removeEventListener('forma:credits-changed', creditsSpy)
  })

  it('does not dispatch credits-changed on unusable artifact_ready', async () => {
    const creditsSpy = vi.fn()
    window.addEventListener('forma:credits-changed', creditsSpy)
    vi.mocked(streamAgentRun).mockImplementation(() =>
      eventsOf(
        sse('run_started', { runId: 'r-bad', sessionId: 's4' }),
        sse('artifact_ready', { items: [{ title: '无 view' }] }),
        sse('human_input_required', { toolCallId: 'ask-bad', runId: 'r-bad' }),
      ),
    )

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    await mounted.result.startSkillRun({
      text: '生成上架',
      skillId: 'ecommerce-skulist',
      sceneCode: 'ecommerce',
    })

    expect(mounted.result.artifact.value).toBeNull()
    expect(mounted.result.pendingHuman.value?.toolCallId).toBe('ask-bad')
    expect(creditsSpy).not.toHaveBeenCalled()
    window.removeEventListener('forma:credits-changed', creditsSpy)
  })

  it('clears pendingHuman on run_failed', async () => {
    vi.mocked(streamAgentRun).mockImplementation(() =>
      eventsOf(
        sse('run_started', { runId: 'r-fail', sessionId: 's3' }),
        sse('human_input_required', { toolCallId: 'ask-fail', runId: 'r-fail' }),
        sse('run_failed', { reason: '模型超时' }),
      ),
    )

    const mounted = withSetup(() => useAgentSkillRun())
    unmount = mounted.unmount
    await mounted.result.startSkillRun({
      text: '生成上架',
      skillId: 'ecommerce-skulist',
      sceneCode: 'ecommerce',
    })

    expect(mounted.result.pendingHuman.value).toBeNull()
    expect(mounted.result.error.value).toBe('模型超时')
    expect(mounted.result.running.value).toBe(false)
  })
})
