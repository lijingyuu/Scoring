<template>
  <view class="scoreboard-page" :style="pageStyle">
    <LockMask
      v-if="lockGuard.maskMode.value"
      :title="lockGuard.maskTitle.value"
      :message="lockGuard.maskMessage.value"
      :score="lockGuard.maskScore.value"
      :retry-label="lockGuard.maskMode.value === 'denied' ? '重试获取' : '重新获取执裁权'"
      :retry-disabled="lockGuard.retryDisabled.value"
      :retrying="lockGuard.retrying.value"
      @retry="lockGuard.retry"
      @back="lockGuard.back" />
    <view class="state-layer" v-if="loading">
      <text class="state-text">{{ t.loading }}</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text error">{{ t.loadFailed }}</text>
      <button class="retry-btn" @click="fetchDetail">{{ t.retry }}</button>
    </view>

    <template v-else>
      <view class="top-left-actions">
        <button class="action-btn side-action-btn" @click="goBack">
          {{ t.back }}
        </button>
      </view>

      <view class="top-flow-row">
        <view class="top-center-actions">
          <button
            class="action-btn center-action-btn"
            @click="undo"
            :disabled="isReadOnly || !historyStack.length || scoreLocked">
            {{ t.undo }}
          </button>
          <button
            class="action-btn center-action-btn"
            @click="editLineup"
            :disabled="isReadOnly || scoreStarted || syncing || synced">
            {{ t.lineup }}
          </button>
          <button
            class="action-btn center-action-btn sync-action-btn"
            @click="syncResult"
            :disabled="
              isReadOnly || !matchEnded || syncing || synced || segmentSwitchPending
            ">
            {{ t.sync }}
          </button>
          <button class="action-btn icon-action-btn sound-action-btn" :class="{ muted: isScoreMuted }" @click="toggleScoreMuted">
            <view class="sound-icon" :class="{ muted: isScoreMuted }">
              <image class="sound-icon-image" src="/static/sound-icon.png" mode="aspectFit"></image>
              <view class="sound-icon-slash"></view>
            </view>
          </button>
        </view>

        <text class="top-score-anchor"
          >{{ displayScore("left") }} : {{ displayScore("right") }}</text
        >

        <view class="match-info">
          <text class="match-rule"
            >{{ t.base }} {{ baseScore }} / {{ t.target }}
            {{ targetScore }}</text
          >
          <text class="game-tag"
            >{{ t.segmentPrefix }}{{ currentSegmentNo
            }}{{ t.segmentSuffix }}</text
          >
        </view>
      </view>

      <view class="game-wins-row">
        <view class="team-summary left-label">
          <view class="team-label">{{ displayTeamName("left") }}</view>
          <view class="team-name">{{
            displayMemberNames("left") || t.unfilled
          }}</view>
        </view>
        <view class="team-summary right-label">
          <view class="team-label">{{ displayTeamName("right") }}</view>
          <view class="team-name">{{
            displayMemberNames("right") || t.unfilled
          }}</view>
        </view>
      </view>

      <view class="main-panels">
        <view class="score-side left-side">
          <view class="team-panel">
            <view
              class="score-box"
              :class="{ disabled: isReadOnly || scoreLocked }"
              @click="addScore('left')">
              <view class="score">{{ displayScore("left") }}</view>
            </view>
            <view
              class="serve-flag"
              :class="{ active: visualLeftSide === lastScoredSide }"
              >{{ t.lastPoint }}</view
            >
          </view>
        </view>

        <view class="score-side right-side">
          <view class="team-panel">
            <view
              class="score-box"
              :class="{ disabled: isReadOnly || scoreLocked }"
              @click="addScore('right')">
              <view class="score">{{ displayScore("right") }}</view>
            </view>
            <view
              class="serve-flag"
              :class="{ active: visualRightSide === lastScoredSide }"
              >{{ t.lastPoint }}</view
            >
          </view>
        </view>
      </view>

      <view v-if="initialSidePromptVisible" class="final-switch-overlay">
        <view class="final-switch-card initial-side-card">
          <text class="final-switch-title">{{ t.initialSideTitle }}</text>
          <view class="initial-side-teams">
            <text class="initial-side-team"
              >{{ t.currentLeft }}{{ teamName("left") }}</text
            >
            <text class="initial-side-team"
              >{{ t.currentRight }}{{ teamName("right") }}</text
            >
          </view>
          <text class="final-switch-tip initial-side-question">{{
            t.initialSideQuestion
          }}</text>
          <view class="final-switch-actions">
            <button
              class="final-switch-btn secondary"
              @click="confirmInitialSideSwap(false)">
              {{ t.initialSideCancel }}
            </button>
            <button
              class="final-switch-btn"
              @click="confirmInitialSideSwap(true)">
              {{ t.initialSideConfirm }}
            </button>
          </view>
        </view>
      </view>

      <view v-if="segmentSwitchPending" class="final-switch-overlay">
        <view class="final-switch-card">
          <text class="final-switch-title">{{ t.segmentEndedTitle }}</text>
          <text class="final-switch-tip"
            >{{ t.segment }} {{ currentSegmentNo }} {{ t.cutoff }}
            {{ currentSegmentTarget }}</text
          >
          <view class="final-switch-actions">
            <button
              class="final-switch-btn secondary"
              :disabled="isReadOnly || segmentSwitchClickLocked"
              @click="advanceSegment(false)">
              {{ t.keepSideNextSegment }}
            </button>
            <button
              class="final-switch-btn"
              :disabled="isReadOnly || segmentSwitchClickLocked"
              @click="advanceSegment(true)">
              {{ t.swapSideNextSegment }}
            </button>
          </view>
        </view>
      </view>

      <view v-if="matchEnded && !segmentSwitchPending" class="lock-mask">
        <view class="settlement-card">
          <text class="settlement-title">{{ t.ended }}</text>
          <view class="settlement-teams">
            <text
              class="settlement-team-name"
              :class="settlementTeamClass('left')"
              >{{ teamName("left") }}</text
            >
            <text class="settlement-team-sep">胜</text>
            <text
              class="settlement-team-name"
              :class="settlementTeamClass('right')"
              >{{ teamName("right") }}</text
            >
          </view>
          <text class="settlement-score"
            >{{ leftScore }} : {{ rightScore }}</text
          >
          <view class="settlement-actions">
            <button
              class="new-match-btn sync-btn"
              @click="syncResult"
              :disabled="isReadOnly || syncing || synced">
              {{ t.sync }}
            </button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, onUnmounted, ref } from "vue";
