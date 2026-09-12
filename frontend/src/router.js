import { createRouter, createWebHashHistory } from 'vue-router'
import { getUser } from './api'
import Login from './views/Login.vue'
import ParentView from './views/ParentView.vue'
import ClinicView from './views/ClinicView.vue'
import AefiView from './views/AefiView.vue'
import AdminView from './views/AdminView.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/parent', component: ParentView, roles: ['PARENT'] },
  { path: '/clinic', component: ClinicView, roles: ['DOCTOR', 'NURSE'] },
  { path: '/aefi', component: AefiView, roles: ['DOCTOR', 'NURSE', 'FOLLOWUP', 'ADMIN'] },
  { path: '/admin', component: AdminView, roles: ['ADMIN', 'FOLLOWUP'] },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const user = getUser()
  if (to.path === '/login') return true
  if (!user) return '/login'
  const rule = routes.find(r => r.path === to.path)
  if (rule && rule.roles && !rule.roles.includes(user.role)) return homeFor(user.role)
  return true
})

export function homeFor(role) {
  return { PARENT: '/parent', DOCTOR: '/clinic', NURSE: '/clinic', FOLLOWUP: '/aefi', ADMIN: '/admin' }[role] || '/login'
}

export default router
