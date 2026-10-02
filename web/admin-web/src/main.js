import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import LobbyView from './views/LobbyView.vue'
import CreateTournamentView from './views/CreateTournamentView.vue'
import DrawSlotsView from './views/DrawSlotsView.vue'
import GroupAssignmentsView from './views/GroupAssignmentsView.vue'
import { getToken } from './services/api'
import { THEME_KEY, normalizeTheme } from './themes'
import './styles.css'

// 挂载前应用保存的主题（已删除的主题回退默认），避免首帧闪回错误配色
document.documentElement.dataset.theme = normalizeTheme(localStorage.getItem(THEME_KEY))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/lobby' },
    { path: '/login', component: LoginView },
    { path: '/lobby', component: LobbyView, meta: { auth: true } },
    { path: '/create', component: CreateTournamentView, meta: { auth: true } },
    { path: '/tournaments/:id/draw-slots', component: DrawSlotsView, meta: { auth: true } },
    { path: '/tournaments/:id/group-assignments', component: GroupAssignmentsView, meta: { auth: true } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.auth && !getToken()) {
    return '/login'
  }
  if (to.path === '/login' && getToken()) {
    return '/lobby'
  }
  return true
})

createApp(App).use(router).mount('#app')
