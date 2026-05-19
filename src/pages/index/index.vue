<template>
  <view class="page">
    <!-- 新建赛事表单 -->
    <view class="form-section">
      <view class="form-title">新建赛事</view>
      <input class="input" v-model="form.name" placeholder="赛事名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />
      <textarea
        class="textarea"
        v-model="form.players"
        placeholder="请将参赛选手姓名直接粘贴至此，支持空格或换行分隔（至少2人）"
      />
      <button class="submit-btn" @click="createTournament">一键生成赛事</button>
    </view>

    <!-- 赛事列表 -->
    <view class="list-section">
      <view class="list-title">赛事大厅 / 全部比赛</view>

      <view
        class="card"
        v-for="item in tournamentList"
        :key="item.id"
        @click="goToBracket(item.id)"
      >
        <view class="card-header">
          <text class="card-name">{{ item.name }}</text>
          <text class="card-status" :class="'status-' + item.status">{{ statusLabels[item.status] }}</text>
        </view>
        <view class="card-body">
          <text class="card-info" v-if="item.location">{{ item.location }}</text>
          <text class="card-time">{{ item.createTime }}</text>
        </view>
      </view>

      <view class="empty" v-if="!tournamentList.length">暂无赛事，快去创建吧</view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const statusLabels = { 0: '未开始', 1: '进行中', 2: '已结束' }

const form = reactive({
  name: '',
  location: '',
  players: '',
})
const tournamentList = ref([])

async function fetchTournaments() {
  try {
    const res = await request('/api/v1/tournaments', { method: 'GET' })
    tournamentList.value = res || []
  } catch (_) {
    // request 内部已弹 toast
  }
}

async function createTournament() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入赛事名称', icon: 'none' })
    return
  }
  if (!form.players.trim()) {
    uni.showToast({ title: '请输入参赛选手', icon: 'none' })
    return
  }

  const playerNames = form.players
    .split(/[\n\r\s,，]+/)
    .map((s) => s.trim())
    .filter(Boolean)

  if (playerNames.length < 2) {
    uni.showToast({ title: '至少需要2名选手', icon: 'none' })
    return
  }

  try {
    await request('/api/v1/tournaments', {
      method: 'POST',
      data: {
        name: form.name.trim(),
        location: form.location.trim() || undefined,
        playerNames,
      },
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    form.name = ''
    form.location = ''
    form.players = ''
    fetchTournaments()
  } catch (_) {
    // request 内部已弹 toast
  }
}

function goToBracket(id) {
  uni.navigateTo({ url: '/pages/tournament/bracket?id=' + id })
}

onShow(() => {
  fetchTournaments()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #1a2a3a;
  color: #ffffff;
  padding: 32rpx 28rpx;
  box-sizing: border-box;
}

/* ─── 表单区域 ─── */
.form-section {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 140, 0, 0.3);
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 36rpx;
}

.form-title {
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.input {
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 20rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
  margin-bottom: 16rpx;
}

.input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.textarea {
  width: 100%;
  height: 300rpx;
  padding: 20rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.textarea::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #1a2a3a;
  font-size: 30rpx;
  font-weight: 700;
}

.submit-btn::after {
  border: none;
}

/* ─── 列表区域 ─── */
.list-section {
  padding-bottom: 40rpx;
}

.list-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
  color: rgba(255, 255, 255, 0.85);
}

.card {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16rpx;
  padding: 24rpx 22rpx;
  margin-bottom: 16rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.card-name {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-status {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.status-0 {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.7);
}

.status-1 {
  background: rgba(255, 140, 0, 0.2);
  color: #ff8c00;
}

.status-2 {
  background: rgba(76, 217, 100, 0.15);
  color: #4cd964;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card-info,
.card-time {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.55);
}

.empty {
  text-align: center;
  color: rgba(255, 255, 255, 0.35);
  font-size: 26rpx;
  padding: 60rpx 0;
}
</style>
