<template>
  <view class="page">
    <view v-if="loading" class="state-layer">
      <text class="state-text">正在加载比赛记录...</text>
    </view>

    <view v-else-if="isError" class="state-layer">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadRecord">重新加载</button>
    </view>

    <template v-else-if="record">
      <view class="toolbar">
        <text class="back-btn" @click="goBack">返回</text>
        <view class="toolbar-actions">
          <button class="toolbar-btn ghost" disabled>高清图片导出开发中</button>
          <button class="toolbar-btn" @click="exportAsPdf">H5 打印 / PDF</button>
        </view>
      </view>

      <scroll-view class="page-scroll" scroll-y>
        <view id="record-export-root" class="record-shell">
          <view class="paper">
            <view class="paper-header">
              <view class="header-main">
                <text class="header-title">{{ header.tournamentName || '赛事记录' }}</text>
                <view class="header-meta-block">
                  <view class="header-meta-line">
                    <text class="meta-label">比赛时间：</text>
                    <text class="meta-value">{{ header.matchTimeText || '待补充' }}</text>
                  </view>
                  <view class="header-meta-line">
                    <text class="meta-label">比赛队伍：</text>
                    <text class="meta-value team-meta-value">{{ teamSummaryText }}</text>
                  </view>
                  <view class="header-meta-line">
                    <text class="meta-label">总比分：</text>
                    <text class="meta-value">{{ scoreSummaryText }}</text>
                  </view>
                </view>
              </view>

              <view class="score-card">
                <text class="score-title">总比分</text>
                <view class="score-main">
                  <text class="score-number">{{ header.leftGameWins ?? 0 }}</text>
                  <text class="score-sep">:</text>
                  <text class="score-number">{{ header.rightGameWins ?? 0 }}</text>
                </view>
                <text class="score-sub">{{ winnerText }}</text>
              </view>
            </view>

            <view class="game-score-panel">
              <view
                v-for="score in fixedScores"
                :key="'score_' + score.gameNo"
                class="game-score-item"
              >
                <text class="game-score-label">第{{ score.gameNo }}局</text>
                <text class="game-score-value">{{ formatGameScore(score) }}</text>
              </view>
            </view>

            <view class="roster-section">
              <view class="roster-card">
                <text class="section-title">A队名单</text>
                <view class="roster-table">
                  <view
                    v-for="(row, rowIndex) in roster.leftRows"
                    :key="'left_row_' + rowIndex"
                    class="roster-row"
                  >
                    <view
                      v-for="(member, memberIndex) in row"
                      :key="'left_member_' + rowIndex + '_' + memberIndex"
                      class="roster-cell"
                    >
                      <text class="roster-no">{{ member?.jerseyNumber ?? '-' }}</text>
                      <text class="roster-name">{{ member?.name || '-' }}</text>
                    </view>
                  </view>
                </view>
              </view>

              <view class="roster-card">
                <text class="section-title">B队名单</text>
                <view class="roster-table">
                  <view
                    v-for="(row, rowIndex) in roster.rightRows"
                    :key="'right_row_' + rowIndex"
                    class="roster-row"
                  >
                    <view
                      v-for="(member, memberIndex) in row"
                      :key="'right_member_' + rowIndex + '_' + memberIndex"
                      class="roster-cell"
                    >
                      <text class="roster-no">{{ member?.jerseyNumber ?? '-' }}</text>
                      <text class="roster-name">{{ member?.name || '-' }}</text>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <view class="games-section">
              <view
                v-for="game in renderGames"
                :key="'game_' + game.gameNo"
                class="game-entry"
              >
                <view v-if="coinTossMap[game.gameNo]" class="game-block-toss-row">
                  <text class="game-block-toss">{{ coinTossMap[game.gameNo] }}</text>
                </view>

                <view class="game-block">
                  <view class="game-block-body">
                    <view class="game-block-side">
                      <text
                        v-for="(char, index) in game.title.split('')"
                        :key="'title_' + game.gameNo + '_' + index"
                        class="game-block-title-char"
                      >
                        {{ char }}
                      </text>
                    </view>

                    <view class="game-block-panels">
                      <view class="rotation-panel">
                        <text class="rotation-team-label">A队</text>
                        <view class="rotation-grid">
                          <view
                            v-for="cell in game.leftRotationGrid"
                            :key="'left_cell_' + game.gameNo + '_' + cell.slotIndex"
                            class="rotation-cell"
                            :class="{ slashed: cell.slashed }"
                          >
                            <text class="rotation-primary">{{ formatJersey(cell.primaryJerseyNumber) }}</text>
                            <text v-if="cell.slashed" class="rotation-secondary">{{ formatJersey(cell.secondaryJerseyNumber) }}</text>
                          </view>
                        </view>
                      </view>

                      <view class="rotation-panel">
                        <text class="rotation-team-label">B队</text>
                        <view class="rotation-grid">
                          <view
                            v-for="cell in game.rightRotationGrid"
                            :key="'right_cell_' + game.gameNo + '_' + cell.slotIndex"
                            class="rotation-cell"
                            :class="{ slashed: cell.slashed }"
                          >
                            <text class="rotation-primary">{{ formatJersey(cell.primaryJerseyNumber) }}</text>
                            <text v-if="cell.slashed" class="rotation-secondary">{{ formatJersey(cell.secondaryJerseyNumber) }}</text>
                          </view>
                        </view>
                      </view>

                      <view class="timeout-panel">
                        <text class="timeout-title">暂停记录</text>
                        <view class="timeout-body">
                          <text
                            v-for="(line, index) in normalizedTimeoutLines(game.timeoutLines)"
                            :key="'timeout_' + game.gameNo + '_' + index"
                            class="timeout-line"
                          >
                            {{ line }}
                          </text>
                        </view>
                      </view>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <view class="signature-section">
              <view class="signature-column">
                <view class="signature-row">
                  <text class="signature-label">{{ signatures.aCaptainLabel || 'A队队长' }}：</text>
                </view>
                <view class="signature-row">
                  <text class="signature-label">{{ signatures.bCaptainLabel || 'B队队长' }}：</text>
                </view>
              </view>

              <view class="signature-column">
                <view class="signature-row">
                  <text class="signature-label">{{ signatures.chiefRefereeLabel || '主裁' }}：</text>
                  <text class="signature-value">{{ signatures.chiefRefereeName || '待补充' }}</text>
                </view>
                <view class="signature-row">
                  <text class="signature-label">{{ signatures.assistantRefereeLabel || '副裁' }}：</text>
                  <text class="signature-value">{{ signatures.assistantRefereeName || '待补充' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const matchId = ref('')
const record = ref(null)

const header = computed(() => record.value?.reportRender?.header || {})
const roster = computed(() => record.value?.reportRender?.roster || { leftRows: [[]], rightRows: [[]] })
const renderGames = computed(() => Array.isArray(record.value?.reportRender?.games) ? record.value.reportRender.games : [])
const signatures = computed(() => record.value?.reportRender?.signatures || {})

const coinTossMap = computed(() => {
  const blocks = Array.isArray(record.value?.reportRender?.coinTossBlocks) ? record.value.reportRender.coinTossBlocks : []
  return blocks.reduce((acc, item) => {
    acc[item.gameNo] = item.text
    return acc
  }, {})
})

const fixedScores = computed(() => Array.isArray(header.value?.gameScores) ? header.value.gameScores : [])

const teamSummaryText = computed(() => {
  return header.value.teamSummaryText || `A队：${header.value.leftTeamName || 'A队'} / B队：${header.value.rightTeamName || 'B队'}`
})

const scoreSummaryText = computed(() => {
  return header.value.scoreSummaryText || `A队 ${header.value.leftGameWins ?? 0}:${header.value.rightGameWins ?? 0} B队，${scoreSummaryWinnerText.value}`
})

const winnerText = computed(() => {
  if (!record.value?.winnerSide) return '胜方待确认'
  return record.value.winnerSide === 'left'
    ? `${header.value.leftTeamName || 'A队'} 获胜`
    : `${header.value.rightTeamName || 'B队'} 获胜`
})

const scoreSummaryWinnerText = computed(() => {
  if (!record.value?.winnerSide) return '胜方待确认'
  return record.value.winnerSide === 'left' ? 'A队获胜' : 'B队获胜'
})

function goBack() {
  uni.navigateBack()
}

function formatRule(data) {
  const bestOf = Number(data?.bestOf || 3)
  return bestOf === 5 ? '五局三胜' : '三局两胜'
}

function formatGameScore(score) {
  if (!score || score.leftScore === null || score.leftScore === undefined || score.rightScore === null || score.rightScore === undefined) {
    return '-- : --'
  }
  return `${score.leftScore} : ${score.rightScore}`
}

function formatJersey(value) {
  return value === null || value === undefined ? '' : String(value)
}

function normalizedTimeoutLines(lines) {
  const safeLines = Array.isArray(lines) ? lines.slice(0, 4) : []
  while (safeLines.length < 4) {
    safeLines.push('')
  }
  return safeLines
}

async function loadRecord() {
  if (!matchId.value) {
    isError.value = true
    errorText.value = '缺少比赛ID'
    loading.value = false
    return
  }

  loading.value = true
  isError.value = false
  try {
    record.value = await request('/api/v1/matches/' + matchId.value + '/record', { method: 'GET' })
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载比赛记录失败'
  } finally {
    loading.value = false
  }
}

function exportAsPdf() {
  // #ifdef H5
  window.print()
  return
  // #endif
  uni.showToast({ title: '当前先保留 H5 打印导出，高清图片导出下一步补齐', icon: 'none' })
}

onLoad((options) => {
  matchId.value = options?.matchId || ''
  loadRecord()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top, rgba(227, 164, 95, 0.18), transparent 24%),
    linear-gradient(180deg, #0f1720 0%, #0a1118 100%);
}

.state-layer {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 40rpx;
  box-sizing: border-box;
}

.state-text {
  color: rgba(255, 255, 255, 0.82);
  font-size: 30rpx;
}

.state-error {
  color: #ffb347;
}

.retry-btn,
.toolbar-btn {
  border: none;
  border-radius: 18rpx;
}

.retry-btn::after,
.toolbar-btn::after {
  border: none;
}

.retry-btn {
  width: 280rpx;
  height: 76rpx;
  line-height: 76rpx;
  background: #ffb347;
  color: #13202d;
  font-size: 28rpx;
  font-weight: 700;
}

.toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 24rpx 16rpx;
  backdrop-filter: blur(18rpx);
  background: rgba(10, 18, 27, 0.72);
}

.back-btn {
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 700;
}

.toolbar-actions {
  display: flex;
  gap: 12rpx;
}

.toolbar-btn {
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 22rpx;
  background: #ffb347;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.toolbar-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.toolbar-btn[disabled] {
  opacity: 0.56;
}

.page-scroll {
  height: calc(100vh - 106rpx);
}

.record-shell {
  padding: 22rpx 12rpx 36rpx;
  box-sizing: border-box;
}

.paper {
  width: 100%;
  max-width: 930rpx;
  margin: 0 auto;
  padding: 28rpx 14rpx;
  background: #f5efdf;
  color: #1d252e;
  border-radius: 28rpx;
  box-shadow: 0 18rpx 48rpx rgba(0, 0, 0, 0.16);
  box-sizing: border-box;
}

.paper-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 184rpx;
  gap: 10rpx;
  align-items: stretch;
}

.header-main {
  min-width: 0;
}

.header-meta-block {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.header-title {
  display: block;
  text-align: center;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.05;
}

.header-meta-line {
  display: grid;
  grid-template-columns: 112rpx minmax(0, 1fr);
  align-items: center;
  gap: 8rpx;
  line-height: 1;
}

.meta-chip {
  min-width: 0;
  padding: 10rpx 12rpx;
  border: 2rpx solid rgba(34, 44, 55, 0.16);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.42);
}

.team-meta-value {
  line-height: 1;
}

.meta-label {
  color: #7e6750;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}

.meta-value {
  min-width: 0;
  color: #1d252e;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
  word-break: keep-all;
}

.meta-value-block {
  display: block;
  margin-top: 8rpx;
}

.score-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 4rpx;
  padding: 4rpx 6rpx;
  border-radius: 20rpx;
  background: linear-gradient(180deg, #ffffff 0%, #ece2ca 100%);
  border: 2rpx solid rgba(34, 44, 55, 0.14);
  box-sizing: border-box;
}

.score-title {
  color: #7a5c40;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1;
}

.score-main {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4rpx;
}

.score-number {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 56rpx;
  font-weight: 700;
  line-height: 0.92;
}

.score-sep {
  font-size: 28rpx;
  font-weight: 700;
  color: #7a5c40;
  line-height: 0.9;
}

.score-sub {
  color: #48614f;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1.2;
}

.game-score-panel {
  margin-top: 8rpx;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8rpx;
}

.game-score-item {
  padding: 6rpx 6rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.48);
  border: 2rpx solid rgba(34, 44, 55, 0.1);
  text-align: center;
}

.game-score-label {
  display: block;
  color: #7b6550;
  font-size: 18rpx;
  line-height: 1.1;
}

.game-score-value {
  display: block;
  margin-top: 2rpx;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.1;
}

.roster-section {
  margin-top: 18rpx;
  padding: 10rpx 12rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.42);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
}

