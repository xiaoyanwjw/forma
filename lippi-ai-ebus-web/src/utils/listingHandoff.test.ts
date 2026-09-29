import { describe, expect, it } from 'vitest'
import { buildListingHandoffText } from './listingHandoff'

describe('buildListingHandoffText', () => {
  it('returns null when href missing or not https', () => {
    expect(buildListingHandoffText({ title: '垫', id: 'pl-1' })).toBeNull()
    expect(buildListingHandoffText({ title: '垫', id: 'pl-1', href: 'http://x' })).toBeNull()
  })
  it('returns null when id blank', () => {
    expect(buildListingHandoffText({
      title: '垫', id: '  ', href: 'https://item.example/1',
    })).toBeNull()
  })
  it('builds prompt with title, url, id, optional refs', () => {
    const text = buildListingHandoffText({
      title: '【优先试】硅胶沥水垫',
      id: 'pl-2',
      href: 'https://item.example/1',
      niche: '租房厨房',
      painPoint: '水渍',
      angle: '小户型',
    })
    expect(text).toContain('硅胶沥水垫')
    expect(text).not.toContain('【优先试】')
    expect(text).toContain('https://item.example/1')
    expect(text).toContain('来源选品条目：pl-2')
    expect(text).toContain('租房厨房')
  })
})
