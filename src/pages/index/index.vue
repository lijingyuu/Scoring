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
      <view class="create-hint">
        <text class="create-hint-label">网页端创建比赛：</text>
        <text class="create-hint-link" user-select="true">www.eunomia.cc</text>
      </view>
    </view>

    <view class="results-panel">
      <view v-if="showWatermark" class="watermark-visual">
        <image
          class="results-watermark"
          src="/static/NJUschoolbadge.png"
          mode="aspectFit" />
      </view>

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
        <view class="empty no-result-hint" v-if="!tournaments.length">
          <view v-if="showWatermark" class="empty-visual">
            <text class="empty-text no-result-text">没有找到相关比赛</text>
          </view>
          <text v-else class="empty-text no-result-text">没有找到相关比赛</text>
        </view>
      </view>

      <view class="empty empty-hint" v-else>
        <view v-if="showWatermark" class="empty-visual">
          <text class="empty-text">输入关键词以查看相关比赛</text>
        </view>
        <text v-else class="empty-text">输入关键词以查看相关比赛</text>
      </view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShareAppMessage, onShareTimeline, onShow } from "@dcloudio/uni-app";
import TournamentListCard from "@/components/TournamentListCard.vue";
import ProfileGatePopup from "@/components/ProfileGatePopup.vue";
import { guardProfileBeforeAction, requireProfile } from "@/store/auth";
import { request } from "@/utils/request";
import { buildShareAppMessage, buildShareTimeline } from "@/utils/share";

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
const showWatermark = ref(false);
const hasKeyword = computed(() => !!keyword.value.trim());

onShareAppMessage(() =>
  buildShareAppMessage({
    title: "Eunomia 赛事管理与记分",
    path: "/pages/index/index",
  }),
);

onShareTimeline(() =>
  buildShareTimeline({
    title: "Eunomia 赛事管理与记分",
    path: "/pages/index/index",
  }),
);

function updateDeviceLayout() {
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    const width = Number(info?.windowWidth || info?.screenWidth || 0);
    const height = Number(info?.windowHeight || info?.screenHeight || 0);
    const shortSide = Math.min(width, height);
    const longSide = Math.max(width, height);
    const deviceType = String(info?.deviceType || "").toLowerCase();
    const platform = String(info?.platform || "").toLowerCase();
    const isDesktopLike = ["windows", "mac", "devtools", "ipad"].includes(
      platform,
    );
    const isExplicitNonPhone =
      (!!deviceType && deviceType !== "phone") || isDesktopLike;
    isPadLayout.value = shortSide >= 720;
    showWatermark.value =
      !isExplicitNonPhone &&
      shortSide > 0 &&
      shortSide <= 480 &&
      longSide <= 980;
  } catch (_) {
    isPadLayout.value = false;
    showWatermark.value = false;
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

async function goCreate() {
  if (!(await guardProfileBeforeAction('请先完善个人资料，再创建比赛'))) return;
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

.create-hint {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.58);
  text-align: center;
}

.create-hint-label {
  font-size: inherit;
}

.create-hint-link {
  font-size: inherit;
  color: #ffcf7a;
  text-decoration: underline;
}

.results-panel {
  position: relative;
  margin-top: 28rpx;
  min-height: 42vh;
  overflow: hidden;
}

.results-watermark {
  display: block;
  width: 230px;
  height: 230px;
  opacity: 0.5;
  pointer-events: none;
}

.watermark-visual {
  position: fixed;
  left: 50%;
  top: 68vh;
  width: 230px;
  height: 230px;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
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

.no-result-hint {
  min-height: 29vh;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-hint {
  min-height: 29vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.empty-visual {
  position: fixed;
  left: 50%;
  top: 68vh;
  width: 230px;
  height: 230px;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
  pointer-events: none;
}

.empty-text {
  color: #ffffff;
  font-size: 34rpx;
  line-height: 1.35;
  letter-spacing: 1rpx;
  text-align: center;
  padding: 0 24rpx;
}

.empty-visual .empty-text {
  position: absolute;
  left: 50%;
  top: 36%;
  width: 100vw;
  transform: translate(-50%, -50%);
  z-index: 2;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.32);
}

.no-result-text {
  color: #ffffff;
  font-size: 35rpx;
}

@media screen and (min-width: 700px) and (min-height: 700px) {
  .page {
    padding-left: 32px;
    padding-right: 32px;
    padding-bottom: 32px;
  }

  .hero {
    max-width: 640px;
    margin: 0 auto;
    padding: 22px 24px;
    border-radius: 18px;
  }

  .hero-title {
    font-size: 30px;
    line-height: 1.2;
  }

  .hero-desc {
    margin-top: 10px;
    font-size: 16px;
    line-height: 1.45;
  }

  .search-input {
    margin-top: 16px;
    height: 52px;
    padding: 0 18px;
    border-radius: 12px;
    font-size: 17px;
  }

  .search-btn,
  .create-btn {
    margin-top: 14px;
    height: 54px;
    line-height: 54px;
    border-radius: 12px;
    font-size: 18px;
  }

  .create-hint {
    margin-top: 10px;
    font-size: 15px;
  }

  .results-panel {
    max-width: 640px;
    margin: 22px auto 0;
  }

  .section-header {
    margin-bottom: 14px;
  }

  .section-title {
    font-size: 19px;
  }

  .section-action {
    font-size: 15px;
  }

  .empty-text {
    font-size: 24px;
  }

  .no-result-text {
    font-size: 25px;
  }
}
</style>
