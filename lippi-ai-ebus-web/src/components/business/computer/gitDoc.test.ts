import { describe, expect, it } from 'vitest'
import type { ComputerBlock } from '@/types/business/computerView'
import {
  buildStoryboardBeats,
  defaultGitFileName,
  hasStoryboardDoc,
  parseFramePromptEntries,
  toStoryboardDoc,
} from './gitDoc'

describe('parseFramePromptEntries', () => {
  it('parses numbered prompts with optional negative lines', () => {
    const entries = parseFramePromptEntries(
      '1. white bg product\n   negative: clutter\n2. lifestyle scene\n3. detail',
    )
    expect(entries).toHaveLength(3)
    expect(entries[0]).toEqual({ prompt: 'white bg product', negative: 'clutter' })
    expect(entries[1]).toEqual({ prompt: 'lifestyle scene', negative: undefined })
    expect(entries[2].prompt).toBe('detail')
  })
})

describe('buildStoryboardBeats', () => {
  it('zips frame captions with prompts one-to-one', () => {
    const beats = buildStoryboardBeats(
      ['首图：白底', '图2：场景'],
      '1. white bg\n2. lifestyle',
    )
    expect(beats).toEqual([
      { caption: '首图：白底', prompt: 'white bg', negative: undefined },
      { caption: '图2：场景', prompt: 'lifestyle', negative: undefined },
    ])
  })
})

describe('toStoryboardDoc', () => {
  it('maps storyboard sections and ordered frames without hero fields', () => {
    const blocks: ComputerBlock[] = [
      {
        type: 'media',
        role: 'hero',
        placeholder: '白底俯拍',
        mediaObjectId: 'm1',
      },
      {
        type: 'list',
        ordered: true,
        items: [{ title: '首图：白底' }, { title: '图2：场景' }],
      },
      { type: 'section', heading: '详情标题', body: '拓展坞标题' },
      { type: 'section', heading: '详情正文', body: '详情段落' },
      { type: 'section', heading: '展示说明', body: '主图顺序' },
      {
        type: 'section',
        heading: '生图 Prompt',
        body: '1. white bg\n2. lifestyle',
      },
    ]
    expect(toStoryboardDoc(blocks)).toEqual({
      detailTitle: '拓展坞标题',
      detailBody: '详情段落',
      displayNotes: '主图顺序',
      frames: ['首图：白底', '图2：场景'],
      framePromptsSummary: '1. white bg\n2. lifestyle',
    })
  })
})

describe('hasStoryboardDoc', () => {
  it('opts into storyboard layout for listing titles; keeps plan markdown and picklists on blocks', () => {
    expect(
      hasStoryboardDoc({
        title: 'listingPreview',
        blocks: [{ type: 'markdown', text: '## 主图分镜' }],
      }),
    ).toBe(true)
    expect(
      hasStoryboardDoc({
        title: '上架素材预览',
        blocks: [{ type: 'section', heading: '详情标题', body: '标题' }],
      }),
    ).toBe(true)
    expect(
      hasStoryboardDoc({
        title: '硅胶沥水垫 · 策划分镜',
        blocks: [{ type: 'markdown', text: '## 主图分镜\n1. 主图：白底产品' }],
      }),
    ).toBe(false)
    expect(
      hasStoryboardDoc({
        title: '选品清单',
        blocks: [
          { type: 'list', ordered: true, items: [{ title: '拓展坞' }] },
          { type: 'section', heading: '详情标题', body: '标题文案' },
        ],
      }),
    ).toBe(false)
  })
})

describe('defaultGitFileName', () => {
  it('slugs title into a .md file name', () => {
    expect(defaultGitFileName('选品清单')).toBe('选品清单.md')
    expect(defaultGitFileName('Mac Mini 拓展坞')).toBe('mac-mini-拓展坞.md')
    expect(defaultGitFileName('  ')).toBe('document.md')
  })
})
