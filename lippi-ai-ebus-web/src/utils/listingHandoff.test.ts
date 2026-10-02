import { describe, expect, it } from 'vitest'
import { buildListingHandoffText, listingHandoffTextForItem } from './listingHandoff'

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
      title: '【优先试】Mac Mini 拓展坞',
      id: 'pl-2',
      href: 'https://item.example/1',
      niche: '居家办公',
      painPoint: '接口不够',
      angle: '桌搭藏线',
    })
    expect(text).toContain('Mac Mini 拓展坞')
    expect(text).not.toContain('【优先试】')
    expect(text).toContain('https://item.example/1')
    expect(text).toContain('来源选品条目：pl-2')
    expect(text).toContain('居家办公')
  })
})

describe('listingHandoffTextForItem', () => {
  it('reads niche/pain/angle by kind and falls back to pl-n id', () => {
    const text = listingHandoffTextForItem(
      {
        title: '拓展坞',
        href: 'https://item.example/1',
        lines: [
          { kind: 'niche', text: '居家办公' },
          { kind: 'painPoint', text: '水渍' },
          { kind: 'angle', text: '小户型' },
        ],
      },
      0,
    )
    expect(text).toContain('来源选品条目：pl-1')
    expect(text).toContain('居家办公')
    expect(text).toContain('水渍')
  })

  it('returns null without https href', () => {
    expect(
      listingHandoffTextForItem({ title: '拓展坞', id: 'pl-1', lines: [] }, 0),
    ).toBeNull()
  })
})
