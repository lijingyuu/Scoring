<template>
  <view class="scoreboard-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle">
    <view class="roster-panel left">
      <view class="column-head roster-head">
        <text class="roster-team">{{ ctx.leftDisplayTeamName }}</text>
        <text class="roster-meta">{{ ctx.leftDisplayGameWins }} 局</text>
      </view>
      <view class="column-body roster-body">
        <scroll-view class="roster-scroll" scroll-y>
          <view
            class="roster-item"
            :class="{
              active: ctx.selectedBench.side === 'left' && ctx.selectedBench.memberId === member.id,
              oncourt: ctx.isOnCourt('left', member.id),
              'captain-active': ctx.isCurrentCaptain('left', member.id),
            }"
            v-for="member in ctx.leftDisplayTeam.members"
            :key="member.id"
            @click="ctx.selectBench('left', member.id)"
          >
            <text class="roster-no" :class="{ oncourt: ctx.isOnCourt('left', member.id), captain: ctx.isCurrentCaptain('left', member.id) }">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: ctx.isOnCourt('left', member.id), captain: ctx.isCurrentCaptain('left', member.id) }">{{ member.name }}</text>
            </view>
            <text class="roster-tags" v-if="member.captain">队长</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="center-panel">
      <view class="column-head center-head">
        <view class="score-top">
          <view class="score-top-main">
            <text class="game-pill">第 {{ ctx.currentGameNo }} 局</text>
            <text class="rule-pill">{{ ctx.info.bestOf === 5 ? '五局三胜' : '三局两胜' }}</text>
            <text class="target-pill">本局 {{ ctx.currentTargetPoints }} 分</text>
            <text class="set-pill finished-set-pill" v-for="(score, index) in ctx.finishedGameScores" :key="'finished_game_' + index">
              {{ score }}
            </text>
          </view>
          <view class="score-top-actions">
            <view class="theme-mode-entry">
              <button class="action-btn top-action-btn" @click.stop="ctx.openThemeModePicker">{{ themeModeLabel }}</button>
            </view>
            <button class="action-btn top-action-btn" @click="ctx.undo" :disabled="!ctx.historyStack.length || ctx.isLocked || ctx.isFinalGameSideSwitchPromptActive">撤销</button>
            <button class="action-btn danger top-action-btn" @click="ctx.openRetireSheet" :disabled="ctx.isLocked || ctx.isFinalGameSideSwitchPromptActive">退赛</button>
          </view>
        </view>
      </view>

      <view class="column-body center-body">
        <view class="score-panel">
          <view class="score-main">
            <view class="score-side" @click="ctx.addScore('left')">
              <text class="score-name">{{ ctx.leftDisplayTeamName }}</text>
              <text class="score-value">{{ ctx.leftDisplayScore }}</text>
              <text class="serve-flag" v-if="ctx.displayServeSide === 'left'">发球</text>
            </view>

            <view class="score-center">
              <view class="set-score">{{ ctx.leftDisplayGameWins }} : {{ ctx.rightDisplayGameWins }}</view>
              <button class="action-btn pause-action-btn" @click="ctx.openTimeoutSheet" :disabled="ctx.isLocked || ctx.isFinalGameSideSwitchPromptActive || (ctx.leftTimeouts <= 0 && ctx.rightTimeouts <= 0)">暂停</button>
            </view>

            <view class="score-side right" @click="ctx.addScore('right')">
              <text class="score-name">{{ ctx.rightDisplayTeamName }}</text>
              <text class="score-value">{{ ctx.rightDisplayScore }}</text>
              <text class="serve-flag" v-if="ctx.displayServeSide === 'right'">发球</text>
            </view>
          </view>

          <view class="captain-confirm-overlay" v-if="ctx.isCaptainPromptActive">
            <view class="captain-confirm-card">
              <text class="captain-confirm-title">请确认{{ ctx.captainPromptTeamName }}场上队长</text>
              <text class="captain-confirm-tip">当前只允许从这 6 名场上队员中选择</text>
              <view class="captain-confirm-list">
                <button
                  v-for="member in ctx.captainPromptCandidates"
                  :key="member.id"
                  class="captain-option-btn"
                  :class="{ active: ctx.captainCandidateMemberId === member.id }"
                  @click="ctx.captainCandidateMemberId = member.id"
                >
                  <text class="captain-option-pos">{{ member.positionLabel }}</text>
                  <text class="captain-option-member">{{ member.jerseyNumber }}号 {{ member.name }}</text>
                </button>
              </view>
              <button class="captain-confirm-btn" @click="ctx.confirmCaptainSelection">确定</button>
            </view>
          </view>

          <view class="captain-confirm-overlay final-switch-overlay" v-if="ctx.isFinalGameSideSwitchPromptActive">
            <view class="captain-confirm-card final-switch-card">
              <text class="captain-confirm-title">请双方队员交换场地</text>
              <text class="captain-confirm-tip">当前比分 {{ ctx.finalGameSideSwitchScoreText }}</text>
              <view class="final-switch-actions">
                <button class="final-switch-btn ghost" :class="{ pending: !ctx.canKeepCurrentDisplaySide }" :disabled="!ctx.canKeepCurrentDisplaySide" @click="ctx.keepCurrentDisplaySide">{{ ctx.keepCurrentDisplaySideLabel }}</button>
                <button class="final-switch-btn" @click="ctx.confirmDisplaySideSwitch">确定</button>
              </view>
            </view>
          </view>
        </view>

        <view class="court-card">
          <view class="court-header">
            <text class="court-title">场上轮转</text>
            <text class="court-tip">先点替补，再点场上号码完成换人</text>
          </view>

          <view class="court-board">
            <view class="court-half">
              <view class="court-grid">
                <view
                  class="court-slot"
                  :class="{
                    'captain-active': ctx.isCurrentCaptain('left', item.memberId),
                    'libero-active': item.isLibero,
                  }"
                  v-for="item in ctx.leftCourtDisplaySlots"
                  :key="item.key"
                  @click="ctx.handleCourtSlot('left', item.dataIndex)"
                >
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no" :class="{ libero: item.isLibero, captain: ctx.isCurrentCaptain('left', item.memberId) }">{{ ctx.jerseyText('left', item.memberId) }}</text>
                </view>
              </view>
            </view>

            <view class="court-net"></view>

            <view class="court-half right">
              <view class="court-grid">
                <view
                  class="court-slot"
                  :class="{
                    'captain-active': ctx.isCurrentCaptain('right', item.memberId),
                    'libero-active': item.isLibero,
                  }"
                  v-for="item in ctx.rightCourtDisplaySlots"
                  :key="item.key"
                  @click="ctx.handleCourtSlot('right', item.dataIndex)"
                >
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no" :class="{ libero: item.isLibero, captain: ctx.isCurrentCaptain('right', item.memberId) }">{{ ctx.jerseyText('right', item.memberId) }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="roster-panel right">
      <view class="column-head roster-head">
        <text class="roster-team">{{ ctx.rightDisplayTeamName }}</text>
        <text class="roster-meta">{{ ctx.rightDisplayGameWins }} 局</text>
      </view>
      <view class="column-body roster-body">
        <scroll-view class="roster-scroll" scroll-y>
          <view
            class="roster-item"
            :class="{
              active: ctx.selectedBench.side === 'right' && ctx.selectedBench.memberId === member.id,
              oncourt: ctx.isOnCourt('right', member.id),
              'captain-active': ctx.isCurrentCaptain('right', member.id),
            }"
            v-for="member in ctx.rightDisplayTeam.members"
            :key="member.id"
            @click="ctx.selectBench('right', member.id)"
          >
            <text class="roster-no" :class="{ oncourt: ctx.isOnCourt('right', member.id), captain: ctx.isCurrentCaptain('right', member.id) }">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: ctx.isOnCourt('right', member.id), captain: ctx.isCurrentCaptain('right', member.id) }">{{ member.name }}</text>
            </view>
            <text class="roster-tags" v-if="member.captain">队长</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="settlement-mask" v-if="ctx.isLocked">
      <view class="settlement-card">
        <text class="settlement-title">{{ ctx.retiredSide ? '比赛已退赛结束' : '比赛结束' }}</text>
        <text class="settlement-winner">获胜方：{{ ctx.winnerDisplayName || '待定' }}</text>
        <text class="settlement-score">{{ ctx.leftDisplayGameWins }} : {{ ctx.rightDisplayGameWins }}</text>
        <text class="settlement-games">{{ ctx.scoreSummary || '暂无局分' }}</text>
        <view class="settlement-actions">
          <button class="settlement-btn ghost" :class="{ pending: !ctx.canResetMatch }" :disabled="!ctx.canResetMatch" @click="ctx.resetMatch">{{ ctx.resetMatchLabel }}</button>
          <button class="settlement-btn" @click="ctx.syncAndBack" v-if="ctx.matchId">同步结算</button>
        </view>
      </view>
    </view>

    <view class="theme-debugger" :class="{ collapsed: ctx.themeDebuggerCollapsed }" v-if="ctx.isThemeDebuggerEnabled">
      <button class="theme-debugger-toggle" @click="ctx.toggleThemeDebugger">{{ ctx.themeDebuggerCollapsed ? '调色' : '收起' }}</button>

      <view class="theme-debugger-panel" v-if="!ctx.themeDebuggerCollapsed">
        <view class="theme-debugger-header">
          <view>
            <text class="theme-debugger-title">开发调色板</text>
            <text class="theme-debugger-subtitle">{{ ctx.activeThemeTokenMeta.label }} {{ ctx.themeDraft[ctx.activeThemeToken] }}</text>
          </view>
          <view class="theme-debugger-actions">
            <button class="theme-debugger-btn ghost" size="mini" @click="ctx.resetThemeDraft">重置</button>
            <!-- ==== 已废弃：配色从硬编码直选，不再存后端 ==== -->
            <!-- <button class="theme-debugger-btn ghost" size="mini" :disabled="ctx.themeServerSaving || !ctx.matchId" @click="ctx.saveThemeDraftToServer">{{ ctx.themeServerSaving ? '保存中' : '存后端' }}</button> -->
            <button class="theme-debugger-btn" size="mini" @click="ctx.copyThemeVariables">复制变量</button>
          </view>
        </view>

        <scroll-view class="theme-debugger-list" scroll-y>
          <view class="theme-debugger-item" v-for="item in ctx.themeTokenOptions" :key="item.key">
            <view class="theme-debugger-item-head">
              <view class="theme-debugger-item-meta" :class="{ active: ctx.activeThemeToken === item.key }" @click="ctx.setActiveThemeToken(item.key)">
                <view class="theme-debugger-swatch" :style="{ background: item.value }"></view>
                <text class="theme-debugger-label">{{ item.label }}</text>
              </view>
              <input
                class="theme-debugger-hex"
                :value="ctx.themeHexInputs[item.key]"
                maxlength="7"
                @input="ctx.handleThemeHexInput(item.key, $event.detail.value)"
                @blur="ctx.normalizeThemeHexInput(item.key)"
              />
            </view>
          </view>
        </scroll-view>

        <view class="theme-debugger-sliders">
          <view class="theme-debugger-slider-head">
            <view class="theme-debugger-swatch large" :style="{ background: ctx.themeDraft[ctx.activeThemeToken] }"></view>
            <text class="theme-debugger-slider-title">RGB 微调</text>
          </view>
          <view class="theme-debugger-slider-row" v-for="channel in ctx.rgbChannels" :key="channel.key">
            <text class="theme-debugger-slider-label">{{ channel.label }} {{ ctx.activeThemeRgb[channel.key] }}</text>
            <slider
              class="theme-debugger-slider"
              min="0"
              max="255"
              :value="ctx.activeThemeRgb[channel.key]"
              :activeColor="ctx.themeDraft[ctx.activeThemeToken]"
              :backgroundColor="ctx.sliderTrackBackgroundColor"
              block-size="16"
              @changing="ctx.previewActiveThemeChannel(channel.key, $event.detail.value)"
              @change="ctx.commitActiveThemeChannel(channel.key, $event.detail.value)"
            />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, unref } from 'vue'

