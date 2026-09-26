import { createApp, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ComputerRenderer from './ComputerRenderer.vue'
import { parseComputerDocument, type ComputerDocument } from '@/types/business/computerView'

const doc: ComputerDocument = {
  version: 1,
  title: '选品清单',
  status: '已结算',
  blocks: [
    { type: 'note', text: '非实时说明', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: [{ badge: '优先试', title: '硅胶垫', lines: ['价格带：19-39'], tags: ['需求 高｜稳'] }],
    },
    { type: 'media', role: 'hero', placeholder: '主图方案预览' },
    { type: 'section', heading: '详情标题', body: '标题文案' },
  ],
}

describe('ComputerRenderer', () => {
  it('renders note list media section and skips unknown', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        ...doc,
        blocks: [...doc.blocks, { type: 'nope' } as never],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.textContent).toMatch(/非实时说明/)
    expect(host.textContent).toMatch(/优先试/)
    expect(host.textContent).toMatch(/硅胶垫/)
    expect(host.textContent).toMatch(/主图方案预览/)
    expect(host.textContent).toMatch(/详情标题/)
    expect(host.textContent).toMatch(/标题文案/)
    expect(host.textContent).not.toMatch(/nope/)
    expect(warn).toHaveBeenCalled()
    app.unmount()
    host.remove()
    warn.mockRestore()
  })
})

describe('parseComputerDocument', () => {
  it('accepts version 1 documents and drops unknown blocks', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const parsed = parseComputerDocument({
      version: 1,
      title: '选品清单',
      status: '已结算',
      blocks: [...doc.blocks, { type: 'nope' }],
    })
    expect(parsed).not.toBeNull()
    expect(parsed?.version).toBe(1)
    expect(parsed?.title).toBe('选品清单')
    expect(parsed?.blocks.map((b) => b.type)).toEqual(['note', 'list', 'media', 'section'])
    expect(warn).toHaveBeenCalledWith('[parseComputerDocument] skip unknown block type:', 'nope')
    warn.mockRestore()
  })

  it('returns null when version or title is invalid', () => {
    expect(parseComputerDocument({ version: 2, title: '选品清单', blocks: [] })).toBeNull()
    expect(parseComputerDocument({ version: 1, title: 3, blocks: [] })).toBeNull()
    expect(parseComputerDocument({ version: 1, title: '选品清单' })).toBeNull()
    expect(parseComputerDocument(null)).toBeNull()
  })
})
