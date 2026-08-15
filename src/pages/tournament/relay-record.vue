<template>
  <view class="page" :style="pageStyle">
    <view v-if="loading" class="state-layer">
      <text class="state-text">正在加载接力赛战报...</text>
    </view>

    <view v-else-if="isError" class="state-layer">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadRecord">重新加载</button>
    </view>

    <scroll-view v-else class="page-scroll" scroll-y>
        <view class="record-shell">
          <view class="top-actions">
            <text class="back-btn" @click="goBack">返回</text>
            <view class="top-actions-right">
              <text v-if="isReportSealed" class="report-status">战报已封存</text>
              <text v-else-if="reportEditAllowed" class="report-status">已获得修改权限</text>
              <button v-else class="auth-btn" @click="openRefereeAuth()">裁判验证</button>
            </view>
          </view>

        <view class="paper">
          <view class="paper-header">
            <text class="paper-title">{{ tournamentName || '接力追分赛战报' }}</text>

            <view class="summary-row">
              <view class="team-block" :class="{ winner: record?.winnerSide === 'left' }">
                <text class="team-label">A队</text>
                <text class="team-name">{{ teamName('left') }}</text>
              </view>

              <view class="score-block">
                <text class="score-label">总比分</text>
                <view class="score-main">
                  <text class="score-number">{{ leftTotalScore }}</text>
                  <text class="score-sep">:</text>
                  <text class="score-number">{{ rightTotalScore }}</text>
                </view>
                <text class="winner-text">{{ winnerText }}</text>
              </view>

              <view class="team-block" :class="{ winner: record?.winnerSide === 'right' }">
                <text class="team-label">B队</text>
                <text class="team-name">{{ teamName('right') }}</text>
              </view>
            </view>

            <view class="basic-info">
              <text class="info-line">比赛类型：接力追分赛</text>
              <text v-if="showStageText" class="info-line">比赛阶段：{{ stageText }}</text>
            </view>
          </view>

          <view class="items-section">
            <view
              v-for="item in reportItems"
              :key="item.itemCode"
              class="item-card"
              :class="{ 'item-card--pending': !segmentScore(item) }"
            >
              <view class="item-head">
                <text class="item-title">{{ segmentTitle(item) }}</text>
              </view>

              <view class="versus-row">
                <view class="member-side" :class="{ winner: segmentWinnerSide(item) === 'left' }">
                  <text class="side-label">A队</text>
                  <text class="member-names">{{ memberNames(item.leftMembers) || '待填写' }}</text>
                </view>

                <view class="center-score">
                  <text class="segment-delta">{{ segmentDeltaText(item) }}</text>
                  <text class="match-score">{{ matchScoreText(item) }}</text>
                </view>

                <view class="member-side" :class="{ winner: segmentWinnerSide(item) === 'right' }">
                  <text class="side-label">B队</text>
                  <text class="member-names">{{ memberNames(item.rightMembers) || '待填写' }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="signature-section">
            <view class="signature-item" @click="openSignature('leftCaptain')">
              <text class="signature-label">A队队长：</text>
              <view class="signature-box" :class="{ 'signature-box--sealed': isReportSealed }">
                <image v-if="leftCaptainSignature" :src="leftCaptainSignature" class="signature-img" mode="aspectFit" />
                <text v-else class="signature-hint">点击签字</text>
              </view>
            </view>

            <view class="signature-item" @click="openSignature('rightCaptain')">
              <text class="signature-label">B队队长：</text>
              <view class="signature-box" :class="{ 'signature-box--sealed': isReportSealed }">
                <image v-if="rightCaptainSignature" :src="rightCaptainSignature" class="signature-img" mode="aspectFit" />
                <text v-else class="signature-hint">点击签字</text>
              </view>
            </view>

            <view class="signature-item" @click="openSignature('referee')">
              <text class="signature-label">裁判：</text>
              <view class="signature-box" :class="{ 'signature-box--sealed': isReportSealed }">
                <image v-if="refereeSignature" :src="refereeSignature" class="signature-img" mode="aspectFit" />
                <text v-else class="signature-hint">点击签字</text>
              </view>
            </view>

            <view class="signature-item signature-item--date" @click="fillTodayDate">
              <text class="signature-label">日期：</text>
              <text class="date-text" :class="{ locked: !!matchDateText }">{{ matchDateText || '点击获取' }}</text>
            </view>
          </view>

          <view v-if="reportComplete && !isReportSealed" class="seal-action">
            <button class="seal-btn" :disabled="sealSaving" @click="promptSealReport(true)">{{ sealSaving ? '封存中...' : reportEditAllowed ? '封存战报' : '验证权限后封存' }}</button>
          </view>
        </view>
      </view>
    </scroll-view>

    <RefereeAuthPopup
      v-model:visible="showRefereeAuth"
      :loading="authLoading"
      title="裁判验证"
      description="请输入裁判密码，验证后可修改和封存战报。"
      confirmText="验证"
      @submit="doRefereeAuth"
      @cancel="clearRefereeAuthContext"
    />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import RefereeAuthPopup from '@/components/RefereeAuthPopup.vue'
import { ensureAuth, guardProfileBeforeAction } from '@/store/auth'
import { request } from '@/utils/request'
import { buildSignatureCaptureUrl, buildSignatureResultEvent, createSignatureEventKey } from '@/utils/signature-capture'
import { navigateToTournamentSchedule } from './tournament-navigation'

function buildBasePortraitPageStyle() {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    const statusBarHeight = Number(info?.statusBarHeight)
    safeTopPx = Number.isFinite(safeInsetTop) && safeInsetTop > 0 ? safeInsetTop : (Number.isFinite(statusBarHeight) ? statusBarHeight : 0)
  } catch (_) {
    // noop
  }
  return { boxSizing: 'border-box', paddingTop: safeTopPx + 'px' }
}