const props = defineProps({
  ctx: { type: Object, required: true },
})

const themeModeLabel = computed(() => unref(props.ctx.themeModeLabel))
</script>

<style scoped>
.state-page,
.scoreboard-page,
.score-top,
.score-main,
.score-side,
.action-list,
.set-strip,
.court-header,
.court-board,
.roster-item,
.settlement-actions {
  display: flex;
}

.scoreboard-page {
  --theme-base-rgb: 34, 95, 110;
  --theme-base-deep-rgb: 20, 56, 67;
  --theme-base: #225F6E;
  --theme-base-deep: #143843;
  --theme-accent-rgb: 244, 165, 58;
  --theme-accent: #F4A53A;
  --theme-accent-ink: #194955;
  --captain-rgb: 115, 156, 105;
  --captain: #739C69;
  --court-surface: #1E4F2B;
  --right-score-accent-rgb: 82, 196, 26;
  --danger-accent-rgb: 255, 122, 69;
  --text-strong: #FFFFFF;
  --text-strong-rgb: 255, 255, 255;
  --surface-glass: #FFFFFF;
  --surface-glass-rgb: 255, 255, 255;
  --shadow-color-rgb: 0, 0, 0;
  --overlay-mask-rgb: 7, 18, 28;
  --court-slot-accent-rgb: 0, 143, 141;
  --rotation-panel-surface-rgb: 34, 95, 110;

  --page-pad: clamp(10px, 1.4vmin, 20px);
  --panel-gap: clamp(8px, 1vmin, 14px);
  --panel-radius: clamp(14px, 1.8vmin, 24px);
  --soft-radius: clamp(10px, 1.4vmin, 18px);
  --roster-width: clamp(130px, 16vmin, 220px);
  --head-height: clamp(36px, 5.6vmin, 64px);
  --small-text: clamp(10px, 1.15vmin, 14px);
  --body-text: clamp(11px, 1.35vmin, 16px);
  --title-text: clamp(15px, 2.1vmin, 28px);
  --score-name-text: clamp(14px, 1.9vmin, 24px);
  --score-value-text: clamp(36px, 7.2vmin, 96px);
  --score-center-width: clamp(96px, 13vmin, 180px);
  --action-height: clamp(34px, 5.1vmin, 56px);
  --court-gap: clamp(6px, 0.85vmin, 12px);
  --court-half-pad: clamp(8px, 0.9vmin, 12px);
  --court-line-width: clamp(4.5px, 0.525vmin, 7.5px);
  --court-line-color: rgba(var(--text-strong-rgb), 0.62);
  --court-label-text: clamp(10px, 1.05vmin, 14px);
  --court-number-text: clamp(22px, 3.1vmin, 42px);

  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, var(--theme-base) 0%, var(--theme-base-deep) 100%);
  color: var(--text-strong);
  box-sizing: border-box;
  padding: var(--page-pad);
  gap: var(--panel-gap);
  align-items: stretch;
  overflow: hidden;
}

