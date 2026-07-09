<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">赛事详情</text>
    </view>

    <view class="detail-card" v-if="detail">
      <text class="name">{{ detail.name }}</text>
      <text class="line" v-if="detail.location">{{ detail.location }}</text>
      <text class="line">运动类型：{{ sportText }}</text>
      <text class="line">{{ typeText }}</text>
      <text class="line">{{ ruleText }}</text>
      <text class="line">收藏数：{{ detail.favoriteCount || 0 }}</text>
      <text class="line">创建时间：{{ detail.createTime || '-' }}</text>
      <view class="archive-badge" v-if="isArchived">已归档，只读查看</view>

      <view class="actions" v-if="!isArchived || isTeamTournament">
        <button class="secondary-btn" v-if="!isArchived" @click="toggleFavorite">{{ detail.favorite ? '取消收藏' : '收藏比赛' }}</button>
        <button class="primary-btn" v-if="isTeamTournament" @click="viewTeams">查看队伍</button>
      </view>

      <button class="judge-btn" @click="goJudge">赛程表</button>

      <button class="referee-btn" v-if="!isArchived && !detail.creator && !detail.refereeGranted" @click="showRefereeAuth = true">裁判验证</button>

      <view class="referee-badge" v-if="!isArchived && detail.refereeGranted && !detail.creator">
        <text>已通过裁判验证，可操作比赛</text>
      </view>

      <button class="archive-action" v-if="canArchive" @click="archiveTournament">归档比赛</button>
    </view>

    <view class="referee-mask" v-if="showRefereeAuth" @click="showRefereeAuth = false">
      <view class="referee-panel" @click.stop>
        <text class="referee-panel-title">裁判验证</text>
        <text class="referee-panel-desc">请输入本赛事的裁判密码</text>
        <input class="input referee-input" v-model="refereePassword" type="number" maxlength="6" placeholder="6位数字密码" />
        <view class="referee-panel-btns">
          <button class="secondary-btn" @click="showRefereeAuth = false">取消</button>
          <button class="primary-btn" :loading="authLoading" @click="doRefereeAuth">验证</button>
        </view>
      </view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ensureAuth, requireProfile } from '@/store/auth'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { useActionLock } from '@/utils/interaction-guard'
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
const detail = ref(null)
const showRefereeAuth = ref(false)
const refereePassword = ref('')
const authLoading = ref(false)
const { begin: beginPageAction, run: runPageAction } = useActionLock(500)

const isVolleyball = computed(() => Number(detail.value?.sportType || 0) === 1)
const isTeamTournament = computed(() => Number(detail.value?.participantType || 0) === 1)
const isArchived = computed(() => detail.value?.archived === true)
const canArchive = computed(() => detail.value?.creator === true && Number(detail.value?.status || 0) === 2 && !isArchived.value)
const sportText = computed(() => (isVolleyball.value ? '排球' : '羽毛球'))

const typeText = computed(() => {
  const tournamentType = Number(detail.value?.tournamentType || 0)
  if (tournamentType === 1) {
    return `小组+淘汰 / ${detail.value?.knockoutSlots || 8}强 / 每组出线${detail.value?.qualifiersPerGroup || 2}${isTeamTournament.value ? '队' : '人'}`
  }
  if (tournamentType === 2) {
    return `循环赛 / ${Number(detail.value?.roundRobinRounds || 1) === 2 ? '双循环' : '单循环'}`
  }
  return '淘汰赛'
})

const ruleText = computed(() => {
  const bestOf = Number(detail.value?.bestOf || 3)
  if (isVolleyball.value) {
    return `${bestOf === 5 ? '五局三胜' : '三局两胜'} / 常规局25分 / 末局15分 / 领先2分`
  }
  const matchText = bestOf === 5 ? '五局三胜' : bestOf === 1 ? '一局定胜负' : '三局两胜'
  const deuceText = detail.value?.enableDeuce ? `${detail.value?.capPoint || 30}分封顶` : '无追分'
  return `${matchText} / ${detail.value?.pointsToWin || 21}分 / ${deuceText}`
})

async function fetchDetail() {
  if (!tournamentId.value) return
  detail.value = await request('/api/v1/tournaments/' + tournamentId.value, { method: 'GET' })
}

function goBack() {
  if (!beginPageAction()) return
  uni.navigateBack()
}

function navigateToTournament() {
  if (!detail.value?.id) return
  const url = Number(detail.value.tournamentType || 0) === 0
    ? '/pages/tournament/bracket?id=' + detail.value.id
    : '/pages/tournament/groups?id=' + detail.value.id
  uni.navigateTo({ url })
}

