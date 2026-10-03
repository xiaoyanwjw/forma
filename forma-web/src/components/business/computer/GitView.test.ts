import { createApp, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import GitView from './GitView.vue'

describe('GitView', () => {
  it('renders file bar and slotted body', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp({
      components: { GitView },
      template: '<GitView file-name="picklist.md" meta="已结算"><p class="slot-body">清单正文</p></GitView>',
    })
    app.mount(host)
    await nextTick()
    expect(host.querySelector('[data-testid="git-view"]')).toBeTruthy()
    expect(host.querySelector('.git-filebar .name')?.textContent).toBe('picklist.md')
    expect(host.querySelector('.git-filebar')?.textContent).toMatch(/已结算/)
    expect(host.querySelector('.git-md .slot-body')?.textContent).toBe('清单正文')
    app.unmount()
    host.remove()
  })
})
