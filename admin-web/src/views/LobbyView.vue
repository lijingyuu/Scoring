<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div>
        <p class="eyebrow">Eunomia</p>
        <h2>赛事后台</h2>
      </div>
      <nav>
        <RouterLink to="/lobby">赛事大厅</RouterLink>
        <RouterLink to="/create">创建比赛</RouterLink>
      </nav>
      <button class="ghost-action" @click="logout">退出登录</button>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <p class="eyebrow">Lobby</p>
          <h1>赛事大厅</h1>
        </div>
        <div class="user-pill">{{ profile?.nickname || '后台用户' }}</div>
      </header>

      <section class="toolbar">
        <input v-model.trim="keyword" placeholder="输入赛事名称或地点搜索全站赛事" @keyup.enter="runSearch" />
        <button class="secondary-action" @click="runSearch">搜索</button>
        <button class="ghost-action" @click="loadHome">刷新我的赛事</button>
      </section>

      <p v-if="error" class="error-text">{{ error }}</p>

      <section v-if="searchMode" class="panel">
        <div class="panel-head">
          <h2>搜索结果</h2>
          <button class="ghost-action small" @click="clearSearch">返回我的赛事</button>
        </div>
        <TournamentTable :items="searchResults" empty-text="没有匹配的赛事" />
      </section>

      <template v-else>
        <section class="panel">
          <div class="panel-head">
            <h2>我创建的赛事</h2>
            <RouterLink class="secondary-link" to="/create">新建比赛</RouterLink>
          </div>
          <TournamentTable :items="created" empty-text="还没有创建赛事" />
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>我收藏的赛事</h2>
          </div>
          <TournamentTable :items="favorites" empty-text="还没有收藏赛事" />
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  clearToken,
  fetchCreatedTournaments,
  fetchFavoriteTournaments,
  fetchMe,
  searchTournaments,
} from '../services/api'
import TournamentTable from '../components/TournamentTable.vue'

const router = useRouter()
const profile = ref(null)
const created = ref([])
const favorites = ref([])
const searchResults = ref([])
const keyword = ref('')
const searchMode = ref(false)
const error = ref('')

async function loadHome() {
  error.value = ''
  searchMode.value = false
  try {
    const [me, createdList, favoriteList] = await Promise.all([
      fetchMe(),
      fetchCreatedTournaments(),
      fetchFavoriteTournaments(),
    ])
    profile.value = me
    created.value = createdList || []
    favorites.value = favoriteList || []
  } catch (err) {
    error.value = err?.message || '加载失败'
  }
}

async function runSearch() {
  if (!keyword.value) {
    await loadHome()
    return
  }
  error.value = ''
  try {
    searchResults.value = await searchTournaments(keyword.value)
    searchMode.value = true
  } catch (err) {
    error.value = err?.message || '搜索失败'
  }
}

function clearSearch() {
  keyword.value = ''
  searchMode.value = false
}

function logout() {
  clearToken()
  router.replace('/login')
}

onMounted(loadHome)
</script>
