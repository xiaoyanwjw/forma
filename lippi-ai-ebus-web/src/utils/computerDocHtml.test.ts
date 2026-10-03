/**
 * @vitest-environment jsdom
 */
import { describe, expect, it } from 'vitest'
import { sanitizeComputerHtml, toPreviewHtml } from './computerDocHtml'

describe('sanitizeComputerHtml', () => {
  it('keeps handoff data attributes', () => {
    const html = sanitizeComputerHtml(
      '<button type="button" data-adam-action="handoff" data-adam-skill-id="xhs-note" data-adam-prompt="请写笔记">写成笔记</button>',
    )
    expect(html).toContain('data-adam-action="handoff"')
    expect(html).toContain('data-adam-skill-id="xhs-note"')
    expect(html).toContain('data-adam-prompt="请写笔记"')
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
})
