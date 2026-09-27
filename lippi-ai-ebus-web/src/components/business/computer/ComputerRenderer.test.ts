import { createApp, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ComputerRenderer from './ComputerRenderer.vue'
import { parseComputerDocument, type ComputerDocument } from '@/types/business/computerView'

const doc: ComputerDocument = {
  version: 1,
  title: 'picklist',
  status: 'settled',
  blocks: [
    { type: 'note', text: '非实时说明', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: [
        {
          badge: 'priority',
          title: '硅胶垫',
          lines: [{ kind: 'priceBand', text: '19-39', emphasis: 'price' }],
          tags: [{ kind: 'demand', text: '高｜稳', tone: 'positive' }],
        },
      ],
    },
    { type: 'media', role: 'hero', placeholder: '主图方案预览' },
    { type: 'section', heading: '详情标题', body: '标题文案' },
    { type: 'section', heading: '详情正文', body: '正文文案', tone: 'mute' },
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
    expect(host.textContent).toMatch(/选品清单/)
    expect(host.textContent).toMatch(/已结算/)
    expect(host.textContent).toMatch(/非实时说明/)
    expect(host.textContent).toMatch(/优先试/)
    expect(host.textContent).toMatch(/硅胶垫/)
    expect(host.textContent).toMatch(/主图方案预览/)
    expect(host.textContent).toMatch(/详情标题/)
    expect(host.textContent).toMatch(/标题文案/)
    expect(host.textContent).toMatch(/正文文案/)
    expect(host.querySelector('.section-body:not(.mute)')).toBeTruthy()
    expect(host.querySelector('.section-body.mute')?.textContent).toMatch(/正文文案/)
    expect(host.textContent).not.toMatch(/nope/)
    expect(warn).toHaveBeenCalled()
    app.unmount()
    host.remove()
    warn.mockRestore()
  })

  it('maps structured kinds to Chinese labels without parsing concatenated reason', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: 'picklist',
        blocks: [
          { type: 'note', kind: 'assumptions', text: '默认货源可达' },
          {
            type: 'list',
            ordered: true,
            items: [
              {
                badge: 'priority',
                title: '硅胶垫',
                lines: [
                  { kind: 'priceBand', text: '19-39', emphasis: 'price' },
                  { kind: 'painPoint', text: '积水难干' },
                  { kind: 'angle', text: '租房刚需' },
                  { kind: 'diff', text: '多色套装' },
                  { kind: 'niche', text: '厨房沥水' },
                ],
                tags: [
                  { kind: 'demand', text: '高', tone: 'positive' },
                  { kind: 'competition', text: '中', tone: 'caution' },
                  { kind: 'margin', text: '中', tone: 'info' },
                  { kind: 'risk', text: '低', tone: 'safe' },
                ],
              },
            ],
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    const root = host
    expect(root.textContent).toMatch(/假设：默认货源可达/)
    expect(root.querySelector('.item-price')?.textContent).toBe('19-39')
    expect([...root.querySelectorAll('.item-line-label')].map((el) => el.textContent)).toEqual([
      '痛点',
      '切入',
      '差异',
      '细分',
    ])
    expect(root.textContent).toMatch(/积水难干/)
    expect(root.querySelector('.dims span.dim-pill.tone-positive')?.textContent).toBe('需求 高')
    expect(root.querySelector('.dims span.dim-pill.tone-caution')?.textContent).toBe('竞争 中')
    expect(root.querySelector('.dims span.dim-pill.tone-info')?.textContent).toBe('利润 中')
    expect(root.querySelector('.dims span.dim-pill.tone-safe')?.textContent).toBe('风险 低')
    app.unmount()
    host.remove()
  })

  it('renders markdown blocks as plain text', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: 'draft',
        blocks: [{ type: 'markdown', text: 'hello **world**' }],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.cv-markdown')?.textContent).toBe('hello **world**')
    app.unmount()
    host.remove()
  })

  it('accepts legacy string lines and tags', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const { parseComputerDocument } = await import('@/types/business/computerView')
    const parsed = parseComputerDocument({
      version: 1,
      title: 'x',
      blocks: [
        {
          type: 'list',
          items: [{ title: 'a', lines: ['旧版纯文本'], tags: ['普通标签'] }],
        },
      ],
    })
    const app = createApp(ComputerRenderer, { document: parsed })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.item-line-text')?.textContent).toBe('旧版纯文本')
    expect(host.querySelector('.dim-pill.tone-neutral')?.textContent).toBe('普通标签')
    app.unmount()
    host.remove()
  })
})

describe('parseComputerDocument', () => {
  it('accepts version 1 documents and drops unknown blocks', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const parsed = parseComputerDocument({
      version: 1,
      title: 'picklist',
      status: 'settled',
      blocks: [...doc.blocks, { type: 'nope' }],
    })
    expect(parsed).not.toBeNull()
    expect(parsed?.version).toBe(1)
    expect(parsed?.title).toBe('picklist')
    expect(parsed?.blocks.map((b) => b.type)).toEqual(['note', 'list', 'media', 'section', 'section'])
    const list = parsed?.blocks.find((b) => b.type === 'list')
    expect(list && list.type === 'list' && list.items[0].lines?.[0].kind).toBe('priceBand')
    expect(list && list.type === 'list' && list.items[0].tags?.[0].kind).toBe('demand')
    const bodySection = parsed?.blocks.find(
      (b) => b.type === 'section' && b.heading === '详情正文',
    )
    expect(bodySection && bodySection.type === 'section' && bodySection.tone).toBe('mute')
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
