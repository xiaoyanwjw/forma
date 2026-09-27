<script setup lang="ts">
import { computed } from 'vue'
import type { ListingPlatformSkin, ListingPreviewContent } from './listingPlatform'

const props = defineProps<{
  platform: ListingPlatformSkin
  /** skill 产出的一套跨平台公共字段 */
  content: ListingPreviewContent
}>()

const titleLen = computed(() => (props.content.detailTitle || '').length)
const titleHint = computed(() => {
  if (props.platform === 'taobao') return `${titleLen.value}/60`
  if (props.platform === 'xianyu') return `${titleLen.value}/30`
  if (props.platform === 'douyin') return `${titleLen.value}/20`
  return `${titleLen.value}字`
})
const titleOverLimit = computed(() => {
  if (props.platform === 'taobao') return titleLen.value > 60
  if (props.platform === 'xianyu') return titleLen.value > 30
  if (props.platform === 'douyin') return titleLen.value > 20
  return false
})
const bodyOverLimit = computed(() => (props.content.detailBody || '').length > 2000)
</script>

<template>
  <div class="plat-preview" :class="{ 'is-adam': platform === 'adam' }">
    <!-- Adam：通用底稿，不要手机浏览器框 -->
    <div v-if="platform === 'adam'" class="adam-doc">
      <div class="listing-hero listing-hero-plan-card">
        <div class="listing-hero-meta">
          <span class="listing-hero-kicker">主图方案</span>
          <span v-if="content.heroMounted" class="listing-hero-chip">占位已挂载</span>
        </div>
        <p class="listing-hero-plan">{{ content.heroPlan || '主图方案待补充' }}</p>
      </div>
      <div class="listing-copy is-title">
        <h4>详情标题 <em>{{ titleHint }}</em></h4>
        <p class="section-body" :class="{ warn: titleOverLimit }">
          {{ content.detailTitle || '—' }}
        </p>
      </div>
      <div class="listing-copy is-body">
        <h4>详情正文</h4>
        <p class="section-body" :class="{ warn: bodyOverLimit }">
          {{ content.detailBody || '—' }}
        </p>
      </div>
      <div class="listing-copy is-notes">
        <h4>展示说明</h4>
        <p class="section-body">{{ content.displayNotes || '—' }}</p>
      </div>
    </div>

    <!-- 淘宝 / 闲鱼 / 抖音：手机商详壳 + skill 平台字段 -->
    <template v-else>
      <p class="plat-hint">示意界面，非官方接入 · 文案为跨平台公共底稿</p>

      <div class="iphone" :data-plat="platform">
        <div class="iphone-side-l" aria-hidden="true" />
        <div class="iphone-side-r" aria-hidden="true" />
        <div class="iphone-screen">
          <div class="iphone-island" aria-hidden="true" />
          <div
            class="iphone-status"
            :class="{ 'on-dark': platform === 'taobao' || platform === 'douyin' }"
          >
            <span>9:41</span>
            <span class="right">
              <span class="signal" aria-hidden="true"><i /><i /><i /><i /></span>
              <span class="bat" aria-hidden="true" />
            </span>
          </div>

          <div class="iphone-body">
            <template v-if="platform === 'taobao'">
              <div class="iphone-scroll">
                <div class="tb-bar">
                  <span>‹</span>
                  <div class="search">搜索店铺内宝贝</div>
                  <span>···</span>
                </div>
                <div class="prod-photo">
                  <div class="slide-bg s4" />
                  <p class="photo-plan on-light">{{ content.heroPlan || '主图方案' }}</p>
                </div>
                <div class="tb-price-row">
                  <div class="tb-price"><small>¥</small>29.9</div>
                  <div class="tb-sold">示意价 · 非实时</div>
                </div>
                <div class="tb-coupon">
                  <span>店铺券 ¥5</span>
                  <span>领券 ›</span>
                </div>
                <div class="tb-title" :class="{ warn: titleOverLimit }">
                  {{ content.detailTitle || '商品标题' }}
                  <span class="len-hint">{{ titleHint }}</span>
                </div>
                <div class="tb-tags">
                  <span>包邮</span>
                  <span>七天无理由</span>
                  <span>极速退款</span>
                </div>
                <div class="tb-shop">
                  <div class="logo">店</div>
                  <div>
                    <strong>一人优选旗舰店</strong>
                    <div class="sub">服务保障示意 · 非实时数据</div>
                  </div>
                </div>
                <div class="tb-detail">
                  <h5>— 宝贝详情 —</h5>
                  <div class="block">
                    <p class="pre">{{ content.detailBody || '详情正文' }}</p>
                  </div>
                  <div v-if="content.displayNotes" class="block">
                    <p class="pre">{{ content.displayNotes }}</p>
                  </div>
                </div>
              </div>
              <div class="tb-buy">
                <button type="button" class="ico" tabindex="-1">店铺</button>
                <button type="button" class="ico" tabindex="-1">客服</button>
                <button type="button" class="cart" tabindex="-1">加入购物车</button>
                <button type="button" class="buy" tabindex="-1">立即购买</button>
              </div>
            </template>

            <template v-else-if="platform === 'xianyu'">
              <div class="iphone-scroll">
                <div class="xy-bar">
                  <span>关闭</span>
                  <span class="xy-name">闲鱼</span>
                  <span>分享</span>
                </div>
                <div class="prod-photo">
                  <div class="slide-bg s2" />
                  <p class="photo-plan on-light">{{ content.heroPlan || '主图方案' }}</p>
                </div>
                <div class="xy-body">
                  <div class="xy-price"><small>¥</small>25</div>
                  <div class="xy-meta">想要 · 浏览为示意数据</div>
                  <div class="xy-title" :class="{ warn: titleOverLimit }">
                    {{ content.detailTitle || '闲置标题' }}
                    <span class="len-hint">{{ titleHint }}</span>
                  </div>
                  <div class="xy-chips">
                    <span>包邮</span>
                    <span>支持验货</span>
                    <span>个人闲置</span>
                  </div>
                  <div class="xy-detail pre">{{ content.detailBody }}</div>
                  <div v-if="content.displayNotes" class="xy-detail pre muted">
                    {{ content.displayNotes }}
                  </div>
                  <div class="xy-seller">
                    <div class="xy-avatar">陈</div>
                    <div>
                      <strong>一人店主小陈</strong>
                      <span>信用说明为示意</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="xy-cta">
                <button type="button" class="want" tabindex="-1">聊一聊</button>
                <button type="button" class="buy" tabindex="-1">立即购买</button>
              </div>
            </template>

            <template v-else>
              <div class="iphone-scroll dy-scroll">
                <div class="dy-bar">
                  <span>‹</span>
                  <span>商品</span>
                  <span>分享</span>
                </div>
                <div class="prod-photo dy-photo">
                  <div class="slide-bg s3" />
                  <p class="photo-plan on-dark">{{ content.heroPlan || '主图方案' }}</p>
                  <span class="dy-live">封面话术示意</span>
                </div>
                <div class="dy-panel">
                  <div class="dy-price"><small>¥</small>39.9 <span class="dy-strike">¥59</span></div>
                  <div class="dy-title" :class="{ warn: titleOverLimit }">
                    {{ content.detailTitle || '商品标题' }}
                    <span class="len-hint">{{ titleHint }}</span>
                  </div>
                  <div class="dy-tags">
                    <span>短句卖点</span>
                    <span>行动导向</span>
                  </div>
                  <div class="dy-detail">
                    <h5>商品详情</h5>
                    <p class="pre">{{ content.detailBody || '详情正文' }}</p>
                    <p v-if="content.displayNotes" class="dy-notes pre">{{ content.displayNotes }}</p>
                  </div>
                </div>
              </div>
              <div class="dy-cta">
                <button type="button" class="cart" tabindex="-1">加购</button>
                <button type="button" class="buy" tabindex="-1">立即购买</button>
              </div>
            </template>
          </div>

          <div class="iphone-home" :class="{ dark: platform === 'douyin' }">
            <span />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.plat-preview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plat-preview.is-adam {
  gap: 12px;
}

