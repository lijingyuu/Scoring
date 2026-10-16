<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
       <text class="title">{{ isTeam ? '查看队伍' : '参赛选手' }}</text>
    </view>

    <view class="state-layer" v-if="loading">
       <text class="state-text">正在加载{{ isTeam ? '队伍' : '名单' }}...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadTeams">重新加载</button>
    </view>

    <view v-else class="content">
      <view class="summary-card">
         <text class="summary-title">{{ tournamentName || (isTeam ? '队伍列表' : '选手名单') }}</text>
         <text class="summary-meta">共 {{ teams.length }} {{ isTeam ? '支队伍' : '名选手' }}</text>
      </view>

      <view v-if="teams.length" class="team-list">
        <view class="team-card" v-for="team in teams" :key="team.id">
           <view class="team-main" @click="isTeam ? openTeam(team) : null">
             <text class="team-name">{{ team.name }}</text>
             <text class="team-desc" v-if="isTeam">{{ team.memberCount || 0 }} 人 / 队长 {{ team.captainName || '-' }}</text>
             <text class="team-desc" v-else-if="team.seedRank">种子序号：{{ team.seedRank }}</text>
           </view>
           <view class="team-actions">
             <template v-if="isTeam">
               <text v-if="isCreator" class="team-action" @click.stop="openTeamEdit(team)">编辑</text>
               <text class="team-arrow" @click.stop="openTeam(team)">查看</text>
             </template>
             <template v-else>
               <text v-if="isCreator && !tournamentStarted" class="team-action" @click.stop="promptRenamePlayer(team)">改名</text>
               <text v-else-if="isCreator && tournamentStarted" class="team-disabled-tag">已开赛</text>
             </template>
           </view>
         </view>
       </view>

      <view v-else class="empty-card">
         <text class="empty-title">{{ isTeam ? '暂无队伍' : '暂无选手' }}</text>
         <text class="empty-desc">{{ isTeam ? '当前赛事还没有可查看的队伍数据。' : '当前赛事还没有录入参赛选手。' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
 import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop
    } else {
      const statusBarHeight = Number(info?.statusBarHeight)
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
        safeTopPx = statusBarHeight
      }
    }
  } catch (_) {
    // noop
  }

  let extraTopPx = 0
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2)
    try {
      if (typeof uni?.upx2px === "function") {
        const px = Number(uni.upx2px(extraTopRpx))
        if (Number.isFinite(px) && px > 0) {
          extraTopPx = px
        }
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}

const pageStyle = buildBasePortraitPageStyle()

const tournamentId = ref('')
const tournamentName = ref('')
const teams = ref([])
 const sportType = ref(0)
 const participantType = ref(1)
 const tournamentStarted = ref(false)
 const isTeam = computed(() => participantType.value === 1 || sportType.value === 1)
 const isCreator = ref(false)
const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')

function goBack() {
  navigateBackOrHome()
}

function openTeam(team) {
  if (!team?.id) return
  uni.navigateTo({
     url: '/pkg-manage/team-members?tournamentId='
      + encodeURIComponent(tournamentId.value)
      + '&participantId=' + encodeURIComponent(team.id)
      + '&teamName=' + encodeURIComponent(team.name || ''),
  })
}

function openTeamEdit(team) {
  if (!team?.id) return
  uni.navigateTo({
     url: '/pkg-manage/team-edit?tournamentId='
      + encodeURIComponent(tournamentId.value)
      + '&participantId=' + encodeURIComponent(team.id)
      + '&sportType=' + encodeURIComponent(sportType.value ?? ''),
  })
}
 function promptRenamePlayer(player) {
   if (!player?.id) return
   uni.showModal({
     title: '修改选手姓名',
     editable: true,
     placeholderText: '请输入新的选手姓名',
     content: player.name || '',
     success: async (res) => {
       if (res.confirm) {
         const newName = (res.content || '').trim()
         if (!newName) {
           uni.showToast({ title: '选手姓名不能为空', icon: 'none' })
           return
         }
         if (newName === player.name) return
         try {
           await request('/api/v1/tournaments/' + tournamentId.value + '/teams/' + player.id, {
             method: 'PUT',
             data: { name: newName }
           })
           uni.showToast({ title: '修改成功', icon: 'success' })
           player.name = newName
         } catch (err) {
           uni.showToast({ title: err?.message || '修改失败', icon: 'none' })
         }
       }
     }
   })
 }
 
async function loadTeams() {
  if (!tournamentId.value) {
    loading.value = false
    isError.value = true
    errorText.value = '缺少赛事ID'
    return
  }

  loading.value = true
  isError.value = false
  try {
    const [detail, data] = await Promise.all([
      request('/api/v1/tournaments/' + tournamentId.value, { method: 'GET' }),
      request('/api/v1/tournaments/' + tournamentId.value + '/teams', { method: 'GET' }),
    ])
    tournamentName.value = detail?.name || ''
    teams.value = Array.isArray(data?.teams) ? data.teams : []
     sportType.value = data?.sportType ?? detail?.sportType ?? 0
     participantType.value = data?.participantType ?? detail?.participantType ?? 1
     tournamentStarted.value = Boolean(data?.started)
     isCreator.value = !!(data?.creator ?? detail?.creator)
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载队伍失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  tournamentId.value = options?.id || ''
  loadTeams()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 40rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.18), transparent 34%),
    linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.header {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 24rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
}

.title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
}

.state-layer {
  min-height: calc(100vh - 140rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
}

.state-text {
  color: rgba(255, 255, 255, 0.76);
  font-size: 28rpx;
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}

.summary-card,
.team-card,
.empty-card {
  padding: 22rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.summary-title,
.team-name,
.empty-title {
  color: #ffffff;
  font-weight: 700;
}

.summary-title {
  display: block;
  font-size: 32rpx;
}

.summary-meta,
.team-desc,
.empty-desc {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  line-height: 1.6;
}

.team-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.team-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.team-actions {
  display: flex;
  align-items: center;
  gap: 22rpx;
  flex-shrink: 0;
}

.team-action {
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 179, 71, 0.55);
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.team-main {
  flex: 1;
  min-width: 0;
}

.team-name {
  display: block;
  font-size: 30rpx;
}

.team-arrow {
  color: #ffb347;
  font-size: 24rpx;
  flex-shrink: 0;
}

.empty-title {
  display: block;
  font-size: 30rpx;
}
 .team-disabled-tag {
   font-size: 22rpx;
   color: #7c8a99;
   padding: 6rpx 14rpx;
   background: rgba(255, 255, 255, 0.05);
   border-radius: 8rpx;
 }
</style>
