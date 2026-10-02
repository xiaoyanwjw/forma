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
      items: [
        {
          badge: '优先试',
          title: '拓展坞',
          lines: [{ kind: 'priceBand', text: '19-39', emphasis: 'price' }],
          tags: [{ label: '需求', text: '高｜稳', tone: 'positive' }],
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
      document: { ...doc, title: 'Mac Mini 配件选品清单', status: 'ready' },
    })
    readyApp.mount(readyHost)
    await nextTick()
    expect(readyHost.textContent).toMatch(/Mac Mini 配件选品清单/)
    expect(readyHost.textContent).not.toMatch(/ready/)
    readyApp.unmount()
    readyHost.remove()
    expect(host.textContent).toMatch(/非实时说明/)
    expect(host.textContent).toMatch(/优先试/)
    expect(host.textContent).toMatch(/拓展坞/)
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

  it('renders payload labels and does not invent copy from kind', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: '选品清单',
        blocks: [
          { type: 'note', kind: 'assumptions', text: '默认货源可达' },
          {
            type: 'list',
            ordered: true,
            items: [
              {
                badge: '优先试',
                title: '拓展坞',
                lines: [
                  { kind: 'priceBand', text: '79-199', emphasis: 'price' },
                  { kind: 'painPoint', label: '痛点', text: '接口不够' },
                  { kind: 'angle', label: '切入', text: '居家办公' },
                  { kind: 'diff', label: '差异', text: '机身同宽' },
                  { kind: 'niche', label: '细分', text: 'Mac Mini 扩展' },
                ],
                tags: [
                  { kind: 'demand', label: '需求', text: '高', tone: 'positive' },
                  { kind: 'competition', label: '竞争', text: '中', tone: 'caution' },
                  { kind: 'margin', label: '利润', text: '中', tone: 'info' },
                  { kind: 'risk', label: '风险', text: '低', tone: 'safe' },
                ],
              },
            ],
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.textContent).toMatch(/默认货源可达/)
    expect(host.textContent).not.toMatch(/假设：/)
    expect(host.querySelector('.item-price')?.textContent).toBe('79-199')
    expect([...host.querySelectorAll('.item-line-label')].map((el) => el.textContent)).toEqual([
      '痛点',
      '切入',
      '差异',
      '细分',
    ])
    expect(host.querySelector('.dims span.dim-pill.tone-positive')?.textContent).toBe('需求 高')
    expect(host.querySelector('.dims span.dim-pill.tone-caution')?.textContent).toBe('竞争 中')
    expect(host.querySelector('.dims span.dim-pill.tone-info')?.textContent).toBe('利润 中')
    expect(host.querySelector('.dims span.dim-pill.tone-safe')?.textContent).toBe('风险 低')
    app.unmount()
    host.remove()
  })

  it('omits line labels when payload has kind only', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: {
        version: 1,
        title: '选题清单',
        blocks: [
          {
            type: 'list',
            ordered: true,
            items: [
              {
                id: 'tp-1',
                title: 'Mini 背后一串转接头',
                lines: [
                  { kind: 'hook', text: '背后永远拖着一串转接头？' },
                  { kind: 'whyFirst', text: '接口对比好拍，当天能发' },
                ],
              },
            ],
          },
        ],
      },
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelectorAll('.item-line-label').length).toBe(0)
    expect(host.textContent).not.toMatch(/说明/)
    expect(host.textContent).toMatch(/背后永远拖着一串转接头？/)
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
        title: '选品清单',
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
          { type: 'section', heading: '详情标题', body: 'Mac Mini 拓展坞标题' },
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
    expect(host.textContent).toMatch(/Mac Mini 拓展坞标题/)
    expect(host.textContent).toMatch(/详情段落/)
    expect(host.textContent).toMatch(/white bg product/)
    expect(host.textContent).toMatch(/首图：白底/)
    // Adam：主图分镜（含成对 Prompt）→ 详情标题 → 详情正文；不再单独出「生图 Prompt」标题
    const text = host.textContent || ''
    expect(text).not.toMatch(/生图 Prompt/)
    expect(text.indexOf('主图分镜')).toBeLessThan(text.indexOf('详情标题'))
    expect(text.indexOf('详情标题')).toBeLessThan(text.indexOf('详情正文'))
    expect(host.textContent).not.toMatch(/素材规范/)
    // 淘宝壳仍用 heroPlan 文案作主图位说明
    const taobao = host.querySelector('.platform-btn.platform-taobao') as HTMLButtonElement
    taobao.click()
    await nextTick()
    expect(host.querySelector('.iphone')).toBeTruthy()
    expect(host.querySelector('.tb-bar')).toBeTruthy()
    expect(host.querySelector('.tb-buy')).toBeTruthy()
    expect(host.textContent).toMatch(/白底俯拍主图方案说明/)
    expect(host.textContent).toMatch(/Mac Mini 拓展坞标题/)
    expect(host.textContent).toMatch(/详情段落/)
    expect(host.textContent).toMatch(/立即购买/)
    expect(host.textContent).toMatch(/主图分镜/)
    expect(host.textContent).toMatch(/white bg product/)
    const xianyu = host.querySelector('.platform-btn.platform-xianyu') as HTMLButtonElement
    xianyu.click()
    await nextTick()
    expect(host.querySelector('.xy-bar')).toBeTruthy()
    expect(host.textContent).toMatch(/Mac Mini 拓展坞标题/)
    expect(host.textContent).toMatch(/闲鱼/)
    const douyin = host.querySelector('.platform-btn.platform-douyin') as HTMLButtonElement
    douyin.click()
    await nextTick()
    expect(host.querySelector('.dy-bar')).toBeTruthy()
    expect(host.textContent).toMatch(/Mac Mini 拓展坞标题/)
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
      title: '选品清单',
      blocks: [
        {
          type: 'list' as const,
          ordered: true,
          items: [
            {
              badge: '优先试',
              title: '【优先试】Mac Mini 拓展坞',
              id: overrides && 'id' in overrides ? overrides.id : 'pl-1',
              href:
                overrides && 'href' in overrides
                  ? overrides.href
                  : 'https://item.example/1',
              lines: [
                { kind: 'niche' as const, text: '居家办公' },
                { kind: 'painPoint' as const, text: '水渍' },
                { kind: 'angle' as const, text: '小户型' },
              ],
            },
          ],
        },
      ],
    }
  }

  it('emits item-action with the list item when the optional action is clicked', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const onItemAction = vi.fn()
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument(),
      itemActionLabel: '做上架素材',
      onItemAction,
    })
    app.mount(host)
    await nextTick()
    const btn = host.querySelector('.item-action-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.textContent).toMatch(/做上架素材/)
    expect(btn.disabled).toBe(false)
    btn.click()
    await nextTick()
    expect(onItemAction).toHaveBeenCalledTimes(1)
    const payload = onItemAction.mock.calls[0]?.[0] as { item: { title: string }; index: number }
    expect(payload.item.title).toContain('Mac Mini 拓展坞')
    expect(payload.index).toBe(0)
    app.unmount()
    host.remove()
  })

  it('disables item action when isItemActionEnabled returns false', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const onItemAction = vi.fn()
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument({ href: undefined }),
      itemActionLabel: '做上架素材',
      isItemActionEnabled: () => false,
      onItemAction,
    })
    app.mount(host)
    await nextTick()
    const btn = host.querySelector('.item-action-btn') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.disabled).toBe(true)
    btn.click()
    await nextTick()
    expect(onItemAction).not.toHaveBeenCalled()
    app.unmount()
    host.remove()
  })

  it('does not render item action when label is omitted', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(ComputerRenderer, {
      document: picklistHandoffDocument(),
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('.item-action-btn')).toBeNull()
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
    expect(list && list.type === 'list' && list.items[0]?.tags?.[0]?.label).toBe('需求')
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
        title: 'Mac Mini 拓展坞 · 策划分镜',
        status: 'ready',
        blocks: [
          {
            type: 'markdown',
            text:
              '## 成交方向\n痛点：Mini 接显示器接口不够\n\n## 主图分镜\n1. 主图：白底产品\n2. 对比：线乱桌面\n3. 场景：坞藏走线\n\n## 标题草稿\nMac Mini 拓展坞',
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
