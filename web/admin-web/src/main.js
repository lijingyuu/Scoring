import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import LobbyView from './views/LobbyView.vue'
import CreateTournamentView from './views/CreateTournamentView.vue'
import { getToken } from './services/api'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/lobby' },
    { path: '/login', component: LoginView },
    { path: '/lobby', component: LobbyView, meta: { auth: true } },
    { path: '/create', component: CreateTournamentView, meta: { auth: true } },
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