const pageStyle = buildBasePortraitPageStyle()
const matchId = ref('')
const tournamentId = ref('')
const tournamentName = ref('')
const tournamentInfo = ref({})
const stageText = ref('')
const matchDateText = ref('')
const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const record = ref({ leftTeam: {}, rightTeam: {}, items: [] })
const matchRecord = ref({ gameScores: [] })
const reportState = ref({ status: 'draft', sealedAt: '', sealedBy: '' })
const showRefereeAuth = ref(false)
const authLoading = ref(false)
const pendingReportAction = ref('')
const pendingSignTarget = ref('')

const leftCaptainSignature = ref('')
const rightCaptainSignature = ref('')
const refereeSignature = ref('')
const signSaving = ref(false)
const sealSaving = ref(false)
const sealPromptVisible = ref(false)
const sealPromptDismissed = ref(false)

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

const sortedItems = computed(() => {
  const items = Array.isArray(record.value?.items) ? record.value.items : []
  return [...items].sort((a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0))
})

const reportItems = computed(() => sortedItems.value)
const relayScoresBySegment = computed(() => {
  const scores = Array.isArray(matchRecord.value?.gameScores) ? matchRecord.value.gameScores : []
  if (scores.length < reportItems.value.length) {
    return {}
  }
  return scores.reduce((result, score) => {
    const gameNo = Number(score?.gameNo || 0)
    if (gameNo > 0) result[gameNo] = score
    return result
  }, {})
})
const finalRelayScore = computed(() => {
  const scores = Array.isArray(matchRecord.value?.gameScores) ? [...matchRecord.value.gameScores] : []
  scores.sort((left, right) => Number(left?.gameNo || 0) - Number(right?.gameNo || 0))
  return scores.length ? scores[scores.length - 1] : null
})
const leftTotalScore = computed(() => Number(finalRelayScore.value?.leftScore || 0))
const rightTotalScore = computed(() => Number(finalRelayScore.value?.rightScore || 0))
const showStageText = computed(() => Number(record.value?.tournamentType ?? tournamentInfo.value?.tournamentType ?? 0) !== 2 && !!stageText.value)
const isReportSealed = computed(() => cleanText(reportState.value?.status) === 'sealed')
const reportEditAllowed = computed(() => !!tournamentInfo.value?.canOperateMatches && !tournamentInfo.value?.archived)
const reportComplete = computed(() => !!leftCaptainSignature.value && !!rightCaptainSignature.value && !!refereeSignature.value && !!matchDateText.value)

