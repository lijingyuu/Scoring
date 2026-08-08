<template>
  <view class="page" :style="pageStyle">
    <view v-if="loading" class="state-layer">
      <text class="state-text">正在加载个人赛战报...</text>
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
            <text class="paper-title">{{ tournamentName || record?.tournamentName || '羽毛球个人赛战报' }}</text>

            <view class="summary-row">
              <view class="player-block" :class="{ winner: record?.winnerSide === 'left' }">
                <text class="player-label">左方选手</text>
                <text class="player-name">{{ playerName('left') }}</text>
              </view>

              <view class="score-block">
                <text class="score-label">总比分</text>
                <view class="score-main">
                  <text class="score-number">{{ leftGameWins }}</text>
                  <text class="score-sep">:</text>
                  <text class="score-number">{{ rightGameWins }}</text>
                </view>
                <text class="winner-text">{{ winnerText }}</text>
              </view>

              <view class="player-block" :class="{ winner: record?.winnerSide === 'right' }">
                <text class="player-label">右方选手</text>
                <text class="player-name">{{ playerName('right') }}</text>
              </view>
            </view>

            <view class="basic-info">
              <text class="info-line">比赛类型：羽毛球个人赛</text>
              <text v-if="showStageText" class="info-line">比赛阶段：{{ stageText }}</text>
              <text class="info-line">赛制：{{ ruleText }}</text>
            </view>
          </view>

          <view class="games-section">
            <view v-if="!gameScores.length" class="empty-games">
              <text class="empty-text">{{ record?.scoreDisplay || '暂无局分记录' }}</text>
            </view>

            <view
              v-for="game in gameScores"
              :key="game.gameNo"
              class="game-card"
              :class="{ 'game-card--pending': !game.winnerSide }"
            >
              <view class="game-head">
                <text class="game-title">第{{ game.gameNo }}局</text>
              </view>

              <view class="versus-row">
                <view class="player-side" :class="{ winner: game.winnerSide === 'left' }">
                  <text class="side-name">{{ playerName('left') }}</text>
                </view>

                <view class="center-score">
                  <text class="game-score">{{ gameScoreText(game) }}</text>
                </view>

                <view class="player-side" :class="{ winner: game.winnerSide === 'right' }">
                  <text class="side-name">{{ playerName('right') }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="signature-section">
            <view class="signature-item" @click="openSignature('leftPlayer')">
              <text class="signature-label">左方选手：</text>
              <view class="signature-box" :class="{ 'signature-box--sealed': isReportSealed }">
                <image v-if="leftPlayerSignature" :src="leftPlayerSignature" class="signature-img" mode="aspectFit" />
                <text v-else class="signature-hint">点击签字</text>
              </view>
            </view>

            <view class="signature-item" @click="openSignature('rightPlayer')">
              <text class="signature-label">右方选手：</text>
              <view class="signature-box" :class="{ 'signature-box--sealed': isReportSealed }">
                <image v-if="rightPlayerSignature" :src="rightPlayerSignature" class="signature-img" mode="aspectFit" />
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

    <view v-if="currentSignTarget" class="sign-overlay" @touchmove.stop.prevent="() => {}">
      <view class="sign-overlay-mask" @click="cancelSignature" />
      <view class="sign-panel">
        <view class="sign-panel-header">
          <text class="sign-panel-title">请{{ signLabel }}签字</text>
          <text class="sign-panel-close" @click="cancelSignature">×</text>
        </view>
        <view class="sign-canvas-wrapper">
          <canvas
            canvas-id="individualRecordSignCanvas"
            id="individualRecordSignCanvas"
            class="sign-canvas"
            disable-scroll="true"
            @touchstart="onSignTouchStart"
            @touchmove="onSignTouchMove"
            @touchend="onSignTouchEnd"
          />
        </view>
        <view class="sign-panel-actions">
          <button class="sign-btn sign-btn--clear" :disabled="signSaving" @click="clearSignature">清空</button>
          <button class="sign-btn sign-btn--confirm" :disabled="signSaving" @click="confirmSignature">{{ signSaving ? '保存中...' : '确认' }}</button>
        </view>
      </view>
    </view>

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
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import RefereeAuthPopup from '@/components/RefereeAuthPopup.vue'
import { ensureAuth, guardProfileBeforeAction } from '@/store/auth'
import { request } from '@/utils/request'
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
const record = ref({ left: {}, right: {}, gameScores: [] })
const reportState = ref({ status: 'draft', sealedAt: '', sealedBy: '' })
const showRefereeAuth = ref(false)
const authLoading = ref(false)
const pendingReportAction = ref('')
const pendingSignTarget = ref('')

const currentSignTarget = ref('')
const leftPlayerSignature = ref('')
const rightPlayerSignature = ref('')
const refereeSignature = ref('')
const signCtx = ref(null)
const signDrawing = ref(false)
const signSaving = ref(false)
const sealSaving = ref(false)
const sealPromptVisible = ref(false)
const sealPromptDismissed = ref(false)
const signCanvasSize = ref(null)
const strokes = ref([])
const currentStroke = ref([])

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

const gameScores = computed(() => {
  const scores = Array.isArray(record.value?.gameScores) ? record.value.gameScores : []
  return [...scores]
    .filter((game) => Number(game?.gameNo || 0) > 0)
    .sort((a, b) => Number(a.gameNo || 0) - Number(b.gameNo || 0))
})
const leftGameWins = computed(() => Number(record.value?.leftGameWins || gameScores.value.filter((game) => game.winnerSide === 'left').length || 0))
const rightGameWins = computed(() => Number(record.value?.rightGameWins || gameScores.value.filter((game) => game.winnerSide === 'right').length || 0))
const showStageText = computed(() => Number(tournamentInfo.value?.tournamentType ?? 0) !== 2 && !!stageText.value)
const isReportSealed = computed(() => cleanText(reportState.value?.status) === 'sealed')
const reportEditAllowed = computed(() => !!tournamentInfo.value?.canOperateMatches && !tournamentInfo.value?.archived)
const reportComplete = computed(() => !!leftPlayerSignature.value && !!rightPlayerSignature.value && !!refereeSignature.value && !!matchDateText.value)

const winnerText = computed(() => {
  if (record.value?.winnerSide === 'left') return playerName('left') + ' 获胜'
  if (record.value?.winnerSide === 'right') return playerName('right') + ' 获胜'
  return '胜方待确认'
})

const ruleText = computed(() => {
  const bestOf = Number(record.value?.bestOf || 0)
  const gamesToWin = Number(record.value?.gamesToWin || 0)
  const pointsToWin = Number(record.value?.pointsToWin || 0)
  const capPoint = Number(record.value?.capPoint || 0)
  const matchText = bestOf && gamesToWin ? `${bestOf}局${gamesToWin}胜` : '按赛程规则'
  const pointText = pointsToWin ? `${pointsToWin}分` : ''
  const deuceText = record.value?.enableDeuce === false ? '无追分' : (capPoint ? `${capPoint}分封顶` : '')
  return [matchText, pointText, deuceText].filter(Boolean).join(' / ')
})

const signLabel = computed(() => {
  if (currentSignTarget.value === 'leftPlayer') return '左方选手'
  if (currentSignTarget.value === 'rightPlayer') return '右方选手'
  if (currentSignTarget.value === 'referee') return '裁判'
  return ''
})

function playerName(side) {
  const player = side === 'left' ? record.value?.left : record.value?.right
  return player?.name || (side === 'left' ? '左方选手' : '右方选手')
}

function gameScoreText(game) {
  if (game?.leftScore == null || game?.rightScore == null) return '--'
  return Number(game.leftScore || 0) + ':' + Number(game.rightScore || 0)
}

function goBack() {
  navigateToTournamentSchedule({
    pages: typeof getCurrentPages === 'function' ? getCurrentPages() : [],
    tournamentId: tournamentId.value,
    tournamentType: tournamentInfo.value?.tournamentType,
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
  const tournamentType = Number(tournamentInfo.value?.tournamentType ?? 0)
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
  leftPlayerSignature.value = cleanText(signatures?.leftParticipant || signatures?.leftCaptain)
  rightPlayerSignature.value = cleanText(signatures?.rightParticipant || signatures?.rightCaptain)
  refereeSignature.value = cleanText(signatures?.referee)
  matchDateText.value = cleanText(signatures?.matchDateText)
}

function buildReportSignaturePayload(overrides = {}) {
  const left = overrides.leftPlayer ?? leftPlayerSignature.value
  const right = overrides.rightPlayer ?? rightPlayerSignature.value
  const referee = overrides.referee ?? refereeSignature.value
  const matchDate = overrides.matchDateText ?? matchDateText.value
  return {
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

function tempFileToDataUrl(tempFilePath) {
  const filePath = cleanText(tempFilePath)
  if (!filePath) return Promise.reject(new Error('签名图片为空'))
  if (/^data:image\//.test(filePath)) return Promise.resolve(filePath)
  if (typeof uni.getFileSystemManager !== 'function') {
    return Promise.reject(new Error('当前环境不支持保存签名'))
  }
  return new Promise((resolve, reject) => {
    uni.getFileSystemManager().readFile({
      filePath,
      encoding: 'base64',
      success: (res) => resolve('data:image/png;base64,' + res.data),
      fail: () => reject(new Error('读取签名失败，请重试')),
    })
  })
}

function signatureOverride(target, value) {
  if (target === 'leftPlayer') return { leftPlayer: value }
  if (target === 'rightPlayer') return { rightPlayer: value }
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
    const data = await request('/api/v1/matches/' + matchId.value + '/record', { method: 'GET' })
    record.value = data || { left: {}, right: {}, gameScores: [] }
    reportState.value = data?.reportMeta?.reportState || { status: 'draft', sealedAt: '', sealedBy: '' }
    applyReportSignatures(data?.reportMeta?.reportSignatures)
    if (!tournamentId.value) tournamentId.value = data?.tournamentId || ''
    await loadTournamentInfo()
    await loadStageText()
    promptSealReport()
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载个人赛战报失败'
  } finally {
    loading.value = false
  }
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
  if (target === 'leftPlayer' && leftPlayerSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  if (target === 'rightPlayer' && rightPlayerSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  if (target === 'referee' && refereeSignature.value) {
    uni.showToast({ title: '签名已确认，不能修改', icon: 'none' })
    return
  }
  currentSignTarget.value = target
  strokes.value = []
  currentStroke.value = []
  nextTick(() => initSignCanvas())
}

function initSignCanvas() {
  const ctx = uni.createCanvasContext('individualRecordSignCanvas')
  ctx.setStrokeStyle('#1d252e')
  ctx.setLineWidth(4)
  ctx.setLineCap('round')
  ctx.setLineJoin('round')
  signCtx.value = ctx

  uni.createSelectorQuery()
    .select('#individualRecordSignCanvas')
    .boundingClientRect()
    .exec((res) => {
      if (res && res[0]) {
        signCanvasSize.value = { width: res[0].width, height: res[0].height }
      }
    })
}

function canvasPoint(touch) {
  if (touch && typeof touch.x === 'number' && typeof touch.y === 'number') {
    return { x: touch.x, y: touch.y }
  }
  return { x: 0, y: 0 }
}

function onSignTouchStart(e) {
  signDrawing.value = true
  const ctx = signCtx.value
  if (!ctx) return
  const pt = canvasPoint(e.touches[0] || {})
  currentStroke.value = [pt]
  ctx.beginPath()
  ctx.arc(pt.x, pt.y, 2, 0, Math.PI * 2)
  ctx.setFillStyle('#1d252e')
  ctx.fill()
  ctx.draw(true)
}

function onSignTouchMove(e) {
  if (!signDrawing.value) return
  const ctx = signCtx.value
  if (!ctx) return
  const pt = canvasPoint(e.touches[0] || {})
  const pts = currentStroke.value
  if (!pts.length) return
  const last = pts[pts.length - 1]
  const dist = Math.sqrt((pt.x - last.x) ** 2 + (pt.y - last.y) ** 2)
  if (dist < 1.5) return
  pts.push(pt)
  ctx.beginPath()
  ctx.moveTo(last.x, last.y)
  ctx.lineTo(pt.x, pt.y)
  ctx.stroke()
  ctx.draw(true)
}

function onSignTouchEnd() {
  if (!signDrawing.value) return
  signDrawing.value = false
  if (currentStroke.value.length) {
    strokes.value.push([...currentStroke.value])
    currentStroke.value = []
  }
}

function redrawStrokes(callback) {
  const ctx = signCtx.value
  if (!ctx) return
  const size = signCanvasSize.value
  ctx.clearRect(0, 0, size ? size.width : 9999, size ? size.height : 9999)
  ctx.setStrokeStyle('#1d252e')
  ctx.setLineWidth(4)
  ctx.setLineCap('round')
  ctx.setLineJoin('round')
  strokes.value.forEach((stroke) => {
    if (!stroke.length) return
    ctx.beginPath()
    ctx.moveTo(stroke[0].x, stroke[0].y)
    for (let i = 1; i < stroke.length; i += 1) {
      ctx.lineTo(stroke[i].x, stroke[i].y)
    }
    ctx.stroke()
  })
  ctx.draw(false, callback)
}

function clearSignature() {
  const ctx = signCtx.value
  const size = signCanvasSize.value
  strokes.value = []
  currentStroke.value = []
  if (ctx) {
    ctx.clearRect(0, 0, size ? size.width : 9999, size ? size.height : 9999)
    ctx.draw()
  }
}

function cancelSignature() {
  if (signSaving.value) return
  currentSignTarget.value = ''
  signCtx.value = null
  signDrawing.value = false
  strokes.value = []
  currentStroke.value = []
}

function confirmSignature() {
  if (signSaving.value) return
  if (!strokes.value.length) {
    uni.showToast({ title: '签名内容为空，请先绘制', icon: 'none' })
    return
  }
  signSaving.value = true
  const ctx = signCtx.value
  if (!ctx) {
    signSaving.value = false
    uni.showToast({ title: '保存签名失败，请重试', icon: 'none' })
    return
  }
  uni.showLoading({ title: '保存中...' })
  redrawStrokes(() => saveSignatureImage())
}

function saveSignatureImage() {
  const size = signCanvasSize.value || {}
  uni.canvasToTempFilePath({
    canvasId: 'individualRecordSignCanvas',
    fileType: 'png',
    quality: 1,
    destWidth: Math.max(1, Math.round(size.width || 300)),
    destHeight: Math.max(1, Math.round(size.height || 160)),
    success: async (res) => {
      const target = currentSignTarget.value
      try {
        const dataUrl = await tempFileToDataUrl(res.tempFilePath)
        await saveReportSignatures(signatureOverride(target, dataUrl))
        if (target === 'leftPlayer') leftPlayerSignature.value = dataUrl
        if (target === 'rightPlayer') rightPlayerSignature.value = dataUrl
        if (target === 'referee') refereeSignature.value = dataUrl
        signSaving.value = false
        uni.hideLoading()
        cancelSignature()
        uni.showToast({ title: '签名已确认', icon: 'success' })
        promptSealReport()
      } catch (error) {
        signSaving.value = false
        uni.hideLoading()
        uni.showToast({ title: error?.message || '保存签名失败，请重试', icon: 'none' })
      }
    },
    fail: () => {
      signSaving.value = false
      uni.hideLoading()
      uni.showToast({ title: '保存签名失败，请重试', icon: 'none' })
    },
  })
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
.sign-btn::after,
.seal-btn::after,
.auth-btn::after {
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
.player-label,
.player-name,
.score-label,
.winner-text,
.info-line,
.game-title,
.side-label,
.side-name,
.game-score,
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

.player-block {
  min-width: 0;
  padding: 16rpx 14rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.44);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
  text-align: center;
}

.player-block.winner,
.player-side.winner {
  border-color: rgba(72, 97, 79, 0.42);
  background: rgba(232, 241, 226, 0.9);
}

.player-label,
.score-label,
.side-label {
  color: #7a5c40;
  font-size: 20rpx;
  font-weight: 700;
}

.player-name {
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

.games-section {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.game-card,
.empty-games {
  padding: 12rpx 16rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.44);
  border: 2rpx solid rgba(34, 44, 55, 0.12);
}

.game-card--pending {
  opacity: 0.82;
}

.game-head {
  position: relative;
  min-height: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.game-title {
  position: absolute;
  left: 0;
  top: 50%;
  max-width: 180rpx;
  transform: translateY(-50%);
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.2;
  text-align: left;
}

.versus-row {
  margin-top: 6rpx;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) 124rpx minmax(0, 1.15fr);
  align-items: center;
  gap: 6rpx;
}

.player-side {
  min-width: 0;
  padding: 8rpx 10rpx;
  border-radius: 14rpx;
  background: rgba(245, 240, 227, 0.82);
  border: 1rpx solid rgba(48, 58, 69, 0.12);
  text-align: center;
}

.side-name {
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

.game-score {
  color: #1d252e;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.2;
}

.empty-text {
  display: block;
  color: #7a5c40;
  font-size: 24rpx;
  font-weight: 700;
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
  height: 76rpx;
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

.sign-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sign-overlay-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.72);
}

.sign-panel {
  position: relative;
  z-index: 1;
  width: calc(100vw - 24rpx);
  max-width: calc(100vw - 24rpx);
  background: #ffffff;
  border-radius: 28rpx;
  overflow: hidden;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.35);
}

.sign-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx 16rpx;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.08);
}

.sign-panel-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1d252e;
}

.sign-panel-close {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  color: #999999;
}

.sign-canvas-wrapper {
  margin: 16rpx 28rpx;
  border-radius: 16rpx;
  overflow: hidden;
  border: 2rpx solid rgba(0, 0, 0, 0.1);
  background: #ffffff;
}

.sign-canvas {
  width: 100%;
  height: 220rpx;
  display: block;
}

.sign-panel-actions {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 28rpx 28rpx;
}

.sign-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border: none;
  border-radius: 18rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.sign-btn--clear {
  background: rgba(0, 0, 0, 0.06);
  color: #666666;
}

.sign-btn--confirm {
  background: #ffb347;
  color: #13202d;
}
</style>