.roster-card {
  padding: 0;
  border-radius: 0;
  background: transparent;
  border: none;
}

.roster-card + .roster-card {
  margin-top: 8rpx;
  padding-top: 8rpx;
  border-top: 1rpx solid rgba(34, 44, 55, 0.08);
}

.roster-card .section-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1.05;
}

.section-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  font-weight: 700;
}

.roster-table {
  margin-top: 6rpx;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1rpx solid rgba(48, 58, 69, 0.14);
  border-radius: 10rpx;
  overflow: hidden;
  background: rgba(244, 239, 226, 0.96);
}

.roster-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0;
}

.roster-cell {
  min-width: 0;
  min-height: 40rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  padding: 2rpx 4rpx;
  background: transparent;
  border-right: 1rpx solid rgba(48, 58, 69, 0.14);
  border-bottom: 1rpx solid rgba(48, 58, 69, 0.14);
  box-sizing: border-box;
}

.roster-row .roster-cell:last-child {
  border-right: none;
}

.roster-row:last-child .roster-cell {
  border-bottom: none;
}

.roster-no {
  font-size: 16rpx;
  font-weight: 800;
  color: #7a5c40;
  line-height: 1.1;
  letter-spacing: -0.4rpx;
}

.roster-name {
  max-width: 100%;
  font-size: 13rpx;
  font-weight: 700;
  text-align: left;
  line-height: 1;
  letter-spacing: -0.8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.games-section {
  margin-top: 11rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.game-entry {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.game-block {
  padding: 10rpx 12rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.44);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
}

.game-block-toss-row {
  display: flex;
  justify-content: flex-start;
}

.game-block-toss {
  display: inline-flex;
  align-self: flex-start;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background: rgba(255, 247, 232, 0.92);
  color: #72573e;
  font-size: 18rpx;
  font-weight: 700;
}

.game-block-body {
  display: flex;
  align-items: stretch;
  gap: 8rpx;
}

.game-block-side {
  width: 44rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.game-block-title-char {
  color: #2f3b47;
  font-size: 21rpx;
  line-height: 1;
  font-weight: 800;
}

.game-block-panels {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  align-items: stretch;
  gap: 16rpx;
}

.rotation-panel,
.timeout-panel {
  min-width: 0;
}

.rotation-panel {
  width: 176rpx;
}

.timeout-panel {
  width: 168rpx;
  margin-top: 0;
  display: flex;
  flex-direction: column;
  align-self: stretch;
}

.rotation-team-label,
.timeout-title {
  display: block;
  margin-bottom: 6rpx;
  color: #72573e;
  font-size: 18rpx;
  font-weight: 700;
}

.rotation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  gap: 0;
  border: 2rpx solid rgba(48, 58, 69, 0.32);
  border-radius: 10rpx;
  overflow: hidden;
  background: rgba(245, 240, 227, 0.98);
}

.rotation-cell {
  position: relative;
  min-height: 56rpx;
  background: transparent;
  border-right: 2rpx solid rgba(48, 58, 69, 0.32);
  border-bottom: 2rpx solid rgba(48, 58, 69, 0.32);
  overflow: hidden;
  box-sizing: border-box;
}

.rotation-cell:nth-child(3n) {
  border-right: none;
}

.rotation-cell:nth-last-child(-n + 3) {
  border-bottom: none;
}

.rotation-cell.slashed::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, transparent 49.2%, rgba(72, 56, 39, 0.55) 50%, transparent 50.8%);
}

