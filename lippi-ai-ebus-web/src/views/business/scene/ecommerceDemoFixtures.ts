import type { ComputerDocument } from '@/types/business/computerView'

/** Demo fixtures for story 3.3 Computer preview — not real generation output. */

export interface DemoPickItem {
  title: string
  painPoint: string
  angle: string
  diff: string
  niche: string
}

export interface DemoListingPreview {
  sku: string
  title: string
  body: string
}

export const DEMO_PICKS: DemoPickItem[] = [
  {
    title: 'Mac Mini 拓展坞',
    painPoint: 'Mini 接显示器后接口不够、线乱',
    angle: '居家办公桌搭',
    diff: '机身同宽好出图',
    niche: 'Mac Mini 扩展',
  },
  {
    title: 'Mac Mini 增高底座',
    painPoint: '机身接口朝后难插',
    angle: '桌面搜索稳',
    diff: '抬高后插线更顺',
    niche: 'Mini 支架',
  },
  {
    title: '透明收纳盒套装',
    painPoint: '抽屉杂乱',
    angle: '空间对比主图好拍',
    diff: '透明可视分层',
    niche: '桌面收纳',
  },
  {
    title: '触摸调光小夜灯',
    painPoint: '夜间起夜晃眼',
    angle: '氛围图好拍',
    diff: '触摸调光',
    niche: '照明小件',
  },
  {
    title: '磁吸理线器',
    painPoint: '线材缠绕',
    angle: '客单低适合测款',
    diff: '磁吸可复用',
    niche: '桌面理线',
  },
  {
    title: '可水洗短毛地垫',
    painPoint: '门口易脏',
    angle: '评价点集中清洗',
    diff: '可机洗短毛',
    niche: '地垫',
  },
  {
    title: '显示器增高架',
    painPoint: '桌面拥挤',
    angle: '办公刚需',
    diff: '承重写清',
    niche: '桌面支架',
  },
  {
    title: '香薰蜡烛礼盒',
    painPoint: '礼赠缺场景感',
    angle: '礼赠场景多',
    diff: '礼盒包装',
    niche: '礼赠香氛',
  },
]

export const DEMO_LISTING: DemoListingPreview = {
  sku: 'Mac Mini 拓展坞',
  title: 'Mac Mini 拓展坞 · 多口扩展 · 走线隐藏',
  body: 'Mini 接显示器总缺口？拓展坞把 HDMI、USB、网线收到机身下，桌面只留一套线。',
}

export const DEMO_PICKS_VIEW: ComputerDocument = {
  version: 1,
  title: '选品清单',
  status: '演示',
  blocks: [
    { type: 'note', text: '演示选品清单，非实时平台数据', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: DEMO_PICKS.map((it) => ({
        title: it.title,
        lines: [
          { kind: 'painPoint', label: '痛点', text: it.painPoint },
          { kind: 'angle', label: '切入', text: it.angle },
          { kind: 'diff', label: '差异', text: it.diff },
          { kind: 'niche', label: '细分', text: it.niche },
        ],
      })),
    },
  ],
}

export const DEMO_LISTING_VIEW: ComputerDocument = {
  version: 1,
  title: '上架素材预览',
  status: '演示',
  blocks: [
    { type: 'media', role: 'hero', placeholder: '主图方案预览', alt: '主图方案' },
    { type: 'section', heading: '详情标题', body: DEMO_LISTING.title },
    { type: 'section', heading: '详情正文', body: DEMO_LISTING.body, tone: 'mute' },
  ],
}

export const DEMO_SESSION_TITLE = '电商开店演示'