const winnerText = computed(() => {
  const winnerSide = matchRecord.value?.winnerSide || record.value?.winnerSide
  if (winnerSide === 'left') return teamName('left') + ' 获胜'
  if (winnerSide === 'right') return teamName('right') + ' 获胜'
  return '胜方待确认'
})

function teamName(side) {
  const team = side === 'left' ? record.value?.leftTeam : record.value?.rightTeam
  return team?.name || (side === 'left' ? 'A队' : 'B队')
}

function memberNames(members) {
  return Array.isArray(members) ? members.map((member) => member?.name).filter(Boolean).join(' / ') : ''
}

function segmentScore(item) {
  return relayScoresBySegment.value[Number(item?.displayOrder || 0)] || null
}

function segmentTitle(item) {
  const labels = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二']
  const order = Number(item?.displayOrder || 0)
  return '第' + (labels[order - 1] || order) + '赛段'
}

function matchScoreText(item) {
  return segmentScoreText(item)
}

function segmentDeltaText(item) {
  const current = segmentScore(item)
  if (!current) return '--'
  const order = Number(item?.displayOrder || 0)
  const prev = relayScoresBySegment.value[order - 1]
  const leftDelta = Number(current.leftScore || 0) - Number(prev?.leftScore || 0)
  const rightDelta = Number(current.rightScore || 0) - Number(prev?.rightScore || 0)
  return leftDelta + ':' + rightDelta
}

function segmentTarget(item) {
  const order = Math.max(1, Number(item?.displayOrder || 1))
  const base = Number(record.value?.relayBaseScore || matchRecord.value?.pointsToWin || 10)
  const total = Number(record.value?.relayTargetScore || base * reportItems.value.length)
  return Math.min(base * order, total)
}

function segmentWinnerSide(item) {
  const score = segmentScore(item)
  if (!score) return ''
  const target = segmentTarget(item)
  const left = Number(score.leftScore || 0)
  const right = Number(score.rightScore || 0)
  if (left >= target && right < target) return 'left'
  if (right >= target && left < target) return 'right'
  if (left >= target && right >= target) {
    if (left > right) return 'left'
    if (right > left) return 'right'
  }
  return ''
}

function segmentScoreText(item) {
  const score = segmentScore(item)
  if (!score) return '--'
  return Number(score.leftScore || 0) + ':' + Number(score.rightScore || 0)
}

function goBack() {
  navigateToTournamentSchedule({
    pages: typeof getCurrentPages === 'function' ? getCurrentPages() : [],
    tournamentId: tournamentId.value,
    tournamentType: record.value?.tournamentType ?? tournamentInfo.value?.tournamentType,
    uniApi: uni,
  })
}

async function openRefereeAuth(action = '', signTarget = '') {
  if (!(await guardProfileBeforeAction('请先完善个人资料，再进行裁判验证'))) return
  pendingReportAction.value = action
  pendingSignTarget.value = signTarget
  showRefereeAuth.value = true
}

function clearRefereeAuthContext() {
  pendingReportAction.value = ''
  pendingSignTarget.value = ''
}

function runPendingReportAction() {
  const action = pendingReportAction.value
  const signTarget = pendingSignTarget.value
  clearRefereeAuthContext()
  if (action === 'signature' && signTarget) {
    openSignature(signTarget)
    return
  }
  if (action === 'date') {
    fillTodayDate()
    return
  }
  if (action === 'seal') {
    promptSealReport(true)
  }
}