.scoreboard-page.is-tablet {
  --page-pad: clamp(12px, 1.5vmin, 24px);
  --panel-gap: clamp(10px, 1.1vmin, 16px);
  --panel-radius: clamp(16px, 1.9vmin, 26px);
  --soft-radius: clamp(12px, 1.4vmin, 18px);
  --head-height: clamp(42px, 5.8vmin, 72px);
  --small-text: clamp(12px, 1.2vmin, 17px);
  --body-text: clamp(13px, 1.45vmin, 19px);
  --title-text: clamp(19px, 2.4vmin, 32px);
  --score-name-text: clamp(17px, 2.1vmin, 30px);
  --score-value-text: clamp(46px, 7.5vmin, 116px);
  --action-height: clamp(38px, 5.1vmin, 62px);
  --court-gap: clamp(8px, 0.95vmin, 14px);
  --court-label-text: clamp(12px, 1.15vmin, 16px);
  --court-number-text: clamp(26px, 3.35vmin, 48px);
}

.scoreboard-page.is-tablet.pad-landscape-sm {
  --roster-width: clamp(183px, 16.25vw, 210px);
  --score-center-width: clamp(126px, 13vw, 168px);
  --score-value-text: clamp(48px, 6.9vmin, 88px);
}

.scoreboard-page.is-tablet.pad-landscape-md {
  --roster-width: clamp(210px, 17.25vw, 248px);
  --score-center-width: clamp(138px, 13.2vw, 182px);
  --score-value-text: clamp(54px, 7.2vmin, 102px);
}