import { onLoad, onShow, onUnload } from "@dcloudio/uni-app";
import { guardProfileBeforeAction } from "@/store/auth";
import { navigateBackOrHome } from "@/utils/back-navigation";
import { request } from "@/utils/request";
import { useScoreAnnouncer } from "@/composables/useScoreAnnouncer";

import { requireMatchOperator } from "@/utils/match-guard";
import {
  acquireMatchLockWithRetry,
  clearMatchLockToken,
  createMatchLockToken,
  loadMatchLockToken,
  matchLockHeader,
  releaseMatchLock,
  saveMatchLockToken,
  startMatchLockHeartbeat,
} from "@/utils/match-lock";
import { createMatchEventPipeline } from "@/pages/scoreboard/match-events";
import {
  RELAY_ACTION_EVENT_MAP,
  RELAY_HISTORY_ACTIONS,
  buildRelayRecoveredState,
  decideRelayEntrySource,
} from "./relay-recovery";
import LockMask from "@/components/LockMask.vue";
import { useLockGuard } from "@/composables/use-lock-guard";

const t = {
  loading: "\u6b63\u5728\u52a0\u8f7d\u63a5\u529b\u8d5b...",
  loadFailed: "\u63a5\u529b\u8d5b\u52a0\u8f7d\u5931\u8d25",
  retry: "\u91cd\u65b0\u52a0\u8f7d",
  back: "\u8fd4\u56de",
  title: "\u4eba\u5458\u6d41\u8f6c\u8ffd\u5206\u8d5b",
  base: "\u57fa\u51c6\u5206",
  target: "\u76ee\u6807\u5206",
  segment: "\u7b2c",
  segmentPrefix: "\u7b2c",
  segmentSuffix: "\u8d5b\u6bb5",
  cutoff: "\u6bb5\u622a\u6b62",
  lineup: "\u987a\u5e8f\u540d\u5355",
  leftTeam: "\u5de6\u961f",
  rightTeam: "\u53f3\u961f",
  unknownTeam: "\u672a\u77e5\u961f\u4f0d",
  unfilled: "\u672a\u586b\u5199",
  lastPoint: "\u00b7\u53d1\u7403",
  pending:
    "\u5f53\u524d\u9636\u6bb5\u5df2\u7ed3\u675f\uff0c\u8bf7\u9009\u62e9\u662f\u5426\u4ea4\u6362\u573a\u5730\u5e76\u8fdb\u5165\u4e0b\u4e00\u8d5b\u6bb5",
  keepSideNextSegment: "\u4e0d\u4ea4\u6362\u573a\u5730",
  swapSideNextSegment: "\u4ea4\u6362\u573a\u5730",
  ended: "\u6bd4\u8d5b\u7ed3\u675f",
  historyEmpty:
    "\u9636\u6bb5\u7ed3\u675f\u6bd4\u5206\u4f1a\u663e\u793a\u5728\u8fd9\u91cc",
  undo: "\u64a4\u9500",
  sync: "\u540c\u6b65\u7ed3\u679c",
  missingMatchId: "\u7f3a\u5c11\u6bd4\u8d5bID",
  completeLineup: "\u8bf7\u5148\u5b8c\u6210\u987a\u5e8f\u540d\u5355",
  segmentEndedTitle: "\u9636\u6bb5\u7ed3\u675f",
  segmentEndedContentPrefix: "\u7b2c ",
  segmentEndedContentSuffix:
    " \u6bb5\u5df2\u7ed3\u675f\uff0c\u8bf7\u9009\u62e9\u662f\u5426\u4ea4\u6362\u573a\u5730\u5e76\u8fdb\u5165\u4e0b\u4e00\u8d5b\u6bb5\u3002",
  initialSideTitle: "\u786e\u8ba4\u573a\u5730",
  initialSideConfirm: "\u9700\u8981\u6362\u8fb9",
  initialSideCancel: "\u4e0d\u6362\u8fb9",
  currentLeft: "\u5de6\u4fa7\u961f\u4f0d\uff1a",
  currentRight: "\u53f3\u4fa7\u961f\u4f0d\uff1a",
  initialSideQuestion:
    "\u76ee\u524d\u53cc\u65b9\u662f\u5426\u9700\u8981\u4ea4\u6362\u573a\u5730\uff1f",
  noWinner: "\u672a\u5206\u51fa\u80dc\u8d1f",
  synced: "\u7ed3\u7b97\u6210\u529f",
};

function buildBaseLandscapePageStyle() {
  let safeTopPx = 0;
  try {
    const info =
      typeof uni.getWindowInfo === "function"
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
    const safeInsetTop = Number(info?.safeAreaInsets?.top);
    const statusBarHeight = Number(info?.statusBarHeight);
    safeTopPx =
      Number.isFinite(safeInsetTop) && safeInsetTop > 0
        ? safeInsetTop
        : Number.isFinite(statusBarHeight)
          ? statusBarHeight
          : 0;
  } catch (_) {
    // noop
  }
  return { boxSizing: "border-box", paddingTop: safeTopPx + "px" };
}

const pageStyle = buildBaseLandscapePageStyle();
const tournamentId = ref("");
const matchId = ref("");
const divisionId = ref("");
const detail = ref({ leftTeam: {}, rightTeam: {}, items: [] });
const loading = ref(true);
const isError = ref(false);
const loadedOnce = ref(false);
const leftScore = ref(0);
const rightScore = ref(0);
const historyStack = ref([]);
const segmentScores = ref([]);
const segmentSwitchPending = ref(false);
const segmentSwitchClickLocked = ref(false);
let segmentSwitchUnlockTimer = null;
const sidesSwapped = ref(false);
const lastScoredSide = ref("");
const syncing = ref(false);
const synced = ref(false);
const initialSidePromptShown = ref(false);
const initialSidePromptVisible = ref(false);
const sessionLockToken = ref("");
const sameSession = ref(false);
// 「事件上云」管道三水位（羽毛球 use-scoreboard-state 同款命名）
const matchEvents = ref([]);
const nextEventSeq = ref(1);
const lastSyncedEventSeq = ref(0);
const isReadOnly = ref(false);
const resumeNoticeShown = ref(false);
let stopHeartbeat = null;
const { isMuted: isScoreMuted, toggleMuted: toggleScoreMuted, announceScore, destroyScoreAnnouncer } = useScoreAnnouncer();