async function doRefereeAuth(password) {
  if (!tournamentId.value) {
    uni.showToast({ title: '缺少赛事ID', icon: 'none' })
    return
  }
  if (!password) {
    uni.showToast({ title: '请输入裁判密码', icon: 'none' })
    return
  }
  authLoading.value = true
  try {
    await ensureAuth()
    await request('/api/v1/tournaments/' + tournamentId.value + '/referee-auth', {
      method: 'POST',
      data: { password },
    })
    await loadTournamentInfo()
    showRefereeAuth.value = false
    uni.showToast({ title: '验证成功', icon: 'success' })
    runPendingReportAction()
  } catch (error) {
    uni.showToast({ title: error?.message || '验证失败', icon: 'none' })
  } finally {
    authLoading.value = false
  }
}

function ensureReportEditable(action = '', signTarget = '') {
  if (reportEditAllowed.value) return true
  openRefereeAuth(action, signTarget)
  return false
}

function groupName(groupNo) {
  const value = Number(groupNo || 0)
  if (value >= 1 && value <= 26) return String.fromCharCode(64 + value) + '组'
  return value > 0 ? '第' + value + '组' : '小组赛'
}

function knockoutStageText(roundNum, knockoutSlots) {
  const slots = Number(knockoutSlots || tournamentInfo.value?.knockoutSlots || 0)
  const round = Math.max(1, Number(roundNum || 1))
  if (!slots) return '淘汰赛'
  const from = Math.max(2, Math.floor(slots / Math.pow(2, round - 1)))
  const to = Math.max(1, Math.floor(from / 2))
  return '淘汰赛' + from + '进' + to
}

function findMatchInGroups(groups) {
  for (const group of Array.isArray(groups) ? groups : []) {
    const found = (Array.isArray(group?.matches) ? group.matches : []).find((match) => match?.id === matchId.value)
    if (found) {
      return { match: found, groupNo: group?.groupNo }
    }
  }
  return null
}

async function loadStageText() {
  const tournamentType = Number(record.value?.tournamentType ?? tournamentInfo.value?.tournamentType ?? 0)
  if (!tournamentId.value || !matchId.value || tournamentType === 2) {
    stageText.value = ''
    return
  }

  if (tournamentType === 1) {
    try {
      const groupData = await request('/api/v1/tournaments/' + tournamentId.value + '/groups', { method: 'GET' })
      const groupMatch = findMatchInGroups(groupData?.groups)
      if (groupMatch) {
        stageText.value = '小组赛' + groupName(groupMatch.groupNo)
        return
      }
    } catch (_) {
      // 找不到小组阶段时继续尝试淘汰赛接口
    }
  }

  try {
    const bracketData = await request('/api/v1/tournaments/' + tournamentId.value + '/bracket', { method: 'GET' })
    const match = (Array.isArray(bracketData?.matches) ? bracketData.matches : []).find((item) => item?.id === matchId.value)
    stageText.value = match ? knockoutStageText(match.roundNum, bracketData?.knockoutSlots) : (tournamentType === 0 ? '淘汰赛' : '')
  } catch (_) {
    stageText.value = tournamentType === 0 ? '淘汰赛' : ''
  }
}

async function fillTodayDate() {
  if (isReportSealed.value) {
    showReportSealedModal()
    return
  }
  if (!matchDateText.value && !ensureReportEditable('date')) {
    return
  }
  if (matchDateText.value) {
    uni.showToast({ title: '日期已确认，不能修改', icon: 'none' })
    return
  }
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const nextDate = year + '-' + month + '-' + day
  try {
    await saveReportSignatures({ matchDateText: nextDate })
    matchDateText.value = nextDate
    promptSealReport()
  } catch (error) {
    uni.showToast({ title: error?.message || '保存日期失败，请重试', icon: 'none' })
  }
}

