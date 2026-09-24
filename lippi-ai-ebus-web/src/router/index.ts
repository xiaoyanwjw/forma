import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'login' } },
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
      component: () => import('@/views/identity/AuthMe.vue'),
    },
    {
      path: '/credits',
      name: 'credits',
      component: () => import('@/views/business/credit/CreditPlan.vue'),
    },
  ],
})

export default router
