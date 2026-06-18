<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
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

      <view class="actions">
        <button class="secondary-btn" @click="toggleFavorite">{{ detail.favorite ? '取消收藏' : '收藏比赛' }}</button>
        <button class="primary-btn" @click="viewTeams">查看队伍</button>
      </view>

      <button class="judge-btn" v-if="detail.canOperateMatches" @click="goJudge">赛程表</button>

      <!-- 裁判验证入口（非创建者且非裁判时显示） -->
      <button class="referee-btn" v-if="!detail.creator && !detail.refereeGranted" @click="showRefereeAuth = true">裁判验证</button>

      <!-- 已通过验证的裁判提示 -->
      <view class="referee-badge" v-if="detail.refereeGranted && !detail.creator">
        <text>已通过裁判验证，可操作比赛</text>
      </view>
    </view>

    <!-- 裁判密码弹窗 -->
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
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { requireProfile } from '@/store/auth'
import { useActionLock } from '@/utils/interaction-guard'
import { request } from '@/utils/request'

const tournamentId = ref('')
const detail = ref(null)
const showRefereeAuth = ref(false)
const refereePassword = ref('')
const authLoading = ref(false)
const { begin: beginPageAction, run: runPageAction } = useActionLock(500)

const isVolleyball = computed(() => Number(detail.value?.sportType || 0) === 1)
const sportText = computed(() => (isVolleyball.value ? '排球' : '羽毛球'))

const typeText = computed(() => {
  if (Number(detail.value?.tournamentType || 0) === 1) {
    return `小组+淘汰 / ${detail.value?.knockoutSlots || 8}强 / 每组出线${detail.value?.qualifiersPerGroup || 2}${isVolleyball.value ? '队' : '人'}`
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
  const url = Number(detail.value.tournamentType || 0) === 1
    ? '/pages/tournament/groups?id=' + detail.value.id
    : '/pages/tournament/bracket?id=' + detail.value.id
  uni.navigateTo({ url })
}

function viewTeams() {
  if (!beginPageAction()) return
  if (!isVolleyball.value) {
    uni.showToast({
      title: '羽毛球赛事暂不支持查看队伍',
      icon: 'none',
    })
    return
  }
  if (!detail.value?.id) return
  uni.navigateTo({
    url: '/pages/tournament/teams?id=' + detail.value.id,
  })
}

async function toggleFavorite() {
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
  if (!refereePassword.value.trim()) {
    uni.showToast({ title: '请输入裁判密码', icon: 'none' })
    return
  }
  authLoading.value = true
  try {
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

async function goJudge() {
  await runPageAction(async () => {
    try {
      await requireProfile()
      navigateToTournament()
    } catch (_) {
      // noop
    }
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
  padding: 28rpx 24rpx 40rpx;
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

.referee-btn::after {
  border: none;
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

/* 裁判验证弹窗 */
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
