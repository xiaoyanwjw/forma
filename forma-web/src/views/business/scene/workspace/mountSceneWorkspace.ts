import { createApp, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import Workspace from '@/views/business/scene/Workspace.vue'

export async function flushUi() {
  await nextTick()
  await new Promise((r) => setTimeout(r, 0))
  await nextTick()
}

/** Mount unified Workspace on `/scenes/:sceneCode`. */
export async function mountSceneWorkspace(
  sceneCode: 'ecommerce' | 'xiaohongshu' | 'tech_digest',
) {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'landing', component: { template: '<div />' } },
      { path: '/scenes', name: 'scenes', component: { template: '<div>gallery</div>' } },
      {
        path: '/scenes/:sceneCode',
        name: 'scene-workspace',
        component: Workspace,
      },
      { path: '/history', name: 'history', component: { template: '<div />' } },
      { path: '/credits', name: 'credits', component: { template: '<div />' } },
      { path: '/me', name: 'me', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push(`/scenes/${sceneCode}`)
  await router.isReady()
  const app = createApp(Workspace)
  app.use(router)
  app.mount(root)
  await flushUi()
  await flushUi()
  return {
    root,
    router,
    unmount() {
      app.unmount()
      root.remove()
    },
  }
}