const items = computed(() =>
  Array.isArray(detail.value.items)
    ? [...detail.value.items].sort(
        (a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0),
      )
    : [],
);
const baseScore = computed(() => Number(detail.value.relayBaseScore || 10));
const targetScore = computed(() =>
  Number(detail.value.relayTargetScore || baseScore.value * items.value.length),
);
const matchEnded = computed(
  () =>
    Number(detail.value.matchStatus || 0) === 2 ||
    (targetScore.value > 0 &&
      (leftScore.value >= targetScore.value ||
        rightScore.value >= targetScore.value)),
);
const scoreLocked = computed(
  () =>
    matchEnded.value ||
    syncing.value ||
    synced.value ||
    segmentSwitchPending.value ||
    initialSidePromptVisible.value,
);
const scoreStarted = computed(
  () =>
    leftScore.value > 0 ||
    rightScore.value > 0 ||
    segmentScores.value.length > 0,
);

const currentSegmentIndex = computed(() => {
  if (!items.value.length) return 0;
  return Math.min(segmentScores.value.length, items.value.length - 1);
});
const currentSegmentNo = computed(() => currentSegmentIndex.value + 1);
const currentSegmentTarget = computed(() =>
  Math.min(baseScore.value * currentSegmentNo.value, targetScore.value),
);
const currentItem = computed(
  () => items.value[currentSegmentIndex.value] || {},
);
const visualLeftSide = computed(() => (sidesSwapped.value ? "right" : "left"));
const visualRightSide = computed(() => (sidesSwapped.value ? "left" : "right"));

function sideLabel(side) {
  return side === "left" ? t.leftTeam : t.rightTeam;
}

function originalSideOfVisual(visualSide) {
  return visualSide === "left" ? visualLeftSide.value : visualRightSide.value;
}

function teamName(side) {
  const team = side === "left" ? detail.value.leftTeam : detail.value.rightTeam;
  return team?.name || t.unknownTeam;
}

function displayTeamName(visualSide) {
  return teamName(originalSideOfVisual(visualSide));
}

function settlementTeamClass(side) {
  const leftWon = leftScore.value > rightScore.value;
  const rightWon = rightScore.value > leftScore.value;
  return {
    winner: (side === "left" && leftWon) || (side === "right" && rightWon),
  };
}

function scoreOf(side) {
  return side === "left" ? leftScore.value : rightScore.value;
}

function displayScore(visualSide) {
  return scoreOf(originalSideOfVisual(visualSide));
}

function membersOf(side) {
  return side === "left"
    ? currentItem.value.leftMembers
    : currentItem.value.rightMembers;
}

function memberNames(members) {
  return Array.isArray(members)
    ? members
        .map((member) => member?.name)
        .filter(Boolean)
        .join(" / ")
    : "";
}

function displayMemberNames(visualSide) {
  return memberNames(membersOf(originalSideOfVisual(visualSide)));
}

function storageKey() {
  return matchId.value
    ? "team_relay_scoreboard_state_" + matchId.value
    : "team_relay_scoreboard_state";
}

function buildScoreState() {
  return {
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    segmentScores: segmentScores.value.map((item) => ({ ...item })),
    segmentSwitchPending: segmentSwitchPending.value,
    sidesSwapped: sidesSwapped.value,
    lastScoredSide: lastScoredSide.value,
  };
}

/**
 * 动作前/后的全量比分快照 → 事件 runtime / prevRuntime（原始坐标）。
 * currentGameNo 承载段号（管道写事件 gameNo 用）；serveSide 借用最近得分方
 * （接力赛无发球权，后端 serveSide 仅接受 left/right）；matchEnded 触发管道立即冲刷。
 */
function buildEventRuntime(pointSegmentNo) {
  // pointSegmentNo：本分所属段号。段截止那一分先记段比分再构造 runtime，
  // 此时 currentSegmentNo 已前移到下一段，须用加分前捕获的段号标注 gameNo
  const segmentNo = Number(pointSegmentNo) > 0 ? Number(pointSegmentNo) : currentSegmentNo.value;
  return {
    ...buildScoreState(),
    currentSegmentNo: segmentNo,
    currentGameNo: segmentNo,
    serveSide: lastScoredSide.value === "right" ? "right" : "left",
    matchEnded: matchEnded.value,
  };
}
function buildSnapshot() {
  return {
    ...buildScoreState(),
    currentSegmentNo: currentSegmentNo.value,
    historyStack: historyStack.value.map((item) => ({
      ...item,
      segmentScores: Array.isArray(item.segmentScores)
        ? item.segmentScores.map((score) => ({ ...score }))
        : [],
    })),
    synced: synced.value,
    matchEvents: matchEvents.value,
    nextEventSeq: nextEventSeq.value,
    lastSyncedEventSeq: lastSyncedEventSeq.value,
  };
}

function applyScoreState(snapshot) {
  leftScore.value = Number(snapshot?.leftScore || 0);
  rightScore.value = Number(snapshot?.rightScore || 0);
  segmentScores.value = Array.isArray(snapshot?.segmentScores)
    ? snapshot.segmentScores.map((item) => ({ ...item }))
    : [];
  segmentSwitchPending.value = !!snapshot?.segmentSwitchPending;
  sidesSwapped.value = !!snapshot?.sidesSwapped;
  lastScoredSide.value = snapshot?.lastScoredSide || "";
}

