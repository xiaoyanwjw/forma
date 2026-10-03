import { createApp, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import DocPreview from './DocPreview.vue'

describe('DocPreview note v3', () => {
  it('renders adam shields into DOM', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(DocPreview, {
      document: {
        version: 2,
        title: '预算 500 元 Mini 桌搭·种草笔记',
        format: 'markdown',
        content: `# 预算 500 元 Mini 桌搭·种草笔记

<p class="forma-deck"><span class="forma-sh"><span class="k">状态</span><span class="v pink">可改写</span></span></p>

## 标题备选

1. 标题一
`,
      },
      fileName: 'note.md',
    })
    app.mount(host)
    await nextTick()
    const body = host.querySelector('.markdown-body')
    expect(body?.innerHTML).toContain('forma-deck')
    expect(body?.innerHTML).toContain('forma-sh')
    expect(body?.querySelector('.forma-sh .v.pink')?.textContent).toContain('可改写')
    app.unmount()
    host.remove()
  })
})
