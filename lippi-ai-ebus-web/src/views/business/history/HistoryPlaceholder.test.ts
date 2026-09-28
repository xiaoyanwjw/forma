import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearToken, setToken } from '@/api/http'
import HistoryPlaceholder from '@/views/business/history/HistoryPlaceholder.vue'

async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

function jsonOk(data: unknown) {
  return new Response(JSON.stringify({ success: true, code: 0, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function mountHistory() {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/history', name: 'history', component: HistoryPlaceholder },
      { path: '/scenes', name: 'scenes', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push('/history')
  await router.isReady()
  const app = createApp(HistoryPlaceholder)
  app.use(router)
  app.mount(root)
  await flushUi()
  return {
    root,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}

describe('HistoryPlaceholder', () => {
  let unmount: (() => void) | undefined

  beforeEach(() => {
    clearToken()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setToken('jwt-history')
  })

  afterEach(() => {
    unmount?.()
    unmount = undefined
  })

  it('shows empty state for near-60-day list', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/history/artifacts') && !url.match(/artifacts\/[^?/]+$/)) {
        return jsonOk([])
      }
      if (url.includes('/api/v1/credits')) {
        return jsonOk({
          tier: 'FREE',
          available: 20,
          balance: 20,
          reserved: 0,
          nextResetAt: '2026-10-24T10:00:00Z',
          periodAnchorAt: '2026-09-24T10:00:00Z',
        })
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountHistory()
    unmount = mounted.unmount
    expect(mounted.root.querySelector('[data-testid="history-empty"]')).toBeTruthy()
    expect(mounted.root.textContent).toMatch(/生成历史/)
  })

  it('lists items, filters by scene, and opens Computer detail', async () => {
    const list = [
      {
        id: 'a1',
        artifactType: 'picklist',
        sceneCode: 'ecommerce',
        title: '家居选品',
        createdAt: '2026-09-28T01:00:00Z',
      },
    ]
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/credits')) {
        return jsonOk({
          tier: 'FREE',
          available: 20,
          balance: 20,
          reserved: 0,
          nextResetAt: '2026-10-24T10:00:00Z',
          periodAnchorAt: '2026-09-24T10:00:00Z',
        })
      }
      if (url.includes('/api/v1/history/artifacts/a1')) {
        return jsonOk({
          ...list[0],
          view: {
            version: 1,
            title: '选品清单',
            blocks: [{ type: 'note', text: '历史预览', tone: 'mute' }],
          },
        })
      }
      if (url.includes('sceneCode=xiaohongshu') || url.includes('sceneCode=nope')) {
        return jsonOk([])
      }
      if (url.includes('/api/v1/history/artifacts')) {
        return jsonOk(list)
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountHistory()
    unmount = mounted.unmount
    expect(mounted.root.querySelector('[data-testid="history-list"]')?.textContent).toMatch(
      /家居选品/,
    )

    ;(mounted.root.querySelector('.history-item') as HTMLElement).click()
    await flushUi()
    await flushUi()
    expect(mounted.root.querySelector('[data-testid="history-detail"]')?.textContent).toMatch(
      /历史预览|Adam's Computer/,
    )

    const select = mounted.root.querySelector(
      '[data-testid="history-scene-filter"]',
    ) as HTMLSelectElement
    const fetchMock = globalThis.fetch as unknown as {
      mock: { calls: ReadonlyArray<unknown[]> }
    }
    select.value = 'xiaohongshu'
    select.dispatchEvent(new Event('change', { bubbles: true }))
    await flushUi()
    await flushUi()

    const sceneCalls = fetchMock.mock.calls.filter(([input]) =>
      String(input).includes('/api/v1/history/artifacts'),
    )
    expect(
      sceneCalls.some(([input]) => String(input).includes('sceneCode=xiaohongshu')),
    ).toBe(true)
    expect(mounted.root.querySelector('[data-testid="history-empty"]')).toBeTruthy()
  })

  it('opens drawer with tabs 成果 default and 对话 messages', async () => {
    const withSession = {
      id: 'a1',
      artifactType: 'picklist',
      sceneCode: 'ecommerce',
      title: '家居选品',
      createdAt: '2026-09-28T01:00:00Z',
    }
    const withoutSession = {
      id: 'a2',
      artifactType: 'sku',
      sceneCode: 'ecommerce',
      title: '无会话成果',
      createdAt: '2026-09-27T01:00:00Z',
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/api/v1/credits')) {
        return jsonOk({
          tier: 'FREE',
          available: 20,
          balance: 20,
          reserved: 0,
          nextResetAt: '2026-10-24T10:00:00Z',
          periodAnchorAt: '2026-09-24T10:00:00Z',
        })
      }
      if (url.includes('/api/v1/history/artifacts/a1')) {
        return jsonOk({
          ...withSession,
          sessionId: 'sess-1',
          view: {
            version: 1,
            title: '选品清单',
            blocks: [{ type: 'note', text: '历史预览', tone: 'mute' }],
          },
        })
      }
      if (url.includes('/api/v1/history/artifacts/a2')) {
        return jsonOk({
          ...withoutSession,
          sessionId: null,
          view: {
            version: 1,
            title: '上架素材',
            blocks: [{ type: 'note', text: '无会话预览', tone: 'mute' }],
          },
        })
      }
      if (url.includes('/api/v1/sessions/sess-1/messages')) {
        return jsonOk([
          { role: 'user', content: '找杯子', createdAt: null },
          { role: 'assistant', content: '这是建议', createdAt: null },
        ])
      }
      if (url.includes('/api/v1/history/artifacts')) {
        return jsonOk([withSession, withoutSession])
      }
      return new Response('not found', { status: 404 })
    })

    const mounted = await mountHistory()
    unmount = mounted.unmount
    const items = mounted.root.querySelectorAll('.history-item')
    expect(items.length).toBe(2)

    ;(items[0] as HTMLElement).click()
    await flushUi()
    await flushUi()

    const drawer = mounted.root.querySelector('[data-testid="history-drawer"]')
    expect(drawer).toBeTruthy()
    expect(mounted.root.querySelector('[data-testid="history-list"]')).toBeTruthy()
    expect(drawer?.textContent).toMatch(/成果/)
    expect(drawer?.textContent).toMatch(/对话/)
    const artifactTab = mounted.root.querySelector(
      '[data-testid="history-tab-artifact"]',
    ) as HTMLButtonElement
    const chatTab = mounted.root.querySelector(
      '[data-testid="history-tab-chat"]',
    ) as HTMLButtonElement
    expect(artifactTab.getAttribute('aria-selected')).toBe('true')
    expect(chatTab.getAttribute('aria-selected')).toBe('false')
    expect(mounted.root.querySelector('[data-testid="history-detail"]')?.textContent).toMatch(
      /历史预览|Adam's Computer/,
    )

    const fetchMock = globalThis.fetch as unknown as {
      mock: { calls: ReadonlyArray<unknown[]> }
    }
    expect(
      fetchMock.mock.calls.some(([input]) => String(input).includes('/api/v1/sessions/')),
    ).toBe(false)

    chatTab.click()
    await flushUi()
    await flushUi()
    expect(chatTab.getAttribute('aria-selected')).toBe('true')
    expect(
      fetchMock.mock.calls.some(([input]) =>
        String(input).includes('/api/v1/sessions/sess-1/messages'),
      ),
    ).toBe(true)
    expect(mounted.root.querySelector('[data-testid="history-chat"]')?.textContent).toMatch(
      /找杯子/,
    )
    expect(mounted.root.querySelector('[data-testid="history-chat"]')?.textContent).toMatch(
      /这是建议/,
    )

    ;(items[1] as HTMLElement).click()
    await flushUi()
    await flushUi()
    const chatTabAgain = mounted.root.querySelector(
      '[data-testid="history-tab-chat"]',
    ) as HTMLButtonElement
    expect(mounted.root.querySelector('[data-testid="history-tab-artifact"]')?.getAttribute(
      'aria-selected',
    )).toBe('true')
    chatTabAgain.click()
    await flushUi()
    await flushUi()
    expect(mounted.root.querySelector('[data-testid="history-chat-empty"]')?.textContent).toMatch(
      /暂无会话记录/,
    )
    expect(
      fetchMock.mock.calls.filter(([input]) => String(input).includes('/api/v1/sessions/')).length,
    ).toBe(1)
  })
})
