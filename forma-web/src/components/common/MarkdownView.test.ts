import { createApp, nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MarkdownView from './MarkdownView.vue'

describe('MarkdownView', () => {
  let host: HTMLDivElement | undefined
  let unmount: (() => void) | undefined

  afterEach(() => {
    unmount?.()
    unmount = undefined
    host?.remove()
    host = undefined
    vi.restoreAllMocks()
  })

  async function mount(
    source: string,
    opts: { showCopy?: boolean; highlightSlots?: boolean } = {},
  ) {
    host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(MarkdownView, {
      source,
      showCopy: opts.showCopy ?? true,
      highlightSlots: opts.highlightSlots ?? false,
    })
    app.mount(host)
    unmount = () => app.unmount()
    await nextTick()
    return host
  }

  it('renders markdown headings lists and bold', async () => {
    const root = await mount(
      '### 成交方向\n痛点一句\n\n## 主图分镜\n1. 前图\n2. 后图\n\n**粗体**',
    )
    const body = root.querySelector('[data-testid="markdown-body"]')
    expect(body?.innerHTML).toContain('<h3>')
    expect(body?.querySelector('h3')?.textContent).toBe('成交方向')
    expect(body?.querySelector('h2')?.textContent).toBe('主图分镜')
    expect(body?.querySelectorAll('ol li').length).toBe(2)
    expect(body?.querySelector('strong')?.textContent).toBe('粗体')
    expect(body?.textContent).not.toContain('###')
  })

  it('drops raw script tags from source', async () => {
    const root = await mount('hello <script>alert(1)</script> **ok**')
    const body = root.querySelector('[data-testid="markdown-body"]')
    expect(body?.innerHTML).not.toMatch(/<script/i)
    expect(body?.querySelector('strong')?.textContent).toBe('ok')
  })

  it('copies raw markdown source on click', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    const source = '## 标题草稿\nMac Mini 拓展坞'
    const root = await mount(source)
    const btn = root.querySelector('[data-testid="markdown-copy"]') as HTMLButtonElement
    expect(btn).toBeTruthy()
    await btn.click()
    await writeText.mock.results[0]?.value
    await nextTick()
    expect(writeText).toHaveBeenCalledWith(source)
    expect(btn.getAttribute('aria-label')).toBe('已复制')
  })

  it('highlights 「…」 slots when highlightSlots is on', async () => {
    const root = await mount('请生成「Mac Mini」清单', {
      showCopy: false,
      highlightSlots: true,
    })
    expect(root.querySelector('.ph')?.textContent).toBe('「Mac Mini」')
  })

  it('renders autolinks for https urls', async () => {
    const root = await mount('原链: https://example.com/item/1', { showCopy: false })
    const link = root.querySelector('[data-testid="markdown-body"] a')
    expect(link?.getAttribute('href')).toBe('https://example.com/item/1')
    expect(link?.textContent).toContain('example.com/item/1')
  })

  it('does not leave trailing whitespace that would show as blank line under pre-wrap', async () => {
    const root = await mount('请帮我生成「Mac Mini配件」类选品清单', {
      showCopy: false,
      highlightSlots: true,
    })
    const body = root.querySelector('[data-testid="markdown-body"]')
    expect(body?.innerHTML.endsWith('\n')).toBe(false)
    expect(body?.textContent?.endsWith('\n')).toBe(false)
  })
})