function applyReportSignatures(signatures) {
  leftCaptainSignature.value = cleanText(signatures?.leftParticipant || signatures?.leftCaptain)
  rightCaptainSignature.value = cleanText(signatures?.rightParticipant || signatures?.rightCaptain)
  refereeSignature.value = cleanText(signatures?.referee)
  matchDateText.value = cleanText(signatures?.matchDateText)
}

function buildReportSignaturePayload(overrides = {}) {
  const left = overrides.leftCaptain ?? leftCaptainSignature.value
  const right = overrides.rightCaptain ?? rightCaptainSignature.value
  const referee = overrides.referee ?? refereeSignature.value
  const matchDate = overrides.matchDateText ?? matchDateText.value
  return {
    teamLeftCaptainSignature: left,
    teamRightCaptainSignature: right,
    teamRefereeSignature: referee,
    teamMatchDateText: matchDate,
    reportLeftParticipantSignature: left,
    reportRightParticipantSignature: right,
    reportRefereeSignature: referee,
    reportMatchDateText: matchDate,
  }
}

async function saveReportSignatures(overrides = {}) {
  await request('/api/v1/matches/' + matchId.value + '/report-meta', {
    method: 'PUT',
    data: buildReportSignaturePayload(overrides),
  })
}

function signatureOverride(target, value) {
  if (target === 'leftCaptain') return { leftCaptain: value }
  if (target === 'rightCaptain') return { rightCaptain: value }
  if (target === 'referee') return { referee: value }
  return {}
}

async function loadTournamentInfo() {
  if (!tournamentId.value) return
  try {
    const data = await request('/api/v1/tournaments/' + tournamentId.value, { method: 'GET' })
    tournamentName.value = data?.name || ''
    tournamentInfo.value = data || {}
  } catch (_) {
    tournamentName.value = ''
    tournamentInfo.value = {}
  }
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
    const [lineupData, recordData] = await Promise.all([
      request('/api/v1/matches/' + matchId.value + '/team-lineup', { method: 'GET' }),
      request('/api/v1/matches/' + matchId.value + '/record', { method: 'GET' }),
    ])
    record.value = lineupData || { leftTeam: {}, rightTeam: {}, items: [] }
    matchRecord.value = recordData || { gameScores: [] }
    reportState.value = lineupData?.reportState || { status: 'draft', sealedAt: '', sealedBy: '' }
    applyReportSignatures(lineupData?.reportSignatures)
    if (!tournamentId.value) tournamentId.value = lineupData?.tournamentId || recordData?.tournamentId || ''
    await loadTournamentInfo()
    await loadStageText()
    promptSealReport()
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载接力赛战报失败'
  } finally {
    loading.value = false
  }
}

function signLabelForTarget(target) {
  if (target === 'leftCaptain') return 'A队队长'
  if (target === 'rightCaptain') return 'B队队长'
  if (target === 'referee') return '裁判'
  return ''
}