function applySnapshot(snapshot) {
  applyScoreState(snapshot);
  historyStack.value = Array.isArray(snapshot?.historyStack)
    ? snapshot.historyStack.map((item) => ({
        ...item,
        segmentScores: Array.isArray(item.segmentScores)
          ? item.segmentScores.map((score) => ({ ...score }))
          : [],
      }))
    : [];
  synced.value = !!snapshot?.synced;
  // 本地缓存同时恢复管道水位与未同步事件（进程被杀后重进的续裁数据源，use-scoreboard-state 同款）
  matchEvents.value = Array.isArray(snapshot?.matchEvents) ? snapshot.matchEvents : [];
  nextEventSeq.value = Number(snapshot?.nextEventSeq || 1);
  lastSyncedEventSeq.value = Number(snapshot?.lastSyncedEventSeq || 0);
}

function saveStateToStorage() {
  try {
    uni.setStorageSync(storageKey(), buildSnapshot());
  } catch (error) {
    console.error("save relay cache failed", error);
  }
}

function clearStateFromStorage() {
  try {
    uni.removeStorageSync(storageKey());
  } catch (error) {
    console.error("clear relay cache failed", error);
  }
}

function restoreStateFromStorage() {
  try {
    const cache = uni.getStorageSync(storageKey());
    if (!cache || typeof cache !== "object") return;
    applySnapshot(cache);
  } catch (error) {
    console.error("restore relay cache failed", error);
  }
}
async function fetchMatchRecord() {
  if (!matchId.value) return null;
  try {
    return await request("/api/v1/matches/" + matchId.value + "/record", {
      method: "GET",
      silent: true,
    });
  } catch (_) {
    // 记录接口失败不阻断进入：仲裁按 record=null 处理，绝不产生写动作
    return null;
  }
}

function readStateCache() {
  try {
    const cache = uni.getStorageSync(storageKey());
    return cache && typeof cache === "object" ? cache : null;
  } catch (_) {
    return null;
  }
}

function applySettledRecord(record) {
  const match = String(record?.scoreDisplay || "").match(/^(\d+):(\d+)$/);
  if (match) {
    leftScore.value = Number(match[1] || 0);
    rightScore.value = Number(match[2] || 0);
  }
  synced.value = true;
}

function applyRelayRecoveredState(recovered) {
  applyScoreState(recovered.snapshot);
  // rebuildHistoryStack 返回各撤销动作的 prevRuntime，与本地栈元素同构，可直接继续撤销
  historyStack.value = Array.isArray(recovered.historyStack)
    ? recovered.historyStack.map((item) => ({ ...item }))
    : [];
}

/**
 * 进入比赛的数据源仲裁与恢复（羽毛球 loadMatchState 同构，「局」换「段」）：
 * - settled：已结算 → 记录比分 + 同步态，清本地，不再重开；
 * - restarted：服务端事件被清（restart 重开）→ 丢弃本地旧一局缓存，从 0:0 开始；
 * - server：服务端事件重放（换设备 / 顶锁 / 本地落后）；
 * - local：sameSession 且本地水位不落后 → 本地续裁（含未同步事件）；
 * - fresh：全新开始（刻意不写 init 基线，首个真实动作自带全量快照）。
 */
async function loadMatchState() {
  const record = await fetchMatchRecord();
  // W2：record 拉取失败（离线）的会话中，服务端状态未知——冲刷 pending 前管道会先探测，
  // 防「restart 清空 + 离线续传」把旧一局事件写进空事件表
  eventPipeline.setServerStateConfirmed(record != null);
  let cached = readStateCache();
  const source = decideRelayEntrySource({
    record,
    cached,
    sameSession: sameSession.value === true,
  });

  if (source === "settled") {
    clearStateFromStorage();
    eventPipeline.reset();
    if (record) applySettledRecord(record);
    return;
  }

  if (source === "restarted") {
    clearStateFromStorage();
    eventPipeline.reset();
    cached = null;
  }

  if (source === "server" && record) {
    const recovered = buildRelayRecoveredState(record);
    if (recovered) {
      applyRelayRecoveredState(recovered);
      eventPipeline.restore({
        events: recovered.events,
        nextEventSeq: recovered.nextEventSeq,
        lastSyncedEventSeq: recovered.lastSyncedEventSeq,
      });
    }
  } else if (source === "local" && cached) {
    restoreStateFromStorage();
    eventPipeline.restore({
      events: matchEvents.value,
      nextEventSeq: nextEventSeq.value,
      lastSyncedEventSeq: lastSyncedEventSeq.value,
    });
  }

  if (source !== "settled" && eventPipeline.hasPendingEvents()) {
    // 恢复出的未同步事件（如退出时防抖窗口内未上云的）尽快续传
    eventPipeline.scheduleFlush(500);
  }
}

function stopMatchLockHeartbeat() {
  if (!stopHeartbeat) return;
  stopHeartbeat();
  stopHeartbeat = null;
}

function enterReadOnly() {
  stopMatchLockHeartbeat();
  // 锁被接管/超时：停发事件（清防抖与重试定时器），全屏遮罩取代只读横幅
  eventPipeline.stop();
  if (isReadOnly.value) return;
  isReadOnly.value = true;
  void lockGuard.showLost();
}


function hasSavedProgress(data) {
  if (Number(data?.matchStatus || 0) === 2 || Number(data?.matchStatus || 0) === 3) return false;
  const items = Array.isArray(data?.items) ? data.items : [];
  return items.some((item) => {
    const leftMembers = Array.isArray(item?.leftMembers) ? item.leftMembers : [];
    const rightMembers = Array.isArray(item?.rightMembers) ? item.rightMembers : [];
    return Number(item?.status || 0) > 0
      || !!item?.childMatchId
      || !!item?.winnerSide
      || leftMembers.length > 0
      || rightMembers.length > 0;
  });
}

function showContinueNotice(content) {
  return new Promise((resolve) => {
    uni.showModal({
      title: "继续执裁",
      content,
      showCancel: false,
      confirmText: "继续执裁",
      success: () => resolve(true),
      fail: () => resolve(true),
    });
  });
}

