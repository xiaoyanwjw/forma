import { describe, it, expect } from 'vitest'
import { toPreviewHtml } from '@/utils/computerDocHtml'

const REAL_NOTE = `# 预算 500 元 Mini 桌搭·种草笔记

<p class="forma-deck"><span class="forma-sh"><span class="k">状态</span><span class="v pink">可改写</span></span><span class="forma-sh"><span class="k">对象</span><span class="v gray">Mac Mini 桌搭</span></span></p>

## 标题备选

1. 预算只有 500，Mini 桌搭我先买了这三件

## 正文

我一开始也想先买显示器支架。

## 标签

<span class="forma-tag">#桌搭</span>
<span class="forma-tag">#MacMini</span>
`

describe('note v3 preview', () => {
  it('keeps shields and tags through markdown sanitize', () => {
    const html = toPreviewHtml('markdown', REAL_NOTE)
    expect(html).toContain('forma-deck')
    expect(html).toContain('forma-sh')
    expect(html).toContain('pink')
    expect(html).toContain('forma-tag')
    expect(html).not.toContain('&lt;p class="forma-deck"')
  })
})
