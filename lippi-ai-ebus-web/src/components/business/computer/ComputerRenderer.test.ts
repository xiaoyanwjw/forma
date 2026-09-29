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
    // protocol placeholder `ready` must not surface in the head
    const readyHost = document.createElement('div')
    document.body.appendChild(readyHost)
    const readyApp = createApp(ComputerRenderer, {
      document: { ...doc, title: '厨房小件选品清单', status: 'ready' },
    })
    readyApp.mount(readyHost)
    await nextTick()
    expect(readyHost.textContent).toMatch(/厨房小件选品清单/)
    expect(readyHost.textContent).not.toMatch(/ready/)
    readyApp.unmount()
    readyHost.remove()
    expect(host.textContent).toMatch(/非实时说明/)
    expect(host.textContent).toMatch(/优先试/)
    expect(host.textContent).toMatch(/硅胶垫/)
    expect(host.textContent).toMatch(/主图方案预览/)
    expect(host.textContent).toMatch(/详情标题/)
    expect(host.textContent).toMatch(/标题文案/)
    expect(host.textContent).toMatch(/正文文案/)
    expect(host.querySelector('.listing-copy.is-title')).toBeTruthy()
    expect(host.querySelector('.listing-copy.is-notes .section-body')?.textContent).toMatch(/正文文案/)
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

  it('renders https list item href as external link; omits http and missing href', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: 'picklist',
        blocks: [
          {
            type: 'list',
            items: [
              { title: '商品A', href: 'https://item.example/1' },
              { title: '商品B', href: 'http://item.example/2' },
              { title: '商品C' },
            ],
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    const links = host.querySelectorAll('a[target="_blank"]')
    expect(links.length).toBe(1)
    expect(links[0]?.getAttribute('href')).toBe('https://item.example/1')
    expect(links[0]?.getAttribute('rel')).toBe('noopener noreferrer')
    expect(host.textContent).toMatch(/商品A/)
    expect(host.textContent).toMatch(/商品B/)
    expect(host.textContent).not.toMatch(/http:\/\/item/)
    app.unmount()
    host.remove()
  })

  it('shows listing hero plan text instead of stretching placeholder src', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: 'listingPreview',
        blocks: [
          {
            type: 'media',
            role: 'hero',
            src: 'data:image/png;base64,aaa',
            mediaObjectId: 'm1',
            placeholder: '白底俯拍主图方案说明',
            alt: '主图',
          },
          {
            type: 'list',
            ordered: true,
            items: [
              { title: '首图：白底' },
              { title: '图2：场景' },
              { title: '图3：细节' },
            ],
          },
          { type: 'section', heading: '详情标题', body: '硅胶沥水垫标题' },
          { type: 'section', heading: '详情正文', body: '详情段落' },
          {
            type: 'section',
            heading: '展示说明',
            body: '主图顺序说明',
            tone: 'mute',
          },
          {
            type: 'section',
            heading: '生图 Prompt',
            body: '1. white bg product\n2. lifestyle scene\n3. detail close-up',
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.platform-switch')).toBeTruthy()
    expect(host.querySelectorAll('.platform-btn').length).toBe(4)
    // Adam：素材卡片，无手机框
    expect(host.querySelector('.adam-doc')).toBeTruthy()
    expect(host.querySelector('.iphone')).toBeNull()
    expect(host.querySelector('.listing-hero-plan-card')).toBeNull()
    expect(host.textContent).toMatch(/硅胶沥水垫标题/)
    expect(host.textContent).toMatch(/详情段落/)
    expect(host.textContent).toMatch(/生图 Prompt/)
    expect(host.textContent).toMatch(/white bg product/)
    expect(host.textContent).toMatch(/首图：白底/)
    // Adam 顺序：生图 Prompt → 详情标题 → 详情正文 → 主图分镜
    const text = host.textContent || ''
    expect(text.indexOf('生图 Prompt')).toBeLessThan(text.indexOf('详情标题'))
    expect(text.indexOf('详情标题')).toBeLessThan(text.indexOf('详情正文'))
    expect(text.indexOf('详情正文')).toBeLessThan(text.indexOf('主图分镜'))
    expect(host.textContent).not.toMatch(/素材规范/)
    // 淘宝壳仍用 heroPlan 文案作主图位说明
    const taobao = host.querySelector('.platform-btn.platform-taobao') as HTMLButtonElement
    taobao.click()
    await nextTick()
    expect(host.querySelector('.iphone')).toBeTruthy()
    expect(host.querySelector('.tb-bar')).toBeTruthy()
    expect(host.querySelector('.tb-buy')).toBeTruthy()
    expect(host.textContent).toMatch(/白底俯拍主图方案说明/)
    expect(host.textContent).toMatch(/硅胶沥水垫标题/)
    expect(host.textContent).toMatch(/详情段落/)
    expect(host.textContent).toMatch(/立即购买/)
    expect(host.textContent).toMatch(/生图 Prompt/)
    expect(host.textContent).toMatch(/white bg product/)
    const xianyu = host.querySelector('.platform-btn.platform-xianyu') as HTMLButtonElement
    xianyu.click()
    await nextTick()
    expect(host.querySelector('.xy-bar')).toBeTruthy()
    expect(host.textContent).toMatch(/硅胶沥水垫标题/)
    expect(host.textContent).toMatch(/闲鱼/)
    const douyin = host.querySelector('.platform-btn.platform-douyin') as HTMLButtonElement
    douyin.click()
    await nextTick()
    expect(host.querySelector('.dy-bar')).toBeTruthy()
    expect(host.textContent).toMatch(/硅胶沥水垫标题/)
    expect(host.textContent).toMatch(/封面|立即购买/)
    app.unmount()
    host.remove()
  })

  it('hides platform switcher on picklist documents', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, { document: doc })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.platform-switch')).toBeNull()
    app.unmount()
    host.remove()
  })

  function picklistHandoffDocument(overrides?: {
    id?: string
    href?: string
  }) {
    return {
      version: 1 as const,
      title: 'picklist',
      blocks: [
        {
          type: 'list' as const,
          ordered: true,
          items: [
            {
              badge: 'priority' as const,
              title: '【优先试】硅胶沥水垫',
              id: overrides && 'id' in overrides ? overrides.id : 'pl-1',
              href:
                overrides && 'href' in overrides
                  ? overrides.href
                  : 'https://item.example/1',
              lines: [
                { kind: 'niche' as const, text: '租房厨房' },
                { kind: 'painPoint' as const, text: '水渍' },
                { kind: 'angle' as const, text: '小户型' },
              ],
            },
          ],
        },
      ],
    }
  }

  it('emits listing-handoff when 做上架素材 is clicked and handoff is enabled', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const onListingHandoff = vi.fn()
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument(),
      enableListingHandoff: true,
      onListingHandoff,
    })
    app.mount(host)
    await nextTick()
    const btn = host.querySelector('.listing-handoff-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.textContent).toMatch(/做上架素材/)
    expect(btn.disabled).toBe(false)
    btn.click()
    await nextTick()
    expect(onListingHandoff).toHaveBeenCalledTimes(1)
    const payload = onListingHandoff.mock.calls[0]?.[0] as { text: string }
    expect(payload.text).toContain('https://item.example/1')
    expect(payload.text).toContain('pl-1')
    expect(payload.text).toContain('租房厨房')
    app.unmount()
    host.remove()
  })

  it('falls back to pl-n when list item id is missing but href is valid', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const onListingHandoff = vi.fn()
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument({ id: undefined }),
      enableListingHandoff: true,
      onListingHandoff,
    })
    app.mount(host)
    await nextTick()
    const btn = host.querySelector('.listing-handoff-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.disabled).toBe(false)
    btn.click()
    await nextTick()
    expect(onListingHandoff).toHaveBeenCalledTimes(1)
    const payload = onListingHandoff.mock.calls[0]?.[0] as { text: string }
    expect(payload.text).toContain('来源选品条目：pl-1')
    app.unmount()
    host.remove()
  })

  it('disables 做上架素材 when https href is missing', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const onListingHandoff = vi.fn()
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument({ href: undefined }),
      enableListingHandoff: true,
      onListingHandoff,
    })
    app.mount(host)
    await nextTick()
    const btn = host.querySelector('.listing-handoff-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.disabled).toBe(true)
    btn.click()
    await nextTick()
    expect(onListingHandoff).not.toHaveBeenCalled()
    app.unmount()
    host.remove()
  })

  it('does not render 做上架素材 when enableListingHandoff is false', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument(),
      enableListingHandoff: false,
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.listing-handoff-btn')).toBeNull()
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
    expect(list && list.type === 'list' && list.items[0]?.lines?.[0]?.kind).toBe('priceBand')
    expect(list && list.type === 'list' && list.items[0]?.tags?.[0]?.kind).toBe('demand')
    const bodySection = parsed?.blocks.find(
      (b) => b.type === 'section' && b.heading === '详情正文',
    )
    expect(bodySection && bodySection.type === 'section' && bodySection.tone).toBe('mute')
    expect(warn).toHaveBeenCalledWith('[parseComputerDocument] skip unknown block type:', 'nope')
    warn.mockRestore()
  })

  it('parses https href on list items and drops http', () => {
    const parsed = parseComputerDocument({
      version: 1,
      title: 'picklist',
      blocks: [
        {
          type: 'list',
          items: [
            { title: 'A', href: 'https://item.example/1' },
            { title: 'B', href: 'http://item.example/2' },
          ],
        },
      ],
    })
    const list = parsed?.blocks.find((b) => b.type === 'list')
    expect(list && list.type === 'list' && list.items[0]?.href).toBe('https://item.example/1')
    expect(list && list.type === 'list' && list.items[1]?.href).toBeUndefined()
  })

  it('parses trimmed id on list items and drops blank', () => {
    const parsed = parseComputerDocument({
      version: 1,
      title: 'picklist',
      blocks: [
        {
          type: 'list',
          items: [
            { title: 'A', id: '  pl-1  ' },
            { title: 'B', id: '   ' },
          ],
        },
      ],
    })
    const list = parsed?.blocks.find((b) => b.type === 'list')
    expect(list && list.type === 'list' && list.items[0]?.id).toBe('pl-1')
    expect(list && list.type === 'list' && list.items[1]?.id).toBeUndefined()
  })

  it('renders listing plan storyboard as single markdown without platform shell', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: '硅胶沥水垫 · 策划分镜',
        status: 'ready',
        blocks: [
          {
            type: 'markdown',
            text:
              '## 成交方向\n痛点：台面长期积水\n\n## 主图分镜\n1. 主图：白底产品\n2. 对比：湿台面\n3. 场景：沥水收纳\n\n## 标题草稿\n硅胶沥水垫',
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.textContent).toMatch(/主图：白底产品/)
    expect(host.textContent).toMatch(/标题草稿/)
    expect(host.querySelector('.cv-markdown')).not.toBeNull()
    expect(host.querySelector('.platform-switch')).toBeNull()
    expect(host.querySelector('.listing-stack')).toBeNull()
    expect(host.querySelector('.iphone')).toBeNull()
    app.unmount()
    host.remove()
  })

  it('returns null when version or title is invalid', () => {
    expect(parseComputerDocument({ version: 2, title: '选品清单', blocks: [] })).toBeNull()
    expect(parseComputerDocument({ version: 1, title: 3, blocks: [] })).toBeNull()
    expect(parseComputerDocument({ version: 1, title: '选品清单' })).toBeNull()
    expect(parseComputerDocument(null)).toBeNull()
  })
})