// 抢锁成功后的会话副作用（setupMatchLock 成功分支与遮罩重试成功共用）
function beginLockSession(result) {
  // 顶替同账号旧设备的统一提示（进入与遮罩重试共用；guard 不再重复弹）
  if (result?.kicked === true) {
    uni.showToast({ title: "已在其他设备打开该比赛，本设备已接管执裁", icon: "none", duration: 2500 });
  }
  sameSession.value = result?.sameSession === true;
  isReadOnly.value = false;
  stopMatchLockHeartbeat();
  saveMatchLockToken(matchId.value, sessionLockToken.value);
  stopHeartbeat = startMatchLockHeartbeat(matchId.value, sessionLockToken.value, () => {
    enterReadOnly();
  });
}

async function setupMatchLock() {
  // 锁 token 持久化：同一设备重进可被判为 sameSession，从而允许本地续裁优先于服务端重放
  sessionLockToken.value = loadMatchLockToken(matchId.value) || createMatchLockToken();
  try {
    // 带确认抢锁：同账号另一设备持锁时先弹「顶替确认」，探测接口缺失则降级直抢（旧行为）
    const result = await lockGuard.acquireGuarded(
      () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
    );
    if (result?.canceled === true) {
      lockGuard.showDenied("已取消顶替，当前账号另一台设备正在执裁");
      return false;
    }
    if (result?.success === true) {
      beginLockSession(result);
      return true;
    }
    lockGuard.showDenied();
    return false;
  } catch (_) {
    lockGuard.showDenied("暂时无法取得执裁权，请稍后再试");
    return false;
  }
}

function matchLockRequestOptions(extra = {}) {
  return {
    ...extra,
    header: {
      ...(extra.header || {}),
      ...matchLockHeader(sessionLockToken.value),
    },
  };
}
// 「事件上云」管道：800ms 防抖批量上云 / 失败指数退避重试 / 409 按服务端水位重排（羽毛球同款）。
// 动作映射注入 relay 版：全 score_snapshot 类型 + 段号 gameNo，理由见 relay-recovery.js 头注释。
const eventPipeline = createMatchEventPipeline({
  events: matchEvents,
  nextEventSeq,
  lastSyncedEventSeq,
  getMatchId: () => matchId.value,
  isReadOnly: () => isReadOnly.value,
  request,
  requestOptions: (extra) => matchLockRequestOptions(extra),
  persist: () => saveStateToStorage(),
  actionEventMap: RELAY_ACTION_EVENT_MAP,
  historyActions: RELAY_HISTORY_ACTIONS,
  // W2：离线会话中比赛被管理端 restart 清空 → 冲刺前探测发现后丢弃本地旧一局，防复活旧比分
  onServerEventsCleared: () => {
    clearStateFromStorage();
    applyScoreState({});
    historyStack.value = [];
    synced.value = false;
    eventPipeline.reset();
    uni.showToast({ title: "比赛已被重开，本地进度已清零", icon: "none" });
  },
});

// 执裁锁遮罩守卫：锁不可得/中途丢失 → 全屏遮罩（取代旧只读模式），重试走顶替确认链路
const lockGuard = useLockGuard({
  getMatchId: () => matchId.value,
  // 探测头带本页 token 换回 sameToken：幂等重进由服务端判定是否弹顶替确认
  getLockToken: () => sessionLockToken.value,
  acquire: () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
  onRetrySuccess: async (result) => {
    // 重抢成功必须全量恢复：绝不能带着遮罩期间的旧内存状态续写
    beginLockSession(result);
    await loadMatchState();
    fetchDetail();
  },
  onBack: () => goBack(),
  buildScore: () => ({
    leftName: teamName("left"),
    rightName: teamName("right"),
    leftScore: leftScore.value,
    rightScore: rightScore.value,
  }),
});

function lineupComplete() {
  return (
    items.value.length > 0 &&
    items.value.every((item) => {
      return (
        Array.isArray(item.leftMembers) &&
        item.leftMembers.length === 2 &&
        Array.isArray(item.rightMembers) &&
        item.rightMembers.length === 2
      );
    })
  );
}

function applySavedScore() {
  const text = detail.value.scoreDisplay || "";
  const match = text.match(/^(\d+):(\d+)$/);
  if (!match) return;
  leftScore.value = Number(match[1] || 0);
  rightScore.value = Number(match[2] || 0);
}

async function fetchDetail() {
  if (!matchId.value) return;
  loading.value = !loadedOnce.value;
  isError.value = false;
  try {
    const data = await request(
      "/api/v1/matches/" + matchId.value + "/team-lineup",
      { method: "GET" },
    );
    detail.value = data || { leftTeam: {}, rightTeam: {}, items: [] };
    if (Number(detail.value.matchStatus || 0) === 2) {
      synced.value = true;
      applySavedScore();
      clearStateFromStorage();
      eventPipeline.reset(); // 已结算不再发事件（兜底 /record 失败走了本地的竞态）
    }
    loadedOnce.value = true;
    if (!isReadOnly.value && !resumeNoticeShown.value && hasSavedProgress(detail.value)) {
      resumeNoticeShown.value = true;
      await showContinueNotice("本场比赛已有保存的比赛进度，继续执裁将从当前进度进入。");
    }
    if (!lineupComplete()) {
      uni.showToast({ title: t.completeLineup, icon: "none" });
    } else {
      promptInitialSideSwap();
    }
  } catch (_) {
    isError.value = true;
  } finally {
    loading.value = false;
  }
}

function promptInitialSideSwap() {
  if (
    initialSidePromptShown.value ||
    scoreStarted.value ||
    matchEnded.value ||
    synced.value
  )
    return;
  initialSidePromptShown.value = true;
  initialSidePromptVisible.value = true;
}

function confirmInitialSideSwap(shouldSwap) {
  if (isReadOnly.value) return;
  initialSidePromptVisible.value = false;
  if (!shouldSwap) return;
  const prevRuntime = buildEventRuntime();
  sidesSwapped.value = !sidesSwapped.value;
  // 初始换边上云：换设备恢复后 sidesSwapped 镜像不丢（不进撤销栈）
  eventPipeline.appendAction({
    action: "side_switch",
    runtime: buildEventRuntime(),
    prevRuntime,
  });
  saveStateToStorage();
}

function pushHistory() {
  historyStack.value.push(buildScoreState());
}

