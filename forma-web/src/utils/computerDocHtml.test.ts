/**
 * @vitest-environment jsdom
 */
import { describe, expect, it } from 'vitest'
import { sanitizeComputerHtml, toPreviewHtml } from './computerDocHtml'

describe('sanitizeComputerHtml', () => {
  it('keeps handoff data attributes', () => {
    const html = sanitizeComputerHtml(
      '<button type="button" data-forma-action="handoff" data-forma-skill-id="xhs-note" data-forma-prompt="请写笔记">写成笔记</button>',
    )
    expect(html).toContain('data-forma-action="handoff"')
    expect(html).toContain('data-forma-skill-id="xhs-note"')
    expect(html).toContain('data-forma-prompt="请写笔记"')
  })

  it('migrates legacy adam protocol to forma', () => {
    const html = sanitizeComputerHtml(
      '<p class="adam-deck"><button type="button" data-adam-action="handoff" data-adam-skill-id="xhs-note" data-adam-prompt="请写笔记">写成笔记</button></p>',
    )
    expect(html).toContain('forma-deck')
    expect(html).not.toContain('adam-deck')
    expect(html).toContain('data-forma-action="handoff"')
    expect(html).not.toContain('data-adam-')
  })

  it('strips script and onclick', () => {
    const html = sanitizeComputerHtml('<p onclick="alert(1)">x</p><script>alert(2)</script>')
    expect(html).not.toMatch(/script/i)
    expect(html).not.toMatch(/onclick/i)
  })
})

describe('toPreviewHtml', () => {
  it('renders markdown then sanitizes', () => {
    const html = toPreviewHtml('markdown', 'Hello **x**')
    expect(html).toMatch(/<strong>x<\/strong>/)
  })

  it('keeps digest kit classes used by ecommerce and xhs views', () => {
    const html = toPreviewHtml(
      'html',
      '<article class="markdown-body"><div class="forma-deck"><span class="forma-sh"><span class="k">类型</span><span class="v info">科技速读</span></span></div><table class="forma-mx"><tr><td class="n">01</td></tr></table><details class="forma-blk" open><summary>h</summary><div class="body"><div class="forma-quote">q</div></div></details></article>',
    )
    expect(html).toContain('forma-deck')
    expect(html).toContain('forma-mx')
    expect(html).toContain('forma-blk')
    expect(html).toContain('forma-quote')
  })
})
