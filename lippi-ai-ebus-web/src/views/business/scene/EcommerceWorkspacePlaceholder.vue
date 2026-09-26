<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import AppHeader from '@/components/common/AppHeader.vue'
import { getScenes } from '@/api/business/scene/scene'
import {
  DEMO_LISTING,
  DEMO_PICKS,
  DEMO_SESSION_TITLE,
} from '@/views/business/scene/ecommerceDemoFixtures'
import '@/views/business/scene/ecommerceWorkspaceSession.css'

/** Stable scene binding for this workbench — Epic 3 session create must carry it */
const SCENE_CODE = 'ecommerce' as const
const SCENE_BREADCRUMB = '电商开店'
const DEMO_SOON = '演示壳暂不支持附件与真实生成'

const PICKS_TEMPLATE = '请帮我生成【品类】类选品清单，客单价【最低价】–【最高价】元。'
const LISTING_TEMPLATE =
  '请为商品「【商品名称】」生成上架素材，优先适配【淘宝/拼多多/闲鱼】。'

type ShellMode = 'empty' | 'session'
type ComputerKind = 'picks' | 'listing' | null

interface DemoMessage {
  id: string
  role: 'user' | 'agent'
  text: string
}

const mode = ref<ShellMode>('empty')
const prompt = ref('')
const sessionPrompt = ref('')
/** Optional Catalog bizId when list is available; null if unresolved */
const sceneBizId = ref<string | null>(null)
const messages = ref<DemoMessage[]>([])
const sessionTitle = ref(DEMO_SESSION_TITLE)
const computerKind = ref<ComputerKind>(null)
const chatScrollEl = ref<HTMLElement | null>(null)
const computerEl = ref<HTMLElement | null>(null)

const computerOpen = computed(() => computerKind.value != null)
const homeSendEnabled = computed(() => prompt.value.trim().length > 0)
const sessionSendEnabled = computed(() => sessionPrompt.value.trim().length > 0)

let msgSeq = 0
function nextMsgId() {
  msgSeq += 1
  return `demo-msg-${msgSeq}`
}

function agentDemoReply(): string {
  return '这是会话态演示：右侧 Computer 需用下方预览按钮打开，不调用生成接口。真生成与结算将在后续故事接入。'
}

function enterSession(userText: string) {
  const text = userText.trim()
  if (!text) return

  mode.value = 'session'
  messages.value = [
    { id: nextMsgId(), role: 'user', text },
    { id: nextMsgId(), role: 'agent', text: agentDemoReply() },
  ]
  sessionTitle.value = text.length > 18 ? `${text.slice(0, 18)}…` : text
  prompt.value = ''
  sessionPrompt.value = ''
  computerKind.value = null
}

function fillPicksEmpty() {
  prompt.value = PICKS_TEMPLATE
  enterSession(PICKS_TEMPLATE)
}

function fillListingEmpty() {
  prompt.value = LISTING_TEMPLATE
  enterSession(LISTING_TEMPLATE)
}

function sendFromEmpty() {
  enterSession(prompt.value)
}

function fillPicksSession() {
  sessionPrompt.value = PICKS_TEMPLATE
}

function fillListingSession() {
  sessionPrompt.value = LISTING_TEMPLATE
}