function appendCurrentSegmentScore() {
  const segmentNo = currentSegmentNo.value;
  if (segmentScores.value.some((item) => item.segmentNo === segmentNo)) return;
  segmentScores.value.push({
    segmentNo,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
  });
}

function buildRelaySegmentScores() {
  const scores = segmentScores.value.map((item) => ({ ...item }));
  if (!scores.some((item) => item.segmentNo === currentSegmentNo.value)) {
    scores.push({
      segmentNo: currentSegmentNo.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
    });
  }
  return scores.map((item) => {
    const winnerSide =
      Number(item.leftScore || 0) > Number(item.rightScore || 0)
        ? "left"
        : "right";
    return {
      gameNo: item.segmentNo,
      leftScore: item.leftScore,
      rightScore: item.rightScore,
      winnerSide,
    };
  });
}

function promptSegmentEnd() {
  segmentSwitchClickLocked.value = true;
  if (segmentSwitchUnlockTimer) clearTimeout(segmentSwitchUnlockTimer);
  segmentSwitchUnlockTimer = setTimeout(() => {
    segmentSwitchClickLocked.value = false;
    segmentSwitchUnlockTimer = null;
  }, 500);
}

function addScore(visualSide) {
  if (isReadOnly.value) return;
  if (scoreLocked.value) return;
  if (!lineupComplete()) {
    editLineup();
    return;
  }
  const side = originalSideOfVisual(visualSide);
  // 本分所属段号（段截止那一分会先记段比分，currentSegmentNo 随之前移，须提前捕获）
  const pointSegmentNo = currentSegmentNo.value;
  const prevRuntime = buildEventRuntime();
  pushHistory();
  if (side === "left") leftScore.value += 1;
  else rightScore.value += 1;
  lastScoredSide.value = side;

  const myScore = side === "left" ? leftScore.value : rightScore.value;
  const opponentScore = side === "left" ? rightScore.value : leftScore.value;
  const isMatchPoint = myScore < targetScore.value && myScore + 1 >= targetScore.value;
  const isGamePoint = !isMatchPoint && myScore < currentSegmentTarget.value && myScore + 1 >= currentSegmentTarget.value;
  void announceScore(side, myScore, opponentScore, {
    isGamePoint,
    isMatchPoint,
  });

  if (
    leftScore.value >= targetScore.value ||
    rightScore.value >= targetScore.value
  ) {
    appendCurrentSegmentScore();
    // 制胜分事件 matchEnded=true → 管道立即冲刷不等防抖，防「最后一分后杀进程丢事件」
    eventPipeline.appendAction({ action: "score", scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime });
    saveStateToStorage();
    uni.showToast({ title: t.ended, icon: "success" });
    return;
  }

  if (
    leftScore.value >= currentSegmentTarget.value ||
    rightScore.value >= currentSegmentTarget.value
  ) {
    segmentSwitchPending.value = true;
    appendCurrentSegmentScore();
    eventPipeline.appendAction({ action: "score", scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime });
    saveStateToStorage();
    promptSegmentEnd();
    return;
  }
  eventPipeline.appendAction({ action: "score", scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime });
  saveStateToStorage();
}

function advanceSegment(shouldSwap = true) {
  if (isReadOnly.value) return;
  if (
    !segmentSwitchPending.value ||
    matchEnded.value ||
    !items.value.length || // detail（名单/目标分）未返回时不推进，防段号失真
    segmentSwitchClickLocked.value
  )
    return;

  if (segmentSwitchUnlockTimer) {
    clearTimeout(segmentSwitchUnlockTimer);
    segmentSwitchUnlockTimer = null;
  }
  const prevRuntime = buildEventRuntime();
  segmentSwitchClickLocked.value = false;
  segmentSwitchPending.value = false;
  if (shouldSwap) {
    sidesSwapped.value = !sidesSwapped.value;
  }
  // 段推进不进撤销栈：换边发 side_switch、不换边发 segment_continue（见 relay-recovery.js）
  eventPipeline.appendAction({
    action: shouldSwap ? "side_switch" : "segment_continue",
    runtime: buildEventRuntime(),
    prevRuntime,
  });
  saveStateToStorage();
}

function undo() {
  if (isReadOnly.value) return;
  if (syncing.value || synced.value) return;
  const last = historyStack.value.pop();
  if (!last) return;
  applyScoreState(last);
  // 撤销只追加 undo 快照事件（revertToSeq 由管道按撤销栈水位计算），不重写历史
  eventPipeline.appendAction({
    action: "undo",
    runtime: buildEventRuntime(),
  });
  saveStateToStorage();
}

async function syncResult() {
  if (isReadOnly.value) {
    uni.showToast({ title: "执裁权已丢失，不能结算比赛", icon: "none" });
    return;
  }
  if (syncing.value || synced.value || segmentSwitchPending.value) return;
  const winnerSide =
    leftScore.value > rightScore.value
      ? "left"
      : rightScore.value > leftScore.value
        ? "right"
        : "";
  if (!winnerSide) {
    uni.showToast({ title: t.noWinner, icon: "none" });
    return;
  }
  syncing.value = true;
  try {
    // 结算前强制冲刷事件：保证 finish 落库前服务端已持有本次执裁的全部事件（羽毛球同款）。
    // 冲刷失败则中止结算，防「比分不在云端先 finish」造成记录页缺事件。
    const eventsFlushed = await eventPipeline.flushPendingEventsImmediately();
    if (!eventsFlushed) {
      uni.showToast({ title: "比分数据同步失败，请检查网络后重试", icon: "none", duration: 2500 });
      return;
    }
    await request("/api/v1/matches/" + matchId.value + "/finish", matchLockRequestOptions({
      method: "PUT",
      data: {
        winnerSide,
        leftScore: leftScore.value,
        rightScore: rightScore.value,
        leftGameWins: winnerSide === "left" ? 1 : 0,
        rightGameWins: winnerSide === "right" ? 1 : 0,
        gameScores: [
          {
            gameNo: 1,
            leftScore: leftScore.value,
            rightScore: rightScore.value,
            winnerSide,
          },
        ],
        relaySegmentScores: buildRelaySegmentScores(),
      },
    }));
    synced.value = true;
    stopMatchLockHeartbeat();
    eventPipeline.reset();
    // 排球同款：结算后清 token，避免同设备重进被判 sameSession 复活已结算缓存
    clearMatchLockToken(matchId.value);
    // 羽毛球同款：立即交还执裁权，防 redirect 失败/切后台时残锁阻塞其他裁判
    void releaseMatchLock(matchId.value, sessionLockToken.value);
    clearStateFromStorage();
    uni.showToast({ title: t.synced, icon: "success" });
    setTimeout(() => {
      openRelayRecord();
    }, 500);
  } finally {
    syncing.value = false;
  }
}

