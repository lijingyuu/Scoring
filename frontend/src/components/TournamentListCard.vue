<template>
  <view class="card" @click="$emit('open', item)">
    <view class="card-top">
      <view class="card-main">
        <text class="sport-tag" :class="{ volleyball: isVolleyball }">{{ sportText }}</text>
        <text class="card-name">{{ item.name }}</text>
        <text class="card-location" v-if="item.location">{{ item.location }}</text>
      </view>
      <view class="status-badge" :class="'status-' + (item.status ?? 0)">{{ statusText }}</view>
    </view>

    <view class="card-info">{{ typeText }} / {{ ruleText }}</view>
    <view class="card-bottom" v-if="showFavorite || archiveActionText">
      <text class="card-favorite" v-if="showFavorite">⭐ {{ item.favoriteCount || 0 }}</text>
      <view class="card-actions">
        <button class="favorite-btn" v-if="showFavorite" @click.stop="$emit('toggle-favorite', item)">
          {{ item.favorite ? '已收藏' : '收藏' }}
        </button>
        <button class="archive-btn" v-if="archiveActionText" @click.stop="$emit('archive-action', item)">
          {{ archiveActionText }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: {
    type: Object,
    required: true,
  },
  showFavorite: {
    type: Boolean,
    default: true,
  },
  archiveActionText: {
    type: String,
    default: '',
  },
})

defineEmits(['open', 'toggle-favorite', 'archive-action'])

const statusText = computed(() => {
  const map = {
    0: '未开始',
    1: '进行中',
    2: '已结束',
  }
  return map[props.item?.status ?? 0] || '未开始'
})

const isVolleyball = computed(() => Number(props.item?.sportType || 0) === 1)

const sportText = computed(() => isVolleyball.value ? '排球' : '羽毛球')

const typeText = computed(() => {
  const tournamentType = Number(props.item?.tournamentType || 0)
  if (tournamentType === 1) {
    return `小组+淘汰 / ${props.item?.knockoutSlots || 8}强`
  }
  if (tournamentType === 2) {
    return `循环赛 / ${Number(props.item?.roundRobinRounds || 1) === 2 ? '双循环' : '单循环'}`
  }
  return '淘汰赛'
})

const ruleText = computed(() => {
  if (isVolleyball.value) {
    const bestOf = Number(props.item?.bestOf || 3)
    return `${bestOf === 5 ? '五局三胜' : '三局两胜'} / 标准排球`
  }
  const bestOf = Number(props.item?.bestOf || 3)
  const matchText = bestOf === 5 ? '五局三胜' : bestOf === 1 ? '一局定胜负' : '三局两胜'
  return `${matchText} / ${props.item?.pointsToWin || 21}分`
})
</script>

<style scoped>
.card {
  padding: 24rpx;
  border-radius: 22rpx;
  background: linear-gradient(180deg, rgba(31, 50, 68, 0.6), rgba(25, 41, 58, 0.6));
  border: 1rpx solid rgba(110, 132, 154, 0.28);
}

.card-top {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-name {
  display: block;
  margin-top: 10rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #ffffff;
}

.sport-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 22rpx;
  font-weight: 700;
}

.sport-tag.volleyball {
  background: rgba(82, 196, 26, 0.16);
  color: #95de64;
}

.card-location {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.58);
}

.status-badge {
  height: 46rpx;
  line-height: 46rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  flex-shrink: 0;
}

.status-0 {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.76);
}

.status-1 {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
}

.status-2 {
  background: rgba(76, 217, 100, 0.14);
  color: #7ee787;
}

.card-info {
  margin-top: 20rpx;
  color: rgba(255, 255, 255, 0.65);
  font-size: 24rpx;
}

.card-bottom {
  margin-top: 22rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-actions {
  margin-left: auto;
  display: flex;
  gap: 12rpx;
}

.card-favorite {
  color: #ffd28a;
  font-size: 24rpx;
}

.favorite-btn,
.archive-btn {
  min-width: 138rpx;
  height: 58rpx;
  line-height: 58rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #ff8c00;
  color: #152231;
  font-size: 24rpx;
  font-weight: 700;
  border: none;
}

.favorite-btn::after,
.archive-btn::after {
  border: none;
}

.archive-btn {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
}
</style>