.scoreboard-page.is-tablet.pad-landscape-lg {
  --roster-width: clamp(245px, 17.75vw, 298px);
  --score-center-width: clamp(150px, 12.8vw, 206px);
  --score-value-text: clamp(60px, 7.4vmin, 124px);
  --court-number-text: clamp(30px, 3.35vmin, 56px);
}

.scoreboard-page.landscape-preview {
  position: fixed;
  top: 0;
  left: 0;
  width: 1280px;
  height: 720px;
  overflow: hidden;
}

.roster-panel {
  width: var(--roster-width);
  flex: 0 0 var(--roster-width);
  min-width: 0;
  min-height: 0;
  padding: clamp(8px, 1vmin, 14px);
  box-sizing: border-box;
  background: rgba(var(--surface-glass-rgb), 0.04);
  border: 1px solid rgba(var(--surface-glass-rgb), 0.08);
  border-radius: var(--panel-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scoreboard-page.is-tablet .roster-panel,
.scoreboard-page.is-tablet .center-panel,
.scoreboard-page.is-tablet .score-panel,
.scoreboard-page.is-tablet .court-card {
  box-shadow: 0 14px 34px rgba(var(--shadow-color-rgb), 0.16);
}

.scoreboard-page.is-tablet .roster-panel {
  padding: clamp(10px, 1.2vmin, 18px);
}

.roster-panel.right {
  border-right: 1px solid rgba(var(--surface-glass-rgb), 0.08);
}

.column-head {
  min-height: var(--head-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.roster-head {
  justify-content: space-between;
  gap: clamp(4px, 0.5vmin, 8px);
  margin-bottom: clamp(8px, 1vmin, 12px);
}

.roster-team {
  min-width: 0;
  font-size: var(--title-text);
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.roster-meta {
  color: var(--theme-accent);
  font-size: var(--body-text);
  white-space: nowrap;
  flex-shrink: 0;
}

.roster-scroll {
  height: 100%;
  min-height: 0;
}

.roster-item {
  align-items: center;
  gap: clamp(4px, 0.45vmin, 8px);
  padding: clamp(6px, 0.75vmin, 10px) clamp(6px, 0.8vmin, 10px);
  margin-bottom: clamp(5px, 0.55vmin, 8px);
  border-radius: var(--soft-radius);
  background: rgba(var(--surface-glass-rgb), 0.06);
  border: 1px solid rgba(var(--text-strong-rgb), 0.82);
}

.scoreboard-page.is-tablet .roster-item {
  padding: clamp(8px, 0.95vmin, 12px) clamp(8px, 1vmin, 12px);
  margin-bottom: clamp(6px, 0.7vmin, 10px);
}

.roster-item.oncourt {
  border-color: rgba(var(--theme-accent-rgb), 0.3);
}

.roster-item.active {
  background: rgba(var(--theme-accent-rgb), 0.16);
  border-color: rgba(var(--theme-accent-rgb), 0.45);
}

.roster-item.captain-active {
  border-color: var(--captain);
  box-shadow: none;
}

.roster-no {
  width: clamp(20px, 2.6vmin, 34px);
  flex-shrink: 0;
  color: rgba(var(--text-strong-rgb), 0.82);
  font-size: var(--body-text);
  font-weight: 700;
}

.roster-no.oncourt {
  color: var(--theme-accent);
}

.roster-no.captain {
  color: var(--captain);
}

.roster-item.captain-active .roster-no {
  color: var(--captain);
}

.roster-main {
  flex: 1;
  min-width: 0;
}

.roster-name {
  display: inline-block;
  width: 100%;
  font-size: var(--body-text);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-name.oncourt {
  color: var(--theme-accent);
}

.roster-name.captain {
  color: var(--captain);
}

.roster-tags {
  display: inline-block;
  flex-shrink: 0;
  margin-left: clamp(2px, 0.35vmin, 6px);
  color: rgba(var(--text-strong-rgb), 0.56);
  font-size: var(--small-text);
  font-weight: 700;
  white-space: nowrap;
}

.roster-tags.captain {
  color: var(--captain);
}

.center-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: clamp(8px, 1vmin, 16px);
  box-sizing: border-box;
  background: rgba(var(--surface-glass-rgb), 0.03);
  border: 1px solid rgba(var(--surface-glass-rgb), 0.08);
  border-radius: var(--panel-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scoreboard-page.is-tablet .center-panel {
  padding: clamp(10px, 1.2vmin, 20px);
}

.center-head {
  justify-content: center;
  margin-bottom: clamp(8px, 1vmin, 12px);
}

.center-body {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: var(--panel-gap);
  overflow: hidden;
}

.score-panel,
.settlement-card {
  border-radius: var(--panel-radius);
  background: rgba(var(--surface-glass-rgb), 0.05);
  border: 1px solid rgba(var(--theme-accent-rgb), 0.16);
}

.score-panel {
  flex-shrink: 0;
  padding: clamp(10px, 1.1vmin, 16px) clamp(10px, 1.2vmin, 18px);
  overflow: hidden;
  position: relative;
}

.scoreboard-page.is-tablet .score-panel {
  padding: clamp(12px, 1.3vmin, 20px) clamp(12px, 1.4vmin, 22px);
}

.scoreboard-page.is-tablet .score-main {
  gap: clamp(10px, 1.15vmin, 18px);
}

.scoreboard-page.is-tablet .score-side {
  min-height: clamp(120px, 21vmin, 210px);
  padding: clamp(10px, 1vmin, 14px);
}

.scoreboard-page.is-tablet .set-score {
  font-size: clamp(28px, 4vmin, 52px);
}

.score-top {
  align-items: center;
  justify-content: space-between;
  gap: clamp(4px, 0.55vmin, 8px);
  flex-wrap: nowrap;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.score-top-main {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: clamp(4px, 0.55vmin, 8px);
  flex: 0 1 auto;
  flex-shrink: 0;
  min-width: 0;
  overflow: hidden;
}

.score-top-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: clamp(4px, 0.45vmin, 8px);
  flex-shrink: 0;
  position: relative;
  z-index: 3;
}

.theme-mode-entry {
  position: relative;
}

.game-pill,
.rule-pill,
.target-pill,
.set-pill {
  padding: clamp(4px, 0.55vmin, 8px) clamp(8px, 1vmin, 12px);
  border-radius: 999rpx;
  background: rgba(var(--surface-glass-rgb), 0.08);
  color: rgba(var(--text-strong-rgb), 0.82);
  font-size: var(--small-text);
  white-space: nowrap;
}

.game-pill {
  color: var(--theme-accent);
}

.score-main {
  align-items: stretch;
  margin-top: clamp(8px, 0.9vmin, 12px);
  gap: clamp(8px, 1vmin, 14px);
  min-height: 0;
}

.score-side {
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: clamp(96px, 19vmin, 180px);
  padding: clamp(6px, 0.75vmin, 10px);
  box-sizing: border-box;
  border-radius: clamp(14px, 1.8vmin, 24px);
  background: rgba(var(--surface-glass-rgb), 0.06);
  border: 2px solid rgba(var(--text-strong-rgb), 0.26);
  overflow: hidden;
}

.score-side.right {
  border-color: rgba(var(--text-strong-rgb), 0.26);
}

.score-name {
  width: 100%;
  text-align: center;
  font-size: var(--score-name-text);
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.score-value {
  font-size: calc(var(--score-value-text) * 1.2);
  line-height: 1;
  font-weight: 800;
  margin-top: clamp(4px, 0.5vmin, 8px);
}

.serve-flag {
  margin-top: clamp(6px, 0.7vmin, 10px);
  color: var(--theme-accent);
  font-size: calc(clamp(12px, 1.45vmin, 18px) * 1.3);
  font-weight: 700;
  white-space: nowrap;
}

.score-center {
  width: var(--score-center-width);
  flex: 0 0 var(--score-center-width);
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: clamp(6px, 0.7vmin, 10px);
}

.set-score {
  text-align: center;
  font-size: clamp(24px, 3.8vmin, 46px);
  font-weight: 800;
  color: var(--text-strong);
  white-space: nowrap;
}

.action-list {
  flex-direction: column;
  gap: clamp(6px, 0.7vmin, 10px);
}

.action-btn,
.settlement-btn {
  border: none;
  border-radius: clamp(10px, 1.2vmin, 14px);
  background: rgba(var(--surface-glass-rgb), 0.08);
  color: var(--text-strong);
  font-size: clamp(11px, 1.2vmin, 16px);
}

.action-btn::after,
.settlement-btn::after {
  border: none;
}

.action-btn {
  height: var(--action-height);
  line-height: var(--action-height);
  white-space: nowrap;
}

.top-action-btn {
  min-width: clamp(48px, 5.8vmin, 74px);
  padding: 0 clamp(8px, 0.8vmin, 12px);
}

.scoreboard-page.is-tablet .top-action-btn {
  min-width: clamp(58px, 6vmin, 88px);
}

.pause-action-btn {
  width: 100%;
}

.action-btn.danger {
  color: rgb(var(--danger-accent-rgb));
  border: 1px solid rgba(var(--danger-accent-rgb), 0.35);
}

.set-strip {
  justify-content: center;
  gap: clamp(4px, 0.5vmin, 8px);
  margin-top: clamp(8px, 0.95vmin, 12px);
  flex-wrap: wrap;
  overflow: hidden;
}

.set-pill {
  display: inline-flex;
  gap: clamp(3px, 0.35vmin, 6px);
}

.captain-confirm-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(16px, 2.4vmin, 28px);
  box-sizing: border-box;
  background: rgba(var(--overlay-mask-rgb), 0.82);
  z-index: 60;
}

.captain-confirm-card {
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 0.9vmin, 12px);
  width: min(100%, 980px);
  max-height: calc(100vh - clamp(32px, 4.8vmin, 56px));
  padding: clamp(16px, 2vmin, 24px);
  box-sizing: border-box;
  background: var(--theme-base);
  border-radius: var(--panel-radius);
  border: 1px solid rgba(var(--text-strong-rgb), 0.16);
  box-shadow: 0 20px 48px rgba(var(--shadow-color-rgb), 0.32), inset 0 0 0 9999px rgba(var(--shadow-color-rgb), 0.12);
  overflow: hidden;
}

.scoreboard-page.is-tablet .captain-confirm-card {
  width: min(100%, 1120px);
}

.captain-confirm-title {
  font-size: clamp(14px, 1.7vmin, 22px);
  font-weight: 800;
  color: var(--text-strong);
}

.captain-confirm-tip {
  color: rgba(var(--text-strong-rgb), 0.72);
  font-size: var(--small-text);
}

.captain-confirm-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-flow: row;
  gap: clamp(6px, 0.8vmin, 10px);
  overflow: auto;
  padding-right: 2px;
}

.captain-option-btn,
.captain-confirm-btn {
  border: none;
  border-radius: clamp(10px, 1.2vmin, 14px);
}

.captain-option-btn::after,
.captain-confirm-btn::after {
  border: none;
}

.captain-option-btn {
  height: clamp(64px, 8vmin, 82px);
  width: 100%;
  background: rgba(var(--text-strong-rgb), 0.08);
  color: var(--text-strong);
  font-size: clamp(11px, 1.2vmin, 15px);
  padding: clamp(8px, 1vmin, 12px) clamp(6px, 0.8vmin, 10px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(4px, 0.45vmin, 6px);
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.14);
}

.scoreboard-page.is-tablet .captain-option-btn {
  height: clamp(74px, 8.2vmin, 96px);
}

.captain-option-btn.active {
  background: rgba(var(--text-strong-rgb), 0.18);
  color: var(--text-strong);
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.42);
}

.captain-option-pos,
.captain-option-member {
  display: block;
  line-height: 1.2;
}

.captain-option-pos {
  font-size: clamp(12px, 1.3vmin, 16px);
  font-weight: 800;
}

.captain-option-member {
  font-size: clamp(11px, 1.1vmin, 14px);
  color: rgba(var(--text-strong-rgb), 0.82);
}

.captain-confirm-btn {
  align-self: stretch;
  min-width: 0;
  height: clamp(40px, 4.8vmin, 54px);
  line-height: clamp(40px, 4.8vmin, 54px);
  background: rgba(var(--text-strong-rgb), 0.18);
  color: var(--text-strong);
  font-size: clamp(12px, 1.25vmin, 16px);
  font-weight: 800;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.28);
}

.final-switch-overlay {
  z-index: 61;
}

.final-switch-card {
  width: min(100%, 560px);
}

.final-switch-actions {
  display: flex;
  gap: clamp(10px, 1.1vmin, 16px);
  margin-top: clamp(14px, 1.6vmin, 22px);
}

.final-switch-btn {
  flex: 1;
  height: clamp(42px, 5.6vmin, 64px);
  line-height: clamp(42px, 5.6vmin, 64px);
  border: none;
  border-radius: clamp(14px, 1.6vmin, 20px);
  background: rgba(var(--text-strong-rgb), 0.18);
  color: var(--text-strong);
  font-size: clamp(14px, 1.5vmin, 18px);
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.28);
}

.final-switch-btn.ghost {
  background: rgba(var(--text-strong-rgb), 0.08);
  color: rgba(var(--text-strong-rgb), 0.88);
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.14);
}

.final-switch-btn::after {
  border: none;
}

.final-switch-btn.pending,
.final-switch-btn[disabled] {
  opacity: 0.48;
}

.court-card {
  flex: 1;
  min-height: 0;
  border-radius: var(--panel-radius);
  background: rgb(var(--rotation-panel-surface-rgb));
  border: 1px solid rgba(var(--theme-accent-rgb), 0.16);
  padding: clamp(10px, 1.1vmin, 16px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scoreboard-page.is-tablet .court-card {
  padding: clamp(12px, 1.25vmin, 20px);
}

.scoreboard-page.is-tablet .court-half {
  padding: clamp(10px, 1.05vmin, 16px);
  border-radius: 0;
  background: transparent;
}

.court-header {
  align-items: center;
  justify-content: center;
  gap: clamp(4px, 0.5vmin, 8px);
  min-width: 0;
  flex-shrink: 0;
  overflow: hidden;
}

.court-title {
  font-size: clamp(13px, 1.55vmin, 20px);
  font-weight: 700;
  white-space: nowrap;
}

.court-tip {
  color: rgba(var(--text-strong-rgb), 0.58);
  font-size: var(--small-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.court-board {
  flex: 1;
  min-height: 0;
  margin-top: clamp(8px, 0.95vmin, 12px);
  align-items: stretch;
  gap: var(--court-gap);
  overflow: hidden;
}

.court-half {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: var(--court-half-pad);
  border-radius: clamp(12px, 1.5vmin, 18px);
  background: rgba(var(--surface-glass-rgb), 0.05);
  overflow: hidden;
  position: relative;
}

.court-net {
  width: var(--court-line-width);
  flex: 0 0 var(--court-line-width);
  align-self: stretch;
  margin-top: clamp(-6px, -0.5vmin, -4px);
  margin-bottom: clamp(-6px, -0.5vmin, -4px);
  background: var(--court-line-color);
}

.court-grid {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: var(--court-gap);
  min-height: 0;
}

.court-half .court-grid {
  grid-template-columns: minmax(0, 1.8fr) minmax(0, 1fr);
}

.court-half.right .court-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.8fr);
}

.scoreboard-page.is-tablet .court-half::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: calc(63.5714% - (var(--court-half-pad) * 0.271428) - (var(--court-gap) * 0.135714));
  width: clamp(3px, 0.35vmin, 5px);
  transform: translateX(-50%);
  background: rgba(var(--text-strong-rgb), 0.52);
  pointer-events: none;
}

.scoreboard-page.is-tablet .court-half.right::after {
  left: calc(36.4286% + (var(--court-half-pad) * 0.271428) + (var(--court-gap) * 0.135714));
}

.court-slot {
  border-radius: var(--soft-radius);
  background: rgba(var(--surface-glass-rgb), 0.08);
  border: 2px solid rgba(var(--theme-accent-rgb), 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  padding: clamp(6px, 0.65vmin, 10px);
  box-sizing: border-box;
  overflow: hidden;
}

.scoreboard-page.is-tablet .court-board {
  background: var(--court-surface);
  border: var(--court-line-width) solid var(--court-line-color);
  box-sizing: border-box;
}

.scoreboard-page.is-tablet .court-slot {
  padding: clamp(8px, 0.8vmin, 12px);
  width: 90%;
  height: 90%;
  justify-self: center;
  align-self: center;
  background: transparent;
  border-width: 2px;
  color: var(--text-strong);
  border-color: rgba(var(--text-strong-rgb), 0.25);
}

.scoreboard-page.is-tablet .court-slot.libero-active {
  color: var(--theme-accent);
  border-color: rgba(var(--theme-accent-rgb), 0.25);
}

.scoreboard-page.is-tablet .court-slot.captain-active {
  color: var(--captain);
  border-color: rgba(var(--captain-rgb), 0.25);
  box-shadow: none;
}

.slot-pos {
  color: rgba(var(--text-strong-rgb), 0.45);
  font-size: var(--court-label-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-no {
  margin-top: clamp(4px, 0.45vmin, 6px);
  font-size: calc(var(--court-number-text) * 1.25);
  font-weight: 800;
  color: currentColor;
  white-space: nowrap;
}

.finished-set-pill {
  color: rgba(var(--text-strong-rgb), 0.9);
  background: rgba(var(--surface-glass-rgb), 0.06);
  border: 1px solid rgba(var(--surface-glass-rgb), 0.08);
}

.slot-no.libero {
  color: var(--theme-accent);
}

.slot-no.captain {
  color: var(--captain);
}

.settlement-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--shadow-color-rgb), 0.76);
  z-index: 50;
  padding: 20rpx;
  box-sizing: border-box;
}

.settlement-card {
  width: clamp(320px, 52vmin, 560px);
  padding: clamp(16px, 2vmin, 28px);
  box-sizing: border-box;
  text-align: center;
  background: var(--theme-base);
  border: 1px solid rgba(var(--text-strong-rgb), 0.16);
  box-shadow: 0 20px 48px rgba(var(--shadow-color-rgb), 0.32), inset 0 0 0 9999px rgba(var(--shadow-color-rgb), 0.12);
}

.scoreboard-page.is-tablet .settlement-card {
  width: min(100%, 680px);
  max-height: calc(100vh - 48px);
  overflow: auto;
}

.settlement-title {
  display: block;
  font-size: clamp(16px, 2.1vmin, 28px);
  font-weight: 800;
}

.settlement-winner {
  display: block;
  margin-top: clamp(8px, 0.9vmin, 12px);
  color: rgba(var(--text-strong-rgb), 0.92);
  font-size: clamp(13px, 1.5vmin, 20px);
}

.settlement-score {
  display: block;
  margin-top: clamp(10px, 1.2vmin, 16px);
  font-size: clamp(34px, 5.8vmin, 76px);
  font-weight: 800;
  line-height: 1;
}

.settlement-games {
  display: block;
  margin-top: clamp(8px, 0.9vmin, 12px);
  color: rgba(var(--text-strong-rgb), 0.76);
  font-size: clamp(12px, 1.35vmin, 18px);
}

.settlement-actions {
  gap: clamp(8px, 0.9vmin, 12px);
  margin-top: clamp(14px, 1.6vmin, 22px);
}

.settlement-btn {
  flex: 1;
  height: clamp(40px, 5.5vmin, 64px);
  line-height: clamp(40px, 5.5vmin, 64px);
  background: rgba(var(--text-strong-rgb), 0.18);
  color: var(--text-strong);
  font-size: clamp(13px, 1.45vmin, 18px);
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(var(--text-strong-rgb), 0.28);
}

.settlement-btn.ghost {
  background: rgba(var(--text-strong-rgb), 0.08);
  color: rgba(var(--text-strong-rgb), 0.86);
}

.settlement-btn.pending,
.settlement-btn[disabled] {
  opacity: 0.48;
}

@media (max-width: 1400px) {
  .scoreboard-page {
    --roster-width: clamp(130px, 14vmin, 190px);
    --score-center-width: clamp(92px, 12vmin, 150px);
    --score-value-text: clamp(34px, 6.4vmin, 80px);
  }
}

@media (max-width: 1100px) {
  .scoreboard-page {
    --page-pad: clamp(8px, 1vmin, 14px);
    --panel-gap: clamp(6px, 0.8vmin, 10px);
    --roster-width: 130px;
    --title-text: clamp(14px, 1.8vmin, 20px);
    --score-center-width: 96px;
    --score-value-text: clamp(32px, 5.8vmin, 68px);
    --court-number-text: clamp(20px, 2.6vmin, 30px);
  }
}

.theme-debugger {
  position: fixed;
  right: clamp(12px, 1.5vmin, 20px);
  bottom: clamp(12px, 1.5vmin, 20px);
  z-index: 80;
  width: min(360px, 32vw);
  max-width: calc(100vw - 24px);
}

.theme-debugger.collapsed {
  width: auto;
}

.theme-debugger-toggle,
.theme-debugger-btn {
  border: none;
}

.theme-debugger-toggle::after,
.theme-debugger-btn::after {
  border: none;
}

.theme-debugger-toggle {
  min-width: clamp(72px, 8vmin, 96px);
  height: clamp(40px, 4.8vmin, 52px);
  line-height: clamp(40px, 4.8vmin, 52px);
  border-radius: 999px;
  background: var(--theme-accent);
  color: var(--theme-accent-ink);
  font-size: clamp(12px, 1.2vmin, 16px);
  font-weight: 800;
  box-shadow: 0 10px 28px rgba(var(--shadow-color-rgb), 0.26);
}

.theme-debugger-panel {
  margin-top: 10px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(var(--theme-base-deep-rgb), 0.94);
  border: 1px solid rgba(var(--theme-accent-rgb), 0.32);
  box-shadow: 0 16px 36px rgba(var(--shadow-color-rgb), 0.3);
  backdrop-filter: blur(10px);
}

.theme-debugger-header,
.theme-debugger-actions,
.theme-debugger-item-head,
.theme-debugger-item-meta,
.theme-debugger-slider-head {
  display: flex;
  align-items: center;
}

.theme-debugger-header {
  justify-content: space-between;
  gap: 10px;
}

.theme-debugger-title {
  display: block;
  font-size: clamp(13px, 1.35vmin, 18px);
  font-weight: 800;
  color: var(--text-strong);
}

.theme-debugger-subtitle {
  display: block;
  margin-top: 3px;
  color: rgba(var(--text-strong-rgb), 0.64);
  font-size: clamp(10px, 1.05vmin, 13px);
}

.theme-debugger-actions {
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.theme-debugger-btn {
  height: 32px;
  line-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--theme-accent);
  color: var(--theme-accent-ink);
  font-size: 12px;
  font-weight: 700;
}

.theme-debugger-btn.ghost {
  background: rgba(var(--surface-glass-rgb), 0.08);
  color: var(--text-strong);
}

.theme-debugger-list {
  height: clamp(180px, 34vh, 310px);
  margin-top: 12px;
  padding-right: 2px;
}

.theme-debugger-item + .theme-debugger-item {
  margin-top: 8px;
}

.theme-debugger-item-head {
  gap: 10px;
}

.theme-debugger-item-meta {
  flex: 1;
  min-width: 0;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(var(--surface-glass-rgb), 0.06);
  border: 1px solid transparent;
}

.theme-debugger-item-meta.active {
  border-color: rgba(var(--theme-accent-rgb), 0.45);
  background: rgba(var(--theme-accent-rgb), 0.14);
}

.theme-debugger-swatch {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  border: 1px solid rgba(var(--text-strong-rgb), 0.18);
  flex-shrink: 0;
}

.theme-debugger-swatch.large {
  width: 28px;
  height: 28px;
  border-radius: 8px;
}

.theme-debugger-label,
.theme-debugger-slider-title {
  color: var(--text-strong);
  font-size: clamp(11px, 1.1vmin, 14px);
  font-weight: 700;
}

.theme-debugger-hex {
  width: 92px;
  height: 36px;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(var(--surface-glass-rgb), 0.08);
  color: var(--text-strong);
  font-size: 12px;
  text-align: center;
  box-sizing: border-box;
}

.theme-debugger-sliders {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(var(--surface-glass-rgb), 0.08);
}

.theme-debugger-slider-head {
  gap: 10px;
}

.theme-debugger-slider-row + .theme-debugger-slider-row {
  margin-top: 8px;
}

.theme-debugger-slider-label {
  display: block;
  color: rgba(var(--text-strong-rgb), 0.7);
  font-size: 12px;
}

.theme-debugger-slider {
  margin-top: 4px;
}
</style>