function openRelayRecord() {
  uni.redirectTo({
    url:
      "/pages/tournament/relay-record?tournamentId=" +
      encodeURIComponent(tournamentId.value) +
      "&matchId=" +
      encodeURIComponent(matchId.value) +
      (divisionId.value
        ? "&divisionId=" + encodeURIComponent(divisionId.value)
        : ""),
  });
}

async function editLineup() {
  if (isReadOnly.value) return;
  if (!(await guardProfileBeforeAction("请先完善个人资料，再填写顺序名单")))
    return;
  stopMatchLockHeartbeat();
  await releaseMatchLock(matchId.value, sessionLockToken.value);
  uni.navigateTo({
    url:
      "/pages/tournament/team-lineup?tournamentId=" +
      encodeURIComponent(tournamentId.value) +
      "&matchId=" +
      encodeURIComponent(matchId.value) +
      // 带上组别：名单页保存后若栈内比赛页未命中需 redirect 新建，否则新页面会丢组别
      (divisionId.value ? "&divisionId=" + encodeURIComponent(divisionId.value) : ""),
    fail: () => {
      void setupMatchLock();
    },
  });
}

function goBack() {
  // 不再清本地缓存：未同步事件与水位留在缓存里，下次进入由仲裁决定续传或服务端重放
  // （改造前清缓存是为丢弃「仅存本机」的比分；事件上云后服务端已是权威数据源）。
  // 退出前的冲刷已收口到 onUnload 的「先冲刷后释锁」链——goBack 与物理返回键
  // 放行后都必经 onUnload，两条退出路径统一获得冲刷
  navigateBackOrHome();
}

onLoad(async (options) => {
  tournamentId.value = options?.tournamentId || "";
  matchId.value = options?.matchId || "";
  divisionId.value = options?.divisionId || "";
  if (!matchId.value) {
    loading.value = false;
    isError.value = true;
    uni.showToast({ title: t.missingMatchId, icon: "none" });
    return;
  }
  if (!(await guardProfileBeforeAction("请先完善个人资料，再进入接力赛记分"))) {
    navigateBackOrHome();
    return;
  }

  const allowed = await requireMatchOperator(matchId.value);
  if (!allowed) {
    clearStateFromStorage();
    setTimeout(() => navigateBackOrHome(), 1500);
    return;
  }
  // 首抢失败（拒绝遮罩）不加载任何数据；重试成功由 onRetrySuccess 走完整恢复链
  if (!(await setupMatchLock())) return;
  await loadMatchState();
  fetchDetail();
});

onShow(async () => {
  // 遮罩期间不自动重抢锁：重抢必须由用户显式点「重新获取执裁权」（防无感顶替同账号另一设备）
  if (loadedOnce.value && !isReadOnly.value && !lockGuard.maskMode.value) {
    if (!stopHeartbeat) {
      const acquired = await setupMatchLock();
      if (acquired) eventPipeline.scheduleFlush(200);
    }
    fetchDetail();
  }
});

onUnmounted(() => {
  if (segmentSwitchUnlockTimer) clearTimeout(segmentSwitchUnlockTimer);
  stopMatchLockHeartbeat();
  eventPipeline.stop();
  lockGuard.stop();
  destroyScoreAnnouncer();
});

onUnload(() => {
  stopMatchLockHeartbeat();
  lockGuard.stop();
  // B1 先冲刷后释锁：释锁后 PUT /events 必 403，未同步事件必须赶在释锁前上云，
  // 冲刷与释锁不能并发赛跑（释锁先到则事件全灭）。已出栈的请求不随页面卸载回收，
  // 发起后即可继续卸载；冲刷失败释锁照发——releaseMatchLock 本就吞错 best-effort，
  // 未同步事件留在缓存由下次进入续传，75s 租约兜底。物理返回键等所有退出路径
  // （不经 goBack 的）由此统一获得冲刷
  const flushed = eventPipeline.flushPendingEventsImmediately();
  void flushed
    .catch(() => {})
    .finally(() => {
      releaseMatchLock(matchId.value, sessionLockToken.value);
      // 释锁后 in-flight 冲刷若失败，flushInternal 的 catch 在下方同步 stop() 之后、
      // 冲刷 promise 结算之时才 arm 重试定时器，而释锁后每次重试必 403（写守卫），
      // 会形成 ≤30s 一次的无界后台循环钉住闭包，故结算后补一次 stop 掐断；
      // 数据兜底仍是本地缓存 + 下次进入续传
      eventPipeline.stop();
    });
  // stop 只清防抖/重试定时器，必须放在冲刷发起之后：冲刷已同步把请求出栈，
  // 若先 stop 会掐掉防抖定时器，还挂在防抖上的 pending 事件便再无触发时机
  eventPipeline.stop();
});
</script>

<style scoped>
.scoreboard-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  background: #1a2a3a;
  color: #ffffff;
  overflow: hidden;
}

.state-layer {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  z-index: 30;
}

.state-text {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.72);
}

.state-text.error {
  color: #ff8c00;
}

.top-left-actions {
  position: absolute;
  top: 14rpx;
  left: 10rpx;
  z-index: 10;
  display: flex;
  align-items: center;
}

.top-flow-row {
  position: absolute;
  top: 14rpx;
  left: 0;
  right: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  min-width: 0;
  pointer-events: none;
}

.top-center-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5rpx;
  min-width: 0;
  pointer-events: auto;
}

.top-score-anchor {
  color: transparent;
  font-size: 23rpx;
  font-weight: 700;
  white-space: nowrap;
  pointer-events: none;
}