function sendFromSession() {
  const text = sessionPrompt.value.trim()
  if (!text) return
  messages.value.push(
    { id: nextMsgId(), role: 'user', text },
    { id: nextMsgId(), role: 'agent', text: agentDemoReply() },
  )
  sessionPrompt.value = ''
  void nextTick(() => {
    const el = chatScrollEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function revealComputer() {
  void nextTick(() => {
    computerEl.value?.scrollIntoView({ block: 'nearest' })
  })
}

function openPicksComputer() {
  computerKind.value = 'picks'
  revealComputer()
}

function openListingComputer() {
  computerKind.value = 'listing'
  revealComputer()
}

function closeComputer() {
  computerKind.value = null
}

function newTask() {
  mode.value = 'empty'
  messages.value = []
  computerKind.value = null
  prompt.value = ''
  sessionPrompt.value = ''
  sessionTitle.value = DEMO_SESSION_TITLE
}

function padIndex(i: number) {
  return String(i + 1).padStart(2, '0')
}

onMounted(async () => {
  try {
    const data = await getScenes()
    const hit = Array.isArray(data)
      ? data.find((s) => s.sceneCode === SCENE_CODE && s.status === 'AVAILABLE')
      : undefined
    if (hit?.bizId) {
      sceneBizId.value = hit.bizId
    }
  } catch {
    // Empty UI still works with sceneCode alone; Epic 3 will harden lookup
  }
})
</script>

<template>
  <div
    class="shell"
    :data-scene-code="SCENE_CODE"
    :data-scene-biz-id="sceneBizId ?? undefined"
  >
    <AppHeader :scene-breadcrumb="SCENE_BREADCRUMB" />

    <div v-if="mode === 'empty'" class="home">
      <main class="home-main">
        <h1>我能为你做什么？</h1>
        <div class="quick-row" role="group" aria-label="快捷任务">
          <button type="button" class="pill" @click="fillPicksEmpty">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path d="M9 11l3 3L22 4" />
              <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
            </svg>
            选品清单
          </button>
          <button type="button" class="pill" @click="fillListingEmpty">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M3 9h18M9 21V9" />
            </svg>
            生成上架素材
          </button>
        </div>
        <div class="prompt-box">
          <textarea
            v-model="prompt"
            class="prompt-editor"
            rows="3"
            placeholder="分配一个任务或提问任何问题"
            aria-label="提问"
          />
          <div class="prompt-toolbar">
            <button
              type="button"
              class="icon-btn"
              aria-label="附件"
              aria-describedby="demo-soon-hint"
              disabled
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </button>
            <button
              type="button"
              class="send-btn"
              :class="{ active: homeSendEnabled }"
              aria-label="发送"
              :disabled="!homeSendEnabled"
              @click="sendFromEmpty"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" />
              </svg>
            </button>
          </div>
          <p id="demo-soon-hint" class="sr-only">{{ DEMO_SOON }}</p>
        </div>
      </main>
    </div>

    <div v-else class="session" data-testid="session-shell">
      <aside class="sidebar" aria-label="会话侧栏">
        <button type="button" class="side-new" @click="newTask">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          新任务
        </button>
        <div class="side-section">当前</div>
        <button type="button" class="side-item on">
          {{ sessionTitle }}
          <span class="sub">进行中 · 电商开店</span>
        </button>
      </aside>

      <div class="workspace" :class="{ split: computerOpen }">
        <section class="chat-pane">
          <div class="chat-toolbar">
            <button type="button" class="chat-new-task" @click="newTask">新任务</button>
          </div>
          <div ref="chatScrollEl" class="chat-scroll" role="log" aria-live="polite">
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="m.role"
            >
              <div class="role">{{ m.role === 'user' ? '你' : 'Adam' }}</div>
              <div class="body">
                <p>{{ m.text }}</p>
                <div v-if="m.role === 'agent'" class="demo-actions">
                  <button
                    type="button"
                    class="pill"
                    data-demo="open-picks"
                    @click="openPicksComputer"
                  >
                    预览选品清单
                  </button>
                  <button
                    type="button"
                    class="pill"
                    data-demo="open-listing"
                    @click="openListingComputer"
                  >
                    预览上架素材
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input-wrap">
            <div class="quick-row" role="group" aria-label="快捷任务">
              <button type="button" class="pill" @click="fillPicksSession">选品清单</button>
              <button type="button" class="pill" @click="fillListingSession">生成上架素材</button>
            </div>
            <div class="prompt-box">
              <textarea
                v-model="sessionPrompt"
                class="prompt-editor"
                rows="2"
                placeholder="分配一个任务或提问任何问题"
                aria-label="继续提问"
              />
              <div class="prompt-toolbar">
                <button
                  type="button"
                  class="icon-btn"
                  aria-label="附件"
                  aria-describedby="session-demo-soon-hint"
                  disabled
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path d="M12 5v14M5 12h14" />
                  </svg>
                </button>
                <button
                  type="button"
                  class="send-btn"
                  :class="{ active: sessionSendEnabled }"
                  aria-label="发送"
                  :disabled="!sessionSendEnabled"
                  @click="sendFromSession"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                    <path d="M12 19V5M5 12l7-7 7 7" />
                  </svg>
                </button>
              </div>
              <p id="session-demo-soon-hint" class="sr-only">{{ DEMO_SOON }}</p>
            </div>
          </div>
        </section>

        <aside ref="computerEl" class="computer" id="computer" aria-label="Adam's Computer">
          <div class="computer-bar">
            <div class="title">
              <span class="dots" aria-hidden="true"><i /><i /><i /></span>
              Adam's Computer
            </div>
            <button type="button" class="icon-btn" id="close-computer" aria-label="关闭" @click="closeComputer">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="computer-body">
            <div v-if="computerKind === 'picks'" class="comp-card">
              <div class="comp-card-head">
                <span>选品清单 · 家居类</span>
                <span class="status">演示</span>
              </div>
              <div class="comp-card-body">
                <ol class="pick-list">
                  <li v-for="(item, i) in DEMO_PICKS" :key="item.title">
                    <span class="n">{{ padIndex(i) }}</span>
                    <div>
                      <div class="t">{{ item.title }}</div>
                      <div class="r">{{ item.reason }}</div>
                    </div>
                  </li>
                </ol>
              </div>
            </div>

            <div v-else-if="computerKind === 'listing'" class="comp-card">
              <div class="comp-card-head">
                <span>上架素材预览</span>
                <span class="status">演示</span>
              </div>
              <div class="comp-card-body">
                <div class="listing-stack">
                  <div class="listing-hero" aria-hidden="true">主图方案预览</div>
                  <div class="listing-copy">
                    <h4>详情标题</h4>
                    <p class="title-text">{{ DEMO_LISTING.title }}</p>
                    <h4>详情正文</h4>
                    <p class="body-text">{{ DEMO_LISTING.body }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.home {
  min-height: calc(100vh - var(--header-h));
  display: flex;
  flex-direction: column;
}

.home-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px 80px;
  width: min(720px, 100%);
  margin: 0 auto;
}

.home-main h1 {
  margin: 0 0 28px;
  font-size: clamp(1.75rem, 3.5vw, 2.35rem);
  font-weight: 600;
  letter-spacing: -0.02em;
  text-align: center;
  color: var(--ink);
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin: 4px 0 14px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
  font-size: 0.8125rem;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.pill:hover {
  background: var(--line-2);
}

.pill svg {
  opacity: 0.7;
  flex-shrink: 0;
}

.prompt-box {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow);
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prompt-editor {
  border: 0;
  resize: none;
  min-height: 56px;
  background: transparent;
  color: var(--ink);
  font-size: 0.95rem;
  line-height: 1.7;
  width: 100%;
}

.prompt-editor::placeholder {
  color: var(--mute-2);
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.icon-btn,
.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 0;
  display: grid;
  place-items: center;
  color: var(--mute);
}

.icon-btn {
  background: transparent;
  cursor: not-allowed;
}

.send-btn {
  background: var(--line-2);
  cursor: pointer;
}

.send-btn.active {
  background: var(--accent);
  color: #fff;
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.icon-btn:disabled {
  opacity: 0.7;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
