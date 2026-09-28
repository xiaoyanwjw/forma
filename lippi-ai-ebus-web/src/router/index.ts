import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: { name: 'scenes' },
    },
    {
      path: '/welcome',
      name: 'landing',
      component: () => import('@/views/marketing/LandingPage.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/identity/AuthRegister.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/identity/AuthLogin.vue'),
    },
    {
      path: '/me',
      name: 'me',
      component: () => import('@/views/identity/AccountSettings.vue'),
    },
    {
      path: '/credits',
      name: 'credits',
      component: () => import('@/views/business/credit/CreditPlan.vue'),
    },
    {
      path: '/scenes',
      name: 'scenes',
      component: () => import('@/views/business/scene/SceneGallery.vue'),
    },
    {
      path: '/scenes/ecommerce',
      name: 'scene-ecommerce',
      component: () => import('@/views/business/scene/EcommerceWorkspacePlaceholder.vue'),
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/business/history/HistoryPlaceholder.vue'),
    },
    {
      path: '/agent/dry-run',
      name: 'agent-dry-run',
      component: () => import('@/views/agent/AgentDryRun.vue'),
    },
  ],
})

export default router
