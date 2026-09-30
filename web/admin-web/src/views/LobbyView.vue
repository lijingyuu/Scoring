<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand-link" to="/lobby">
        <strong>Eunomia</strong>
      </RouterLink>
      <nav class="app-nav">
        <RouterLink to="/lobby">赛事大厅</RouterLink>
        <RouterLink to="/create">创建比赛</RouterLink>
      </nav>
      <div class="account-area">
        <div class="user-pill">{{ profile?.nickname || '工作台用户' }}</div>
        <button class="ghost-action small" @click="logout">退出登录</button>
      </div>
    </header>

    <!-- §9-5：新微信用户在 Web 端无完善资料入口，创建必被拒——给明确引导而非死路 -->
    <p v-if="profile && !profile.profileCompleted" class="profile-incomplete-banner">
      你的账号还未完善资料：请打开微信小程序「我的」页设置昵称与头像，完成后即可在此创建赛事（浏览与收藏不受影响）。
    </p>
    <main class="content">
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
          </div>
          <TournamentTable :items="created" empty-text="还没有创建赛事">
            <!-- 纯淘汰(type0)/小组+淘汰(type1) 赛事开赛前可手动调整；组别筛选在实际页面内完成 -->
            <template #actions="{ item }">
              <button v-if="canAdjustDraw(item)" class="text-action" type="button" @click="openDrawSlots(item)">
                调整签位
              </button>
              <button v-if="canAdjustGroups(item)" class="text-action" type="button" @click="openGroupAssignments(item)">
                调整分组
              </button>
            </template>
          </TournamentTable>
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

/** 纯淘汰(0) 与 小组+淘汰(1) 赛事提供手写签表调整入口；精确的 drawMode/淘汰赛是否已生成在页面内过滤 */
function canAdjustDraw(item) {
  const type = Number(item.tournamentType)
  return type === 0 || type === 1
}

/** 小组+淘汰赛事提供手写分组调整入口；精确的 drawMode 在页面内过滤 */
function canAdjustGroups(item) {
  return Number(item.tournamentType) === 1
}

function openDrawSlots(item) {
  router.push(`/tournaments/${item.id}/draw-slots`)
}

function openGroupAssignments(item) {
  router.push(`/tournaments/${item.id}/group-assignments`)
}

onMounted(loadHome)
</script>

<style scoped>
.profile-incomplete-banner {
  margin: 12px 24px 0;
  padding: 10px 14px;
  border: 1px solid #f0c36d;
  background: #fdf6e3;
  color: #7a5c00;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
}
</style>