function viewTeams() {
  if (!beginPageAction()) return
  if (!isTeamTournament.value) {
    uni.showToast({ title: '个人赛不支持查看队伍', icon: 'none' })
    return
  }
  if (!detail.value?.id) return
  uni.navigateTo({
    url: '/pages/tournament/teams?id=' + detail.value.id,
  })
}

async function toggleFavorite() {
  if (isArchived.value) return
  await runPageAction(async () => {
    try {
      await requireProfile()
      if (detail.value?.favorite) {
        await request('/api/v1/tournaments/' + tournamentId.value + '/favorite', { method: 'DELETE' })
      } else {
        await request('/api/v1/tournaments/' + tournamentId.value + '/favorite', { method: 'POST' })
      }
      await fetchDetail()
    } catch (_) {
      // noop
    }
  })
}

async function doRefereeAuth() {
  if (isArchived.value) return
  if (!refereePassword.value.trim()) {
    uni.showToast({ title: '请输入裁判密码', icon: 'none' })
    return
  }
  authLoading.value = true
  try {
    await ensureAuth()
    await request('/api/v1/tournaments/' + tournamentId.value + '/referee-auth', {
      method: 'POST',
      data: { password: refereePassword.value.trim() },
    })
    showRefereeAuth.value = false
    refereePassword.value = ''
    uni.showToast({ title: '验证成功', icon: 'success' })
    await fetchDetail()
  } catch (error) {
    uni.showToast({ title: error?.message || '验证失败', icon: 'none' })
  } finally {
    authLoading.value = false
  }
}

async function archiveTournament() {
  if (!canArchive.value) return
  uni.showModal({
    title: '归档比赛',
    content: '归档后，这场比赛将从大厅、我的比赛和收藏列表隐藏，只能在已归档比赛中查看。',
    confirmText: '归档',
    success: async (res) => {
      if (!res.confirm) return
      await runPageAction(async () => {
        try {
          await request('/api/v1/tournaments/' + tournamentId.value + '/archive', { method: 'PUT' })
          uni.showToast({ title: '已归档', icon: 'success' })
          await fetchDetail()
        } catch (_) {
          // request handles toast
        }
      })
    },
  })
}

async function goJudge() {
  await runPageAction(async () => {
    navigateToTournament()
  })
}

onLoad((options) => {
  tournamentId.value = options?.id || ''
})

onShow(() => {
  fetchDetail()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 40rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
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

.detail-card {
  padding: 28rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.06);
}

.name {
  display: block;
  color: #ffffff;
  font-size: 38rpx;
  font-weight: 800;
}

.line {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.65);
  font-size: 26rpx;
  line-height: 1.6;
}

.archive-badge {
  display: inline-flex;
  margin-top: 18rpx;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}

.actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.secondary-btn,
.primary-btn,
.judge-btn {
  border: none;
  border-radius: 18rpx;
}

.secondary-btn::after,
.primary-btn::after,
.judge-btn::after {
  border: none;
}

.secondary-btn,
.primary-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn,
.judge-btn {
  background: #ff8c00;
  color: #13202d;
  font-weight: 700;
}

.judge-btn {
  margin-top: 20rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}

.referee-btn {
  margin-top: 20rpx;
  height: 84rpx;
  line-height: 84rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.5);
  border-radius: 18rpx;
  background: rgba(255, 140, 0, 0.1);
  color: #ffb347;
  font-size: 28rpx;
}

.referee-btn::after,
.archive-action::after {
  border: none;
}

.archive-action {
  margin-top: 30rpx;
  width: 220rpx;
  height: 60rpx;
  line-height: 60rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.16);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.58);
  font-size: 24rpx;
}

.referee-badge {
  margin-top: 20rpx;
  padding: 18rpx 22rpx;
  border-radius: 16rpx;
  background: rgba(0, 200, 100, 0.12);
  border: 1rpx solid rgba(0, 200, 100, 0.3);
}

.referee-badge text {
  color: #4cd964;
  font-size: 26rpx;
}

.referee-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  z-index: 30;
}

.referee-panel {
  width: 560rpx;
  padding: 36rpx;
  border-radius: 24rpx;
  background: #23384d;
  border: 1rpx solid rgba(255, 140, 0, 0.25);
}

.referee-panel-title {
  display: block;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
}

.referee-panel-desc {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.6);
  font-size: 24rpx;
  text-align: center;
}

.referee-input {
  margin-top: 24rpx;
  height: 84rpx;
  text-align: center;
  font-size: 36rpx;
  letter-spacing: 12rpx;
}

.referee-panel-btns {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.referee-panel-btns .secondary-btn,
.referee-panel-btns .primary-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
}
</style>