.plat-hint {
  margin: 0;
  font-size: 0.72rem;
  color: var(--mute);
  line-height: 1.4;
}

.pre {
  white-space: pre-wrap;
  word-break: break-word;
}

.warn {
  outline: 1px solid #f59e0b;
  outline-offset: 2px;
  border-radius: 4px;
}

.len-hint {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  font-weight: 500;
  color: #999;
}

.listing-copy h4 em {
  font-style: normal;
  font-weight: 500;
  color: var(--mute);
  margin-left: 6px;
}

.xy-detail.muted {
  margin-top: 8px;
  color: #78716c;
  font-size: 11px;
}

.adam-doc {
  display: flex;
  flex-direction: column;
}

.listing-hero {
  width: 100%;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background:
    radial-gradient(ellipse 80% 55% at 20% 0%, rgba(15, 23, 42, 0.04), transparent 55%),
    linear-gradient(165deg, #f4f6f8 0%, #e8ecf1 100%);
}

.listing-hero-plan-card {
  min-height: 120px;
  padding: 14px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-sizing: border-box;
}

.listing-hero-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.listing-hero-kicker {
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  color: #475569;
}

.listing-hero-chip {
  flex-shrink: 0;
  font-size: 0.65rem;
  font-weight: 600;
  color: #166534;
  background: #ecfdf3;
  border-radius: 999px;
  padding: 2px 8px;
}

.listing-hero-plan {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 500;
  line-height: 1.55;
  color: var(--ink);
  word-break: break-word;
}

.listing-copy {
  padding: 12px 0 4px;
  border-top: 1px solid var(--line-2);
}

.listing-copy h4 {
  margin: 0 0 6px;
  font-size: 0.68rem;
  font-weight: 650;
  color: var(--mute-2, #a3a3a3);
  letter-spacing: 0.04em;
}

.listing-copy .section-body {
  margin: 0;
  color: var(--ink);
  word-break: break-word;
}

.listing-copy.is-title .section-body {
  font-size: 1.02rem;
  font-weight: 650;
  line-height: 1.4;
}

.listing-copy.is-body .section-body {
  font-size: 0.875rem;
  font-weight: 450;
  line-height: 1.65;
}

.listing-copy.is-notes {
  margin-top: 4px;
  padding: 12px 12px 14px;
  border-top: 0;
  border-radius: var(--r-md);
  background: #f8fafc;
  border: 1px solid var(--line-2);
}

.listing-copy.is-notes h4 {
  color: #64748b;
}

.listing-copy.is-notes .section-body {
  font-size: 0.8rem;
  font-weight: 400;
  line-height: 1.6;
  color: var(--mute);
}

.iphone {
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
  position: relative;
  padding: 9px;
  background: linear-gradient(145deg, #4a4a4c 0%, #2c2c2e 40%, #1c1c1e 100%);
  border-radius: 36px;
  box-shadow:
    0 0 0 1.5px #6a6a6c,
    0 0 0 3px #1a1a1a,
    0 16px 36px rgba(0, 0, 0, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.iphone-side-l {
  position: absolute;
  left: -2.5px;
  top: 100px;
  width: 3px;
  height: 26px;
  background: #3a3a3c;
  border-radius: 2px 0 0 2px;
  box-shadow: 0 34px 0 #3a3a3c, 0 68px 0 #3a3a3c;
}

.iphone-side-r {
  position: absolute;
  right: -2.5px;
  top: 128px;
  width: 3px;
  height: 64px;
  background: #3a3a3c;
  border-radius: 0 2px 2px 0;
}

.iphone-screen {
  position: relative;
  background: #000;
  border-radius: 28px;
  overflow: hidden;
  height: 560px;
  display: flex;
  flex-direction: column;
}

.iphone-island {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  width: 88px;
  height: 26px;
  background: #000;
  border-radius: 16px;
  z-index: 6;
}

.iphone-status {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 22px 8px;
  color: #111;
  font-size: 12px;
  font-weight: 650;
  pointer-events: none;
}

.iphone-status.on-dark {
  color: #fff;
}

.iphone-status .right {
  display: flex;
  align-items: center;
  gap: 5px;
}

.iphone-status .signal {
  display: flex;
  gap: 1.5px;
  align-items: flex-end;
  height: 10px;
}

.iphone-status .signal i {
  width: 3px;
  background: currentColor;
  border-radius: 1px;
  display: block;
}

.iphone-status .signal i:nth-child(1) {
  height: 3px;
}
.iphone-status .signal i:nth-child(2) {
  height: 5px;
}
.iphone-status .signal i:nth-child(3) {
  height: 7px;
}
.iphone-status .signal i:nth-child(4) {
  height: 10px;
}

.iphone-status .bat {
  width: 20px;
  height: 9px;
  border: 1.2px solid currentColor;
  border-radius: 2.5px;
  position: relative;
}

.iphone-status .bat::after {
  content: '';
  position: absolute;
  inset: 1px 3px 1px 1px;
  background: #34c759;
  border-radius: 1px;
}

.iphone-body {
  flex: 1;
  min-height: 0;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.iphone-scroll {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.iphone-home {
  flex-shrink: 0;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.iphone-home.dark {
  background: #0a0a0a;
}

.iphone-home span {
  width: 112px;
  height: 4px;
  background: #1c1c1e;
  border-radius: 4px;
}

.iphone-home.dark span {
  background: #525252;
}

.prod-photo {
  aspect-ratio: 1;
  position: relative;
  overflow: hidden;
  background: #f0f0f0;
}

.slide-bg {
  position: absolute;
  inset: 0;
}

.slide-bg.s1 {
  background:
    radial-gradient(ellipse 70% 50% at 50% 78%, rgba(0, 0, 0, 0.07), transparent),
    linear-gradient(180deg, #f8f8f8, #ececec);
}

.slide-bg.s2 {
  background:
    radial-gradient(ellipse at 30% 40%, #fff, transparent 55%),
    linear-gradient(160deg, #fafafa, #e8eef5);
}

.slide-bg.s3 {
  background: linear-gradient(180deg, #1a1a1a, #333);
}

.slide-bg.s4 {
  background: linear-gradient(145deg, #fff7ed, #ffedd5 50%, #fed7aa);
}

.photo-plan {
  position: absolute;
  inset: auto 12px 14px;
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 11px;
  line-height: 1.45;
  max-height: 42%;
  overflow: auto;
}

.photo-plan.on-light {
  background: rgba(255, 255, 255, 0.88);
  color: #1a1a1a;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.photo-plan.on-dark {
  background: rgba(0, 0, 0, 0.55);
}

.photo-chip {
  position: absolute;
  top: 52px;
  right: 10px;
  font-size: 10px;
  font-weight: 600;
  color: #166534;
  background: #ecfdf3;
  border-radius: 999px;
  padding: 2px 8px;
}

/* Taobao */
.tb-bar {
  background: linear-gradient(90deg, #ff9000, #ff5000);
  color: #fff;
  padding: 48px 10px 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.tb-bar .search {
  flex: 1;
  background: rgba(255, 255, 255, 0.95);
  color: #bbb;
  border-radius: 14px;
  padding: 6px 10px;
  font-size: 11px;
}

.tb-price-row {
  padding: 10px 12px 4px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.tb-price {
  color: #ff5000;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.tb-price small {
  font-size: 13px;
  font-weight: 600;
  margin-right: 1px;
}

.tb-sold {
  color: #999;
  font-size: 11px;
}

.tb-coupon {
  margin: 6px 12px 0;
  background: #fff5f0;
  border: 1px solid #ffd0b5;
  border-radius: 6px;
  padding: 6px 8px;
  color: #ff5000;
  font-size: 11px;
  display: flex;
  justify-content: space-between;
}

.tb-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.tb-tags {
  padding: 0 12px 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tb-tags span {
  font-size: 10px;
  color: #ff5000;
  background: #fff7f2;
  border-radius: 3px;
  padding: 2px 6px;
}

.tb-shop {
  margin: 0 12px 10px;
  padding: 8px;
  background: #fafafa;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.tb-shop .logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(135deg, #ff9000, #ff5000);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
}

.tb-shop .sub {
  color: #999;
  font-size: 10px;
  margin-top: 2px;
}

.tb-detail {
  border-top: 8px solid #f5f5f5;
  padding: 12px;
}

.tb-detail h5 {
  margin: 0 0 8px;
  font-size: 12px;
  text-align: center;
  color: #999;
  font-weight: 500;
}

.tb-detail .block {
  background: #fafafa;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}

.tb-detail p {
  margin: 0;
  color: #333;
  line-height: 1.55;
  font-size: 12px;
}

.tb-buy {
  display: grid;
  grid-template-columns: 52px 52px 1fr 1fr;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
}

.tb-buy .ico,
.tb-buy .cart,
.tb-buy .buy {
  border: 0;
  font-size: 11px;
  padding: 10px 2px;
}

.tb-buy .ico {
  background: #fff;
  color: #666;
  font-size: 9px;
}

.tb-buy .cart {
  background: #ff9500;
  color: #fff;
  font-weight: 650;
}

.tb-buy .buy {
  background: #ff5000;
  color: #fff;
  font-weight: 650;
}

/* 闲鱼 */
.xy-bar {
  padding: 48px 12px 10px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #333;
  background: #fff;
}

.xy-name {
  font-weight: 700;
}

.xy-body {
  padding: 10px 12px 16px;
}

.xy-price {
  color: #ff4d00;
  font-size: 26px;
  font-weight: 800;
  line-height: 1;
}

.xy-price small {
  font-size: 14px;
}

.xy-meta {
  margin-top: 6px;
  font-size: 11px;
  color: #999;
}

.xy-title {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.xy-chips {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.xy-chips span {
  font-size: 10px;
  background: #fff8db;
  color: #78716c;
  border-radius: 4px;
  padding: 2px 6px;
}

.xy-detail {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.55;
  color: #444;
  white-space: pre-wrap;
}

.xy-seller {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 11px;
}

.xy-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #ffe60f;
  color: #111;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.xy-seller strong {
  display: block;
}

.xy-seller span {
  color: #999;
  font-size: 10px;
}

.xy-cta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.xy-cta button {
  border: 0;
  padding: 12px;
  font-size: 13px;
  font-weight: 650;
}

.xy-cta .want {
  background: #fff;
  color: #333;
}

.xy-cta .buy {
  background: #ffe60f;
  color: #111;
}

/* 抖音 */
.dy-scroll {
  background: #0a0a0a;
}

.dy-bar {
  background: #0a0a0a;
  color: #fff;
  padding: 48px 12px 10px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.dy-photo .dy-live {
  position: absolute;
  top: 52px;
  left: 10px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #fe2c55;
  border-radius: 4px;
  padding: 2px 6px;
}

.dy-panel {
  padding: 12px;
  background: #0a0a0a;
  color: #f5f5f5;
}

.dy-price {
  color: #fe2c55;
  font-size: 22px;
  font-weight: 800;
}

.dy-price small {
  font-size: 13px;
}

.dy-strike {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #737373;
  text-decoration: line-through;
}

.dy-title {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.4;
}

.dy-tags {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}

.dy-tags span {
  font-size: 10px;
  color: #fe2c55;
  background: rgba(254, 44, 85, 0.12);
  border-radius: 3px;
  padding: 2px 6px;
}

.dy-detail {
  margin-top: 14px;
  border-top: 1px solid #262626;
  padding-top: 12px;
}

.dy-detail h5 {
  margin: 0 0 8px;
  font-size: 12px;
  color: #a3a3a3;
  font-weight: 500;
}

.dy-detail p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: #e5e5e5;
}

.dy-notes {
  margin-top: 8px !important;
  color: #a3a3a3 !important;
}

.dy-cta {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  flex-shrink: 0;
}

.dy-cta button {
  border: 0;
  padding: 12px;
  font-size: 13px;
  font-weight: 650;
  color: #fff;
}

.dy-cta .cart {
  background: #333;
}

.dy-cta .buy {
  background: linear-gradient(90deg, #ff2e4d, #fe2c55);
}
</style>