.match-info {
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10rpx;
  min-width: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 15rpx;
  white-space: nowrap;
  pointer-events: none;
}

.game-wins-row {
  position: absolute;
  top: 60rpx;
  left: 0;
  right: 0;
  z-index: 8;
  display: flex;
  align-items: flex-start;
  gap: 28rpx;
  padding: 0 36rpx;
  box-sizing: border-box;
  min-height: 72rpx;
  font-size: 23rpx;
  white-space: nowrap;
}

.game-tag {
  color: #ff8c00;
  font-weight: 700;
}

.game-wins {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: #ff8c00;
  font-weight: 700;
}

.action-btn {
  min-width: 82rpx;
  height: 48rpx;
  line-height: 48rpx;
  padding: 0 8rpx;
  border: 1px solid #ff8c00;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 18rpx;
}

.action-btn::after,
.retry-btn::after,
.final-switch-btn::after,
.new-match-btn::after {
  border: none;
}

.action-btn[disabled],
.new-match-btn[disabled] {
  background: rgba(150, 160, 170, 0.08);
  border-color: rgba(150, 160, 170, 0.46);
  color: rgba(170, 178, 186, 0.72);
  opacity: 1;
}

.icon-action-btn {
  min-width: 30rpx;
  width: 30rpx;
  padding: 0;
}

.sound-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.sound-action-btn.muted {
  border-color: #b84747;
  color: #b84747;
}

.sync-action-btn {
  border-color: #ffffff;
}

.side-action-btn {
  min-width: 80rpx;
  height: 44rpx;
  line-height: 44rpx;
  padding: 0 6rpx;
  font-size: 18rpx;
}

.top-left-actions .action-btn,
.top-center-actions .action-btn {
  margin: 0;
  min-width: 52rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 12rpx;
}

.sound-icon {
  position: relative;
  width: 22rpx;
  height: 22rpx;
  display: block;
  box-sizing: border-box;
}

.sound-icon-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.sound-icon-slash {
  position: absolute;
  left: 2rpx;
  top: 10rpx;
  width: 20rpx;
  height: 2rpx;
  background: currentColor;
  border-radius: 999rpx;
  opacity: 0;
  transform: rotate(-45deg);
  transform-origin: center;
}

.sound-icon.muted .sound-icon-image {
  opacity: 0.45;
}

.sound-icon.muted .sound-icon-slash {
  opacity: 1;
}

.main-panels {
  position: absolute;
  left: 0;
  right: 0;
  top: 40vh;
  bottom: 6vh;
  display: flex;
  padding: 0 36rpx;
  box-sizing: border-box;
  gap: 28rpx;
}

.score-side {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  height: 100%;
}

.left-side {
  justify-content: flex-end;
}

.right-side {
  justify-content: flex-start;
}

.team-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  gap: 6rpx;
  transform: none;
}

.team-summary {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.team-label {
  display: block;
  width: 100%;
  min-width: 0;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.left-label {
  text-align: center;
}

.right-label {
  text-align: center;
}

.team-name {
  display: block;
  max-width: 92%;
  min-height: 36rpx;
  text-align: center;
  font-size: 23rpx;
  font-weight: 400;
  line-height: 1.3;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-box {
  width: 100%;
  max-width: 560rpx;
  flex: 1;
  min-height: 0;
  height: auto;
  padding: 34rpx 16rpx;
  box-sizing: border-box;
  border: 4rpx solid #ff8c00;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.score-box.disabled {
  opacity: 0.5;
}

.score {
  max-width: 90%;
  font-size: 78rpx;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: 2rpx;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: none;
}

.serve-flag {
  max-width: 90%;
  color: #ff8c00;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 24rpx;
  min-height: 24rpx;
  opacity: 0;
  margin-top: 2rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: opacity 0.15s ease;
}

.serve-flag.active {
  opacity: 1;
}

.games-strip {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 18rpx;
  display: flex;
  justify-content: center;
  gap: 8rpx;
  z-index: 9;
}

.game-pill {
  display: flex;
  gap: 6rpx;
  padding: 5rpx 9rpx;
  border-radius: 7rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 15rpx;
}

.final-switch-overlay,
.lock-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 25;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.72);
}

.final-switch-card,
.settlement-card {
  width: 72vw;
  max-width: 620rpx;
  border-radius: 22rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow:
    0 12rpx 40rpx rgba(0, 0, 0, 0.35),
    inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
  padding: 34rpx 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.settlement-card {
  gap: 10rpx;
}

.final-switch-title,
.settlement-title {
  line-height: 1.25;
  font-weight: 700;
}

.final-switch-title {
  font-size: 34rpx;
}

.settlement-title {
  font-size: 30rpx;
}

.final-switch-tip {
  color: rgba(255, 255, 255, 0.86);
}

.final-switch-tip {
  font-size: 22rpx;
}

.settlement-teams {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  color: rgba(255, 255, 255, 0.88);
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.25;
}

.settlement-team-name {
  max-width: 250rpx;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.settlement-team-name.winner {
  color: #ffb347;
}

.settlement-team-sep {
  flex-shrink: 0;
  color: #ffffff;
}

.initial-side-card {
  max-width: 760rpx;
}

.initial-side-teams {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 28rpx;
  color: rgba(255, 255, 255, 0.88);
  font-size: 24rpx;
  line-height: 1.4;
}

.initial-side-team {
  max-width: 320rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.initial-side-question {
  display: block;
  text-align: center;
}

.settlement-score {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.final-switch-btn,
.new-match-btn,
.retry-btn {
  width: 100%;
  max-width: 360rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: rgba(255, 140, 0, 0.2);
  color: #ffffff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 140, 0, 0.55);
}

.final-switch-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 16rpx;
}

.final-switch-btn {
  flex: 1;
  max-width: 240rpx;
  font-size: 22rpx;
}

.final-switch-btn.secondary {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.32);
}

.final-switch-btn[disabled] {
  opacity: 0.45;
}

.new-match-btn,
.retry-btn {
  font-size: 24rpx;
}

.settlement-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 16rpx;
  margin-top: 5rpx;
}

.sync-btn {
  background: rgba(255, 255, 255, 0.1);
}
</style>
