/** Demo fixtures for story 3.3 Computer preview — not real generation output. */

export interface DemoPickItem {
  title: string
  reason: string
}

export interface DemoListingPreview {
  sku: string
  title: string
  body: string
}

export const DEMO_PICKS: DemoPickItem[] = [
  { title: '硅胶沥水垫（多色）', reason: '厨房刚需，图文好做差异化。' },
  { title: '免打孔置物架', reason: '租房搜索稳，包装轻。' },
  { title: '透明收纳盒套装', reason: '适合空间对比主图。' },
  { title: '触摸调光小夜灯', reason: '夜间氛围图好拍。' },
  { title: '磁吸理线器', reason: '客单低，适合测款。' },
  { title: '可水洗短毛地垫', reason: '评价点集中在清洗。' },
  { title: '显示器增高架', reason: '桌面刚需，写清承重。' },
  { title: '香薰蜡烛礼盒', reason: '礼赠场景多，勿夸大功效。' },
]

export const DEMO_LISTING: DemoListingPreview = {
  sku: '硅胶沥水垫（多色）',
  title: '硅胶沥水垫（多色） · 易清洗 · 多色可选',
  body: '水槽边总积水？软硅胶垫贴合台面，洗完随手一垫。可卷收纳。',
}

export const DEMO_SESSION_TITLE = '电商开店演示'
