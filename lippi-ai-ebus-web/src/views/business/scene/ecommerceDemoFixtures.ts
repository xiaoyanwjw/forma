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
    title: '硅胶沥水垫（多色）',
    painPoint: '水槽边易积水难打理',
    angle: '租房厨房刚需',
    diff: '多色套装好出图',
    niche: '厨房沥水',
  },
  {
    title: '免打孔置物架',
    painPoint: '墙面无处挂',
    angle: '租房搜索稳',
    diff: '免打孔轻包装',
    niche: '墙面收纳',
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
  sku: '硅胶沥水垫（多色）',
  title: '硅胶沥水垫（多色） · 易清洗 · 多色可选',
  body: '水槽边总积水？软硅胶垫贴合台面，洗完随手一垫。可卷收纳。',
}

export const DEMO_PICKS_VIEW: ComputerDocument = {
  version: 1,
  title: 'picklist',
  status: 'demo',
  blocks: [
    { type: 'note', text: '演示选品清单，非实时平台数据', tone: 'mute' },
    {
      type: 'list',
      ordered: true,
      items: DEMO_PICKS.map((it) => ({
        title: it.title,
        lines: [
          { kind: 'painPoint', text: it.painPoint },
          { kind: 'angle', text: it.angle },
          { kind: 'diff', text: it.diff },
          { kind: 'niche', text: it.niche },
        ],
      })),
    },
  ],
}

export const DEMO_LISTING_VIEW: ComputerDocument = {
  version: 1,
  title: 'listingPreview',
  status: 'demo',
  blocks: [
    { type: 'media', role: 'hero', placeholder: '主图方案预览', alt: '主图方案' },
    { type: 'section', heading: '详情标题', body: DEMO_LISTING.title },
    { type: 'section', heading: '详情正文', body: DEMO_LISTING.body, tone: 'mute' },
  ],
}

export const DEMO_SESSION_TITLE = '电商开店演示'