.rotation-primary,
.rotation-secondary {
  position: absolute;
  font-size: 16rpx;
  font-weight: 800;
  line-height: 1;
}

.rotation-primary {
  top: 8rpx;
  left: 8rpx;
}

.rotation-cell:not(.slashed) .rotation-primary {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.rotation-secondary {
  right: 8rpx;
  bottom: 10rpx;
}

.rotation-cell.slashed .rotation-primary {
  top: 10rpx;
  left: 8rpx;
}

.rotation-cell.slashed .rotation-secondary {
  right: 8rpx;
  bottom: 8rpx;
}

.timeout-body {
  min-height: 0;
  flex: 1;
  padding: 6rpx 8rpx;
  border-radius: 12rpx;
  background: rgba(245, 240, 227, 0.98);
  border: 1rpx solid rgba(48, 58, 69, 0.16);
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  box-sizing: border-box;
}

.timeout-line {
  min-height: 0;
  font-size: 14rpx;
  font-weight: 700;
  color: #2f3a45;
  line-height: 1.42;
}

.notes-section {
  margin-top: 22rpx;
  padding: 18rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.42);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
}

.notes-text {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.7;
  color: #33414e;
}

.signature-section {
  margin-top: 16rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.signature-column {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.signature-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.signature-label {
  font-size: 18rpx;
  font-weight: 700;
}

.signature-value {
  font-size: 18rpx;
}

@media print {
  .page {
    background: #ffffff;
  }

  .toolbar {
    display: none;
  }

  .page-scroll {
    height: auto;
  }

  .record-shell {
    padding: 0;
  }

  .paper {
    max-width: none;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
