<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <view class="header-main">
        <text class="title">自定义排名规则</text>
        <text class="subtitle">{{ modeTitle }}</text>
      </view>
    </view>

    <view class="selected-panel">
      <view class="panel-head">
        <text class="panel-title">优先级顺序</text>
        <text class="clear-btn" @click="clearSelected">清空</text>
      </view>
      <view class="slot-row" v-if="selected.length">
        <view
          class="slot"
          v-for="criterion in selected"
          :key="criterion"
          @click="toggleCriterion(criterion)">
          <text class="slot-label">{{ getCriterionLabel(criterion) }}</text>
        </view>
      </view>
      <view class="empty-slots" v-else>
        <text class="empty-text">还没有选择指标</text>
      </view>
    </view>

    <scroll-view class="option-scroll" scroll-y>
      <view
        class="option-group"
        v-for="group in criterionGroups"
        :key="group.title">
        <view class="group-head">
          <text class="group-title">{{ group.title }}</text>
        </view>
        <view class="option-list">
          <view
            class="option-item"
            v-for="item in group.items"
            :key="item.value"
            :class="{ selected: isSelected(item.value) }"
            @click="toggleCriterion(item.value)">
            <text class="option-name">{{ item.label }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="footer-bar">
      <button class="ghost-btn" @click="goBack">取消</button>
      <button class="primary-btn" @click="saveCustomRanking">保存规则</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import {
  RANKING_CUSTOM_INPUT_PREFIX,
  RANKING_CUSTOM_RESULT_PREFIX,
  STANDARD_RANKING_MODE,
  TEAM_RANKING_MODE,
  defaultBaseTemplateForRankingMode,
  getCriterionLabel,
  getRankingCriterionGroups,
  rankingStorageKey,
} from "./ranking-options";

function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0;
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    const safeInsetTop = Number(info?.safeAreaInsets?.top);
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop;
    } else {
      const statusBarHeight = Number(info?.statusBarHeight);
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
        safeTopPx = statusBarHeight;
      }
    }
  } catch (_) {
    // noop
  }
  return {
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopRpx}px`,
  };
}

const pageStyle = buildBasePortraitPageStyle();
const storageKey = ref("default");
const mode = ref(STANDARD_RANKING_MODE);
const baseTemplate = ref("");
const selected = ref([]);

const criterionGroups = computed(() => getRankingCriterionGroups(mode.value));
const modeTitle = computed(() =>
  mode.value === TEAM_RANKING_MODE
    ? "羽毛球团体赛：胜场、场内大分、场内局、局内小分"
    : "羽毛球个人赛 / 排球：胜场、局、分、胜负关系",
);

function goBack() {
  uni.navigateBack();
}

function isSelected(value) {
  return selected.value.includes(value);
}

function toggleCriterion(value) {
  if (!value) return;
  if (isSelected(value)) {
    selected.value = selected.value.filter((item) => item !== value);
    return;
  }
  selected.value = [...selected.value, value];
}

function clearSelected() {
  selected.value = [];
}

function saveCustomRanking() {
  if (!selected.value.length) {
    uni.showToast({ title: "请至少选择 1 个指标", icon: "none" });
    return;
  }
  uni.setStorageSync(
    rankingStorageKey(RANKING_CUSTOM_RESULT_PREFIX, storageKey.value),
    {
      mode: mode.value,
      baseTemplate:
        baseTemplate.value || defaultBaseTemplateForRankingMode(mode.value),
      priorities: selected.value,
    },
  );
  uni.navigateBack();
}

onLoad((options) => {
  storageKey.value = options?.key || "default";
  mode.value =
    options?.mode === TEAM_RANKING_MODE
      ? TEAM_RANKING_MODE
      : STANDARD_RANKING_MODE;

  const input =
    uni.getStorageSync(
      rankingStorageKey(RANKING_CUSTOM_INPUT_PREFIX, storageKey.value),
    ) || {};
  if (
    input.mode === TEAM_RANKING_MODE ||
    input.mode === STANDARD_RANKING_MODE
  ) {
    mode.value = input.mode;
  }
  baseTemplate.value =
    input.baseTemplate ||
    options?.baseTemplate ||
    defaultBaseTemplateForRankingMode(mode.value);
  const systemFallbackCriterion = input.systemFallbackCriterion || null;
  selected.value = Array.isArray(input.priorities)
    ? input.priorities
      .filter(Boolean)
      .filter((criterion) => criterion !== systemFallbackCriterion)
    : [];
});
</script>

<style scoped>
.page {
  height: 100vh;
  min-height: 100vh;
  padding: 0 24rpx 128rpx;
  box-sizing: border-box;
  background: #13202d;
  color: #ffffff;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
  flex-shrink: 0;
}

.header-main {
  min-width: 0;
}

.title,
.subtitle,
.panel-title,
.group-title,
.option-name,
.empty-text {
  display: block;
}

.title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 800;
}

.subtitle {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
}

.selected-panel {
  padding: 20rpx;
  border-radius: 18rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.28);
  background: rgba(255, 255, 255, 0.06);
}

.panel-head,
.group-head,
.option-item,
.slot {
  display: flex;
  align-items: center;
}

.panel-head {
  justify-content: space-between;
  gap: 16rpx;
}

.panel-title {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
}

.clear-btn {
  color: #ffb347;
  font-size: 23rpx;
}

.slot-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 16rpx;
}

.slot {
  min-height: 48rpx;
  padding: 0 18rpx;
  border-radius: 12rpx;
  background: rgba(255, 140, 0, 0.12);
  border: 1rpx solid rgba(255, 140, 0, 0.42);
}

.slot-label {
  color: #ffb347;
  font-size: 26rpx;
  font-weight: 500;
}

.empty-slots {
  margin-top: 16rpx;
  min-height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
  border: 1rpx dashed rgba(255, 255, 255, 0.2);
}

.empty-text {
  color: rgba(255, 255, 255, 0.48);
  font-size: 26rpx;
}

.option-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 20rpx;
}

.option-group {
  margin-bottom: 18rpx;
  padding: 18rpx 18rpx 16rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.05);
}

.group-head {
  margin-bottom: 12rpx;
}

.group-title {
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 600;
}

.option-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.option-item {
  min-height: 50rpx;
  padding: 0 18rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.option-item.selected {
  border-color: rgba(255, 140, 0, 0.58);
  background: rgba(255, 140, 0, 0.12);
}

.option-name {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
}

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 18rpx;
  border: none;
  font-size: 28rpx;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn {
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-weight: 800;
}

.ghost-btn::after,
.primary-btn::after {
  border: none;
}
</style>