function openSignature(target) {
  if (signSaving.value) return
  if (isReportSealed.value) {
    showReportSealedModal()
    return
  }
  if (!ensureReportEditable('signature', target)) {
    return
  }
  if (target === 'leftCaptain' && leftCaptainSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  if (target === 'rightCaptain' && rightCaptainSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  if (target === 'referee' && refereeSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  const eventKey = createSignatureEventKey(target)
  uni.$once(buildSignatureResultEvent(eventKey), ({ dataUrl } = {}) => {
    if (dataUrl) saveSignatureDataUrl(target, dataUrl)
  })
  uni.navigateTo({
    url: buildSignatureCaptureUrl({ eventKey, label: signLabelForTarget(target) }),
  })
}

async function saveSignatureDataUrl(target, dataUrl) {
  if (signSaving.value) return
  signSaving.value = true
  uni.showLoading({ title: '保存中...' })
  try {
    await saveReportSignatures(signatureOverride(target, dataUrl))
    if (target === 'leftCaptain') leftCaptainSignature.value = dataUrl
    if (target === 'rightCaptain') rightCaptainSignature.value = dataUrl
    if (target === 'referee') refereeSignature.value = dataUrl
    uni.hideLoading()
    signSaving.value = false
    uni.showToast({ title: '签名已确认', icon: 'success' })
    promptSealReport()
  } catch (error) {
    uni.hideLoading()
    signSaving.value = false
    uni.showToast({ title: error?.message || '保存签名失败，请重试', icon: 'none' })
  }
}

function showReportSealedModal() {
  uni.showModal({
    title: '战报已封存',
    content: '签名和日期已锁定，不能再修改。',
    showCancel: false,
    confirmText: '知道了',
  })
}

function promptSealReport(manual = false) {
  if (!reportComplete.value || isReportSealed.value || sealSaving.value || sealPromptVisible.value) return
  if (!reportEditAllowed.value) {
    if (manual) {
      openRefereeAuth('seal')
    }
    return
  }
  if (!manual && sealPromptDismissed.value) return

  sealPromptVisible.value = true
  uni.showModal({
    title: '战报信息已完成',
    content: '所有签名和日期已登记，是否封存战报？封存后不能再修改。',
    confirmText: '封存',
    cancelText: '暂不',
    success: async (res) => {
      sealPromptVisible.value = false
      if (!res.confirm) {
        sealPromptDismissed.value = true
        return
      }
      await sealReport()
    },
    fail: () => {
      sealPromptVisible.value = false
    },
  })
}

async function sealReport() {
  if (sealSaving.value) return
  sealSaving.value = true
  try {
    await request('/api/v1/matches/' + matchId.value + '/report-seal', { method: 'PUT' })
    reportState.value = { ...(reportState.value || {}), status: 'sealed' }
    uni.showToast({ title: '战报已封存', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error?.message || '封存战报失败，请重试', icon: 'none' })
  } finally {
    sealSaving.value = false
  }
}

onLoad((options) => {
  matchId.value = options?.matchId || ''
  tournamentId.value = options?.tournamentId || ''
  loadRecord()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top, rgba(255, 179, 71, 0.16), transparent 25%),
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

.retry-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border: none;
  border-radius: 14rpx;
  background: #ffb347;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 800;
}

.retry-btn::after,
.seal-btn::after {
  border: none;
}

.page-scroll {
  height: 100vh;
}

.record-shell {
  padding: 18rpx 12rpx 40rpx;
  box-sizing: border-box;
}

.top-actions {
  max-width: 930rpx;
  margin: 0 auto 16rpx;
  padding: 18rpx 12rpx 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.top-actions-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 700;
}

.auth-btn {
  height: 48rpx;
  line-height: 48rpx;
  padding: 0 20rpx;
  border: none;
  border-radius: 999rpx;
  background: rgba(255, 179, 71, 0.16);
  color: #ffb347;
  font-size: 22rpx;
  font-weight: 700;
}

.auth-btn::after {
  border: none;
}

.report-status {
  color: rgba(255, 255, 255, 0.82);
  font-size: 24rpx;
  font-weight: 700;
}

.paper {
  width: 100%;
  max-width: 930rpx;
  margin: 0 auto;
  padding: 28rpx 18rpx 32rpx;
  background: #f5efdf;
  color: #1d252e;
  border-radius: 28rpx;
  box-shadow: 0 18rpx 48rpx rgba(0, 0, 0, 0.16);
  box-sizing: border-box;
}

.paper-title,
.team-label,
.team-name,
.score-label,
.winner-text,
.info-line,
.item-title,
.side-label,
.member-names,
.match-score,
.small-score,
.signature-label,
.date-text {
  display: block;
}

.paper-title {
  text-align: center;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 42rpx;
  font-weight: 800;
  line-height: 1.35;
}

.summary-row {
  margin-top: 22rpx;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190rpx minmax(0, 1fr);
  align-items: stretch;
  gap: 12rpx;
}

.team-block {
  min-width: 0;
  padding: 16rpx 14rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.44);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
  text-align: center;
}

.team-block.winner {
  border-color: rgba(72, 97, 79, 0.42);
  background: rgba(232, 241, 226, 0.9);
}

.team-label,
.score-label,
.side-label {
  color: #7a5c40;
  font-size: 20rpx;
  font-weight: 700;
}

.team-name {
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.score-block {
  padding: 12rpx 8rpx;
  border-radius: 20rpx;
  background: linear-gradient(180deg, #ffffff 0%, #ece2ca 100%);
  border: 2rpx solid rgba(34, 44, 55, 0.14);
  text-align: center;
}

.score-main {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4rpx;
  margin-top: 4rpx;
}

.score-number {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 56rpx;
  font-weight: 800;
  line-height: 0.95;
}

.score-sep {
  color: #7a5c40;
  font-size: 28rpx;
  font-weight: 800;
}

.winner-text {
  margin-top: 6rpx;
  color: #48614f;
  font-size: 20rpx;
  font-weight: 800;
  line-height: 1.2;
}

.basic-info {
  margin-top: 18rpx;
  padding: 14rpx 18rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.38);
  border: 2rpx solid rgba(34, 44, 55, 0.1);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.info-line {
  color: #33414e;
  font-size: 23rpx;
  font-weight: 650;
}

.items-section {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.item-card {
  padding: 12rpx 16rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.44);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
}

.item-card--pending {
  opacity: 0.82;
}

.item-head {
  position: relative;
  min-height: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-strip {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-title {
  position: absolute;
  left: 0;
  top: 50%;
  max-width: 260rpx;
  transform: translateY(-50%);
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.2;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.versus-row {
  margin-top: 6rpx;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) 124rpx minmax(0, 1.15fr);
  align-items: center;
  gap: 6rpx;
}

.member-side {
  min-width: 0;
  padding: 8rpx 10rpx;
  border-radius: 14rpx;
  background: rgba(245, 240, 227, 0.82);
  border: 1rpx solid rgba(48, 58, 69, 0.12);
  text-align: center;
}

.member-side.winner {
  border-color: rgba(72, 97, 79, 0.42);
  background: rgba(232, 241, 226, 0.9);
}

.member-names {
  margin-top: 3rpx;
  min-height: 34rpx;
  font-size: 27rpx;
  font-weight: 750;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.center-score {
  text-align: center;
}

.segment-delta {
  display: block;
  color: #7a5c40;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.15;
  text-align: center;
  white-space: nowrap;
}

.match-score {
  color: #1d252e;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.2;
}

.small-score {
  min-height: 30rpx;
  white-space: nowrap;
  color: #7a5c40;
  font-size: 19rpx;
  font-weight: 700;
  line-height: 1.25;
  text-align: center;
}

.signature-section {
  margin-top: 22rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.signature-item {
  display: grid;
  grid-template-columns: 132rpx minmax(0, 1fr);
  align-items: center;
  gap: 8rpx;
}

.signature-item--date {
  align-self: center;
}

.signature-label {
  text-align: right;
  font-size: 22rpx;
  font-weight: 800;
}

.signature-box {
  aspect-ratio: 3 / 1;
  min-height: 76rpx;
  border-radius: 12rpx;
  border: 2rpx dashed rgba(34, 44, 55, 0.2);
  background: rgba(255, 255, 255, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.signature-box--sealed {
  border-color: transparent;
  background: transparent;
}

.date-text {
  color: #1d252e;
  font-size: 22rpx;
  font-weight: 800;
  line-height: 1.2;
  text-align: left;
}

.date-text.locked {
  font-weight: 700;
}

.signature-img {
  width: 100%;
  height: 100%;
}

.signature-hint {
  color: #aa9984;
  font-size: 20rpx;
}

.seal-action {
  margin-top: 20rpx;
  display: flex;
  justify-content: flex-end;
}

.seal-btn {
  width: 220rpx;
  height: 68rpx;
  line-height: 68rpx;
  border: none;
  border-radius: 12rpx;
  background: #48614f;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 800;
}

.seal-btn[disabled] {
  opacity: 0.62;
}
</style>
