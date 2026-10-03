import { createApp, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import SlotAwarePromptEditor from './SlotAwarePromptEditor.vue'

describe('SlotAwarePromptEditor', () => {
  it('selects the 「…」 slot on mouseup so typing replaces it', async () => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const app = createApp(SlotAwarePromptEditor, {
      modelValue: '请为商品「Mac Mini 拓展坞」生成上架素材。',
      ariaLabel: '继续提问',
    })
    app.mount(host)
    await nextTick()
    const area = host.querySelector('textarea') as HTMLTextAreaElement
    const start = area.value.indexOf('「')
    const end = area.value.indexOf('」') + 1
    area.focus()
    area.setSelectionRange(start + 2, start + 2)
    area.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await nextTick()
    expect(area.selectionStart).toBe(start)
    expect(area.selectionEnd).toBe(end)
    expect(host.querySelector('.ph')?.textContent).toBe('「Mac Mini 拓展坞」')
    app.unmount()
    host.remove()
  })
})
