<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">选择运动</text>
    </view>

    <view class="intro-card">
      <text class="intro-title">先选运动，再进入创建流程</text>
      <text class="intro-desc">羽毛球继续沿用现在的创建方式，排球使用球队建队流程。</text>
    </view>

    <view class="sport-list">
      <view class="sport-card" @click="openBadminton">
        <text class="sport-tag">羽毛球</text>
        <text class="sport-name">羽毛球比赛</text>
        <text class="sport-desc">沿用现有选手录入、赛制和记分模式。</text>
      </view>

      <view class="sport-card volleyball" @click="openVolleyball">
        <text class="sport-tag">排球</text>
        <text class="sport-name">排球比赛</text>
        <text class="sport-desc">按球队创建，录入 12 人名单、号码和队长。</text>
      </view>
    </view>
  </view>
</template>

<script setup>
function goBack() {
  uni.navigateBack()
}

function openBadminton() {
  uni.navigateTo({ url: '/pages/create/index' })
}

function openVolleyball() {
  uni.navigateTo({ url: '/pages/create/volleyball' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 40rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top right, rgba(255, 140, 0, 0.18), transparent 36%),
    linear-gradient(180deg, #142130 0%, #0f1822 100%);
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

.intro-card,
.sport-card {
  border-radius: 26rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.18);
  background: rgba(255, 255, 255, 0.06);
}

.intro-card {
  padding: 28rpx;
}

.intro-title {
  display: block;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
}

.intro-desc {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.65);
  font-size: 24rpx;
  line-height: 1.6;
}

.sport-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 22rpx;
}

.sport-card {
  padding: 28rpx;
}

.sport-card.volleyball {
  border-color: rgba(255, 140, 0, 0.28);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.05));
}

.sport-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 22rpx;
  font-weight: 700;
}

.sport-name {
  display: block;
  margin-top: 18rpx;
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 800;
}

.sport-desc {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.66);
  font-size: 24rpx;
  line-height: 1.6;
}
</style>
