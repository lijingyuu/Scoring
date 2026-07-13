<template>
  <view class="page" :style="pageStyle">
    <view class="hero">
      <text class="hero-title">赛事大厅</text>
      <text class="hero-desc"
        >搜索比赛并快速发起新比赛。为保护隐私，首页不再默认展示赛事列表。</text
      >
      <input
        class="search-input"
        v-model="keyword"
        placeholder="输入比赛名称或地点关键词"
        confirm-type="search"
        @confirm="fetchTournaments" />
      <button class="search-btn" @click="handleRefresh">搜索比赛</button>
      <button class="create-btn" @click="goCreate">创建比赛</button>
    </view>

    <view class="results-panel">
      <image
        v-if="!isPadLayout"
        class="results-watermark"
        src="/static/NJUschoolbadge.png"
        mode="aspectFit" />

      <view class="section-header">
        <text class="section-title" v-if="hasKeyword">搜索结果</text>
        <text class="section-action" v-if="hasKeyword" @click="handleRefresh"
          >刷新</text
        >
      </view>

      <view class="list" v-if="hasKeyword">
        <TournamentListCard
          v-for="item in tournaments"
          :key="item.id"
          :item="item"
          @open="openDetail"
          @toggle-favorite="toggleFavorite" />
        <view class="empty" v-if="!tournaments.length">没有找到相关比赛</view>
      </view>

      <view class="empty empty-hint" v-else>输入关键词以查看相关比赛</view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import TournamentListCard from "@/components/TournamentListCard.vue";
import ProfileGatePopup from "@/components/ProfileGatePopup.vue";
import { requireProfile } from "@/store/auth";
import { request } from "@/utils/request";

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 28) {
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

  let extraTopPx = 0;
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2);
    try {
      if (typeof uni?.upx2px === "function") {
        const px = Number(uni.upx2px(extraTopRpx));
        if (Number.isFinite(px) && px > 0) {
          extraTopPx = px;
        }
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopPx}px`,
  };
}

const pageStyle = buildBasePortraitPageStyle(28);

const keyword = ref("");
const tournaments = ref([]);
const isPadLayout = ref(false);
const hasKeyword = computed(() => !!keyword.value.trim());

function updateDeviceLayout() {
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    const width = Number(info?.windowWidth || info?.screenWidth || 0);
    const height = Number(info?.windowHeight || info?.screenHeight || 0);
    isPadLayout.value = Math.min(width, height) >= 720;
  } catch (_) {
    isPadLayout.value = false;
  }
}

updateDeviceLayout();

async function fetchTournaments() {
  const query = keyword.value.trim();
  if (!query) {
    tournaments.value = [];
    return;
  }
  tournaments.value = await request(
    "/api/v1/tournaments?keyword=" + encodeURIComponent(query),
    { method: "GET" },
  );
}

function handleRefresh() {
  if (!hasKeyword.value) {
    uni.showToast({ title: "请输入关键词", icon: "none" });
    return;
  }
  fetchTournaments();
}

function openDetail(item) {
  uni.navigateTo({ url: "/pages/tournament/detail?id=" + item.id });
}

function goCreate() {
  uni.navigateTo({ url: "/pages/create/sport" });
}

async function toggleFavorite(item) {
  try {
    await requireProfile();
    if (item.favorite) {
      await request("/api/v1/tournaments/" + item.id + "/favorite", {
        method: "DELETE",
      });
    } else {
      await request("/api/v1/tournaments/" + item.id + "/favorite", {
        method: "POST",
      });
    }
    await fetchTournaments();
  } catch (_) {
    // noop
  }
}

onShow(() => {
  updateDeviceLayout();
  if (hasKeyword.value) {
    fetchTournaments();
  } else {
    tournaments.value = [];
  }
});
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 40rpx;
  background: linear-gradient(180deg, #142130 0%, #101a25 100%);
  box-sizing: border-box;
}

.hero {
  padding: 30rpx 28rpx;
  border-radius: 28rpx;
  background: linear-gradient(
    180deg,
    rgba(31, 50, 68, 0.92),
    rgba(25, 41, 58, 0.92)
  );
  border: 1rpx solid rgba(110, 132, 154, 0.28);
}

.hero-title {
  display: block;
  font-size: 42rpx;
  font-weight: 800;
  color: #ffffff;
}

.hero-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.6;
}

.search-input {
  margin-top: 24rpx;
  height: 84rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 28rpx;
}

.search-btn {
  margin-top: 24rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 18rpx;
  border: none;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 30rpx;
  font-weight: 800;
}

.search-btn::after {
  border: none;
}

.create-btn {
  margin-top: 24rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 18rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.82);
  background: transparent;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 800;
}

.create-btn::after {
  border: none;
}

.results-panel {
  position: relative;
  margin-top: 28rpx;
  min-height: 42vh;
  overflow: hidden;
}

.results-watermark {
  position: fixed;
  left: 50%;
  top: 68vh;
  width: 460rpx;
  height: 460rpx;
  opacity: 0.7;
  transform: translate(-50%, -50%);
  z-index: 0;
  pointer-events: none;
}

.section-header {
  margin-bottom: 18rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
}

.section-title {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
}

.section-action {
  color: #ffb347;
  font-size: 24rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  position: relative;
  z-index: 1;
}

.empty {
  padding: 80rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 26rpx;
  position: relative;
  z-index: 1;
}

.empty-hint {
  min-height: 29vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 34rpx;
  letter-spacing: 1rpx;
}
</style>
