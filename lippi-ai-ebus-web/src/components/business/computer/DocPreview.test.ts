/**
 * @vitest-environment jsdom
 */
import { createApp, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import DocPreview from './DocPreview.vue'
import type { ComputerDocView } from '@/types/business/computerView'

function mountDocPreview(
  docView: ComputerDocView,
  options?: { fileName?: string; onHandoff?: (payload: { skillId: string; prompt: string }) => void },
) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const handoffs: Array<{ skillId: string; prompt: string }> = []
  const app = createApp({
    components: { DocPreview },
    setup() {
      const onHandoff = (payload: { skillId: string; prompt: string }) => {
        handoffs.push(payload)
        options?.onHandoff?.(payload)
      }
      return { document: docView, fileName: options?.fileName, onHandoff }
    },
    template:
      '<DocPreview :document="document" :file-name="fileName" @handoff="onHandoff" />',
  })
  app.mount(host)
  return {
    host,
    app,
    handoffs,
    async cleanup() {
      app.unmount()
      host.remove()
    },
  }
}

describe('DocPreview', () => {
  it('renders sanitized html inside git-view', async () => {
    const doc: ComputerDocView = {
      version: 2,
      title: 'readme',
      format: 'html',
      content: '<h1>Hi</h1>',
    }
    const { host, cleanup } = mountDocPreview(doc)
    await nextTick()
    expect(host.querySelector('[data-testid="git-view"]')).toBeTruthy()
    expect(host.querySelector('.markdown-body')?.innerHTML).toContain('Hi')
    await cleanup()
  })

  it('emits handoff from data-adam button click', async () => {
    const doc: ComputerDocView = {
      version: 2,
      title: 'doc',
      format: 'html',
      content:
        '<button type="button" data-adam-action="handoff" data-adam-skill-id="xhs-note" data-adam-prompt="请写笔记">写成笔记</button>',
    }
    const { host, handoffs, cleanup } = mountDocPreview(doc)
    await nextTick()
    const btn = host.querySelector('[data-adam-action="handoff"]') as HTMLButtonElement
    btn.click()
    await nextTick()
    expect(handoffs).toEqual([{ skillId: 'xhs-note', prompt: '请写笔记' }])
    await cleanup()
  })

  it('does not emit when required attrs missing', async () => {
    const doc: ComputerDocView = {
      version: 2,
      title: 'doc',
      format: 'html',
      content: '<button type="button" data-adam-action="handoff">noop</button>',
    }
    const { host, handoffs, cleanup } = mountDocPreview(doc)
    await nextTick()
    const btn = host.querySelector('[data-adam-action="handoff"]') as HTMLButtonElement
    btn.click()
    await nextTick()
    expect(handoffs).toEqual([])
    await cleanup()
  })
})
