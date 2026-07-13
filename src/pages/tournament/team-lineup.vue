<template>
  <view class="page" :style="pageStyle">
    <view class="state-layer" v-if="loading">
      <text class="state-text">{{ t.loading }}</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ t.loadFailed }}</text>
      <button class="retry-btn" @click="fetchLineup">{{ t.retry }}</button>
    </view>

    <template v-else>
      <view class="header">
        <view class="header-top">
          <text class="back-btn safe-back-btn" @click="handleBack">{{ t.back }}</text>
          <view class="header-main">
            <text class="title">{{ setupPage === 'main' ? t.title : currentTeamName + t.lineupSuffix }}</text>
            <text class="subtitle" v-if="setupPage !== 'main'">{{ currentProgressText }}</text>
          </view>
        </view>
        <text class="hint">{{ setupPage === 'main' ? t.mainHint : t.editorHint }}</text>
      </view>

      <scroll-view class="content" scroll-y>
        <view v-if="setupPage === 'main'" class="main-panel">
          <view class="entry-list">
            <view class="entry-card" @click="openEditor('left')">
              <view class="entry-main">
                <text class="entry-title">{{ teamName('left') }}{{ t.lineupSuffix }}</text>
                <text class="entry-desc">{{ t.privateHint }}</text>
              </view>
              <text class="entry-meta" :class="{ complete: sideComplete('left') }">{{ sideProgressText('left') }}</text>
            </view>
            <view class="entry-card" @click="openEditor('right')">
              <view class="entry-main">
                <text class="entry-title">{{ teamName('right') }}{{ t.lineupSuffix }}</text>
                <text class="entry-desc">{{ t.privateHint }}</text>
              </view>
              <text class="entry-meta" :class="{ complete: sideComplete('right') }">{{ sideProgressText('right') }}</text>
            </view>
          </view>
        </view>

          <view v-else class="editor-panel">
          <view class="slot-list" v-if="isRelayTemplate">
            <view class="slot-row" v-for="(_, index) in relaySlotIndexes" :key="'relay_' + index">
              <view class="slot-label">
                <text class="item-name">第 {{ index + 1 }} 位</text>
                <text class="item-rule">自动生成第 {{ index + 1 }} 段与下一位搭档</text>
              </view>
              <view class="slot-group">
                <view
                  class="lineup-slot relay-slot"
                  :class="{ active: activeRelayIndex === index, filled: !!relayMemberId(index) }"
                  @click="setActiveRelaySlot(index)"
                >
                  <text class="slot-pos">顺序</text>
                  <text class="slot-name">{{ relayMemberName(index) }}</text>
                </view>
              </view>
            </view>
          </view>
          <view class="slot-list" v-else>
            <view class="slot-row" v-for="item in sortedItems" :key="item.itemCode">
              <view class="slot-label">
                <text class="item-name">{{ item.itemName || item.itemCode }}</text>
                <text class="item-rule">{{ item.playerCount === 1 ? t.singleRule : t.doubleRule }}</text>
              </view>
              <view class="slot-group">
                <view
                  class="lineup-slot"
                  :class="{ active: isActiveSlot(item, index), filled: !!slotMemberId(item, index) }"
                  v-for="(_, index) in slotIndexes(item)"
                  :key="item.itemCode + '_' + index"
                  @click="setActiveSlot(item.itemCode, index)"
                >
                  <text class="slot-pos">{{ slotTitle(item, index) }}</text>
                  <text class="slot-name">{{ slotMemberName(item, index) }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="roster-panel">
            <view class="roster-head">
              <text class="roster-title">{{ t.roster }}</text>
              <text class="roster-tip">{{ activeSlotText }}</text>
            </view>
            <view class="roster-grid">
              <view
                class="roster-member"
                :class="{ selected: rosterMemberSelected(member.id), active: rosterMemberActive(member.id) }"
                v-for="member in currentMembers"
                :key="member.id"
                @click="pickMember(member)"
              >
                <text class="member-name">{{ member.name }}</text>
                <text class="captain-tag" v-if="member.captain">{{ t.captain }}</text>
              </view>
            </view>
            <view class="draft-empty" v-if="!currentMembers.length">{{ t.noMembers }}</view>
          </view>
        </view>
      </scroll-view>

      <view class="footer-bar" v-if="setupPage === 'main'">
        <button class="ghost-btn" @click="clearLineup">{{ t.clearAll }}</button>
        <button class="primary-btn" :loading="submitting" :disabled="submitting" @click="saveLineup">{{ isRelayTemplate ? '确认开始比赛' : t.save }}</button>
      </view>
      <view class="footer-bar" v-else>
        <button class="ghost-btn" @click="clearCurrentSide">{{ t.clearCurrent }}</button>
        <button class="primary-btn" @click="backToMain">{{ t.done }}</button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { useActionLock } from '@/utils/interaction-guard'
import { navigateToExistingMatchPage } from './tournament-navigation'

function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === 'function'
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop
    } else {
      const statusBarHeight = Number(info?.statusBarHeight)
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) safeTopPx = statusBarHeight
    }
  } catch (_) {
    // noop
  }

  let extraTopPx = 0
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2)
    try {
      if (typeof uni?.upx2px === 'function') {
        const px = Number(uni.upx2px(extraTopRpx))
        if (Number.isFinite(px) && px > 0) extraTopPx = px
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: 'border-box',
    paddingTop: safeTopPx + extraTopPx + 'px',
  }
}

const t = {
  loading: '正在获取团体赛对阵名单...',
  loadFailed: '对阵名单加载失败',
  retry: '\u91cd\u65b0\u52a0\u8f7d',
  back: '\u8fd4\u56de',
  title: '团体赛对阵名单',
  mainHint: '分别进入两队对阵名单页面，单队填写时不展示对方具体安排。',
  editorHint: '\u5148\u70b9\u9009\u4e0a\u65b9\u4f4d\u7f6e\uff0c\u518d\u70b9\u4e0b\u65b9\u961f\u5458\u586b\u5165\u3002',
  privateHint: '点击进入本队对阵名单',
  leftTeam: '\u5de6\u961f',
  rightTeam: '\u53f3\u961f',
  lineupSuffix: '对阵名单',
  singleRule: '\u5355\u6253\uff1a1 \u4eba',
  doubleRule: '\u53cc\u6253\uff1a2 \u4eba',
  roster: '\u961f\u5458\u5217\u8868',
  captain: '\u961f\u957f',
  clearAll: '\u6e05\u7a7a\u5168\u90e8',
  clearCurrent: '\u6e05\u7a7a\u672c\u961f',
  save: '保存对阵名单',
  done: '\u5b8c\u6210',
  unknownTeam: '\u672a\u77e5\u961f\u4f0d',
  missingMatchId: '\u7f3a\u5c11\u6bd4\u8d5bID',
  chooseSlot: '\u8bf7\u5148\u9009\u62e9\u4e00\u4e2a\u4f4d\u7f6e',
  duplicated: '\u540c\u4e00\u9879\u76ee\u4e0d\u80fd\u91cd\u590d\u9009\u4eba',
  saved: '对阵名单已保存',
  incomplete: '\u8bf7\u8865\u5168\u4e24\u961f\u6240\u6709\u9879\u76ee\u7684\u51fa\u573a\u961f\u5458',
  pending: '\u5f85\u8865\u5168',
  noMembers: '\u5f53\u524d\u6ca1\u6709\u53ef\u9009\u961f\u5458',
  emptySlot: '\u5f85\u9009',
}

const pageStyle = buildBasePortraitPageStyle()
const matchId = ref('')
const tournamentId = ref('')
const lineup = ref({ leftTeam: {}, rightTeam: {}, items: [] })
const loading = ref(true)
const isError = ref(false)
const submitting = ref(false)
const setupPage = ref('main')
const editingSide = ref('left')
const activeSlot = ref({ itemCode: 'MS', index: 0 })
const activeRelayIndex = ref(0)
const relayOrders = ref({ left: [], right: [] })
const { begin: beginNav, run: runAction } = useActionLock(500)

const sortedItems = computed(() => {
  const items = Array.isArray(lineup.value.items) ? lineup.value.items : []
  return [...items].sort((a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0))
})
const leftMembers = computed(() => normalizeMembers(lineup.value.leftTeam?.members))
const rightMembers = computed(() => normalizeMembers(lineup.value.rightTeam?.members))
const currentMembers = computed(() => (editingSide.value === 'left' ? leftMembers.value : rightMembers.value))
const currentTeamName = computed(() => teamName(editingSide.value))
const currentProgressText = computed(() => sideProgressText(editingSide.value))
const isRelayTemplate = computed(() => Number(lineup.value?.teamMatchTemplate || 0) === 2)
const relayMemberCount = computed(() => Number(lineup.value?.relayMemberCount || 6))
const relaySlotIndexes = computed(() => Array.from({ length: relayMemberCount.value }))
const activeSlotText = computed(() => {
  if (isRelayTemplate.value) return '第 ' + (activeRelayIndex.value + 1) + ' 位'
  const item = findItem(activeSlot.value.itemCode)
  if (!item) return t.chooseSlot
  return (item.itemName || item.itemCode) + ' ' + slotTitle(item, activeSlot.value.index)
})

function normalizeMembers(members) {
  return Array.isArray(members) ? members.filter((member) => member?.id) : []
}

function normalizeLineup(data) {
  const items = Array.isArray(data?.items) ? data.items : []
  return {
    ...data,
    leftTeam: data?.leftTeam || {},
    rightTeam: data?.rightTeam || {},
    items: items.map((item) => ({
      ...item,
      leftMemberIds: normalizeMembers(item.leftMembers).map((member) => member.id),
      rightMemberIds: normalizeMembers(item.rightMembers).map((member) => member.id),
    })),
  }
}

function hydrateRelayOrders() {
  if (!isRelayTemplate.value) return
  const items = sortedItems.value
  relayOrders.value = {
    left: buildRelayOrder(items, 'left', relayMemberCount.value),
    right: buildRelayOrder(items, 'right', relayMemberCount.value),
  }
}

function buildRelayOrder(items, side, count) {
  const key = side === 'left' ? 'leftMemberIds' : 'rightMemberIds'
  const order = Array.from({ length: count }, () => '')
  items.forEach((item, index) => {
    if (index < count && Array.isArray(item[key])) order[index] = item[key][0] || ''
  })
  return order
}

function teamName(side) {
  const team = side === 'left' ? lineup.value.leftTeam : lineup.value.rightTeam
  return team?.name || t.unknownTeam
}

function selectedIds(item, side = editingSide.value) {
  const key = side === 'left' ? 'leftMemberIds' : 'rightMemberIds'
  if (!Array.isArray(item[key])) item[key] = []
  return item[key]
}

function findItem(code) {
  return sortedItems.value.find((item) => item.itemCode === code)
}

function slotIndexes(item) {
  return Array.from({ length: Number(item.playerCount || 1) })
}

function slotMemberId(item, index, side = editingSide.value) {
  return selectedIds(item, side)[index] || ''
}

function memberNameById(memberId, side = editingSide.value) {
  const members = side === 'left' ? leftMembers.value : rightMembers.value
  return members.find((member) => member.id === memberId)?.name || t.emptySlot
}

function slotMemberName(item, index) {
  return memberNameById(slotMemberId(item, index))
}

function relayOrder(side = editingSide.value) {
  if (!Array.isArray(relayOrders.value[side])) relayOrders.value[side] = []
  return relayOrders.value[side]
}

function usedRelayOrder(side = editingSide.value) {
  return relayOrder(side).filter(Boolean)
}

function relayMemberId(index, side = editingSide.value) {
  return relayOrder(side)[index] || ''
}

function relayMemberName(index) {
  return memberNameById(relayMemberId(index), editingSide.value)
}

function setActiveRelaySlot(index) {
  activeRelayIndex.value = index
}

function slotTitle(item, index) {
  if (Number(item.playerCount || 1) === 1) return '\u51fa\u573a'
  return '\u7b2c ' + (index + 1) + ' \u4eba'
}

function isActiveSlot(item, index) {
  return activeSlot.value.itemCode === item.itemCode && activeSlot.value.index === index
}

function setActiveSlot(itemCode, index) {
  activeSlot.value = { itemCode, index }
}

function rosterMemberSelected(memberId) {
  if (isRelayTemplate.value) return relayOrder().includes(memberId)
  const item = findItem(activeSlot.value.itemCode)
  return item ? selectedIds(item).includes(memberId) : false
}

function rosterMemberActive(memberId) {
  if (isRelayTemplate.value) return relayMemberId(activeRelayIndex.value) === memberId
  const item = findItem(activeSlot.value.itemCode)
  return item ? slotMemberId(item, activeSlot.value.index) === memberId : false
}

function pickMember(member) {
  if (isRelayTemplate.value) {
    pickRelayMember(member)
    return
  }
  const item = findItem(activeSlot.value.itemCode)
  if (!item || !member?.id) {
    uni.showToast({ title: t.chooseSlot, icon: 'none' })
    return
  }
  const ids = selectedIds(item)
  const duplicatedIndex = ids.indexOf(member.id)
  if (duplicatedIndex >= 0 && duplicatedIndex !== activeSlot.value.index) {
    uni.showToast({ title: t.duplicated, icon: 'none' })
    return
  }
  ids[activeSlot.value.index] = member.id
  moveToNextEmptySlot()
}

function pickRelayMember(member) {
  if (!member?.id) {
    uni.showToast({ title: t.chooseSlot, icon: 'none' })
    return
  }
  const order = relayOrder()
  const duplicatedIndex = order.indexOf(member.id)
  if (duplicatedIndex >= 0 && duplicatedIndex !== activeRelayIndex.value) {
    uni.showToast({ title: t.duplicated, icon: 'none' })
    return
  }
  order[activeRelayIndex.value] = member.id
  moveToNextEmptyRelaySlot()
}

function moveToNextEmptyRelaySlot() {
  const order = relayOrder()
  const emptyIndex = order.findIndex((id, index) => index < relayMemberCount.value && !id)
  if (emptyIndex >= 0) activeRelayIndex.value = emptyIndex
}

function moveToNextEmptySlot() {
  for (const item of sortedItems.value) {
    const ids = selectedIds(item)
    const count = Number(item.playerCount || 1)
    for (let index = 0; index < count; index += 1) {
      if (!ids[index]) {
        setActiveSlot(item.itemCode, index)
        return
      }
    }
  }
}

function sideFilledCount(side) {
  if (isRelayTemplate.value) {
    return usedRelayOrder(side).length + '/' + relayMemberCount.value
  }
  return sortedItems.value.filter((item) => selectedIds(item, side).length === Number(item.playerCount || 1)).length
}

function sideComplete(side) {
  if (isRelayTemplate.value) {
    return usedRelayOrder(side).length === relayMemberCount.value
  }
  return sortedItems.value.length > 0 && sideFilledCount(side) === sortedItems.value.length
}

function sideProgressText(side) {
  if (isRelayTemplate.value) return sideFilledCount(side)
  return sideFilledCount(side) + '/' + sortedItems.value.length
}

function validateLineup() {
  if (isRelayTemplate.value) {
    const leftCount = usedRelayOrder('left').length
    const rightCount = usedRelayOrder('right').length
    return leftCount === relayMemberCount.value && rightCount === relayMemberCount.value
  }
  return sortedItems.value.length > 0 && sideComplete('left') && sideComplete('right')
}

function lineupValidationMessage() {
  if (!isRelayTemplate.value) return t.incomplete
  const leftCount = usedRelayOrder('left').length
  const rightCount = usedRelayOrder('right').length
  if (leftCount !== relayMemberCount.value || rightCount !== relayMemberCount.value) {
    return '\u63a5\u529b\u8d5b\u6bcf\u961f\u9700\u8981\u586b\u6ee1 ' + relayMemberCount.value + ' \u540d\u51fa\u573a\u961f\u5458'
  }
  return t.incomplete
}

function clearLineup() {
  if (isRelayTemplate.value) {
    relayOrders.value = {
      left: Array.from({ length: relayMemberCount.value }, () => ''),
      right: Array.from({ length: relayMemberCount.value }, () => ''),
    }
    buildRelayItemsFromOrders()
    activeRelayIndex.value = 0
    return
  }
  sortedItems.value.forEach((item) => {
    item.leftMemberIds = []
    item.rightMemberIds = []
  })
  activeSlot.value = { itemCode: 'MS', index: 0 }
}

function clearCurrentSide() {
  if (isRelayTemplate.value) {
    relayOrders.value[editingSide.value] = Array.from({ length: relayMemberCount.value }, () => '')
    buildRelayItemsFromOrders()
    activeRelayIndex.value = 0
    return
  }
  sortedItems.value.forEach((item) => {
    if (editingSide.value === 'left') item.leftMemberIds = []
    else item.rightMemberIds = []
  })
  activeSlot.value = { itemCode: 'MS', index: 0 }
}

function openEditor(side) {
  editingSide.value = side
  setupPage.value = 'editor'
  if (isRelayTemplate.value) {
    const order = relayOrder(side)
    const emptyIndex = order.findIndex((id, index) => index < relayMemberCount.value && !id)
    activeRelayIndex.value = emptyIndex >= 0 ? emptyIndex : 0
    return
  }
  const firstIncomplete = sortedItems.value.find((item) => selectedIds(item, side).length < Number(item.playerCount || 1))
  if (firstIncomplete) {
    const ids = selectedIds(firstIncomplete, side)
    const count = Number(firstIncomplete.playerCount || 1)
    const emptyIndex = Array.from({ length: count }).findIndex((_, index) => !ids[index])
    activeSlot.value = { itemCode: firstIncomplete.itemCode, index: emptyIndex >= 0 ? emptyIndex : 0 }
  } else if (sortedItems.value[0]) {
    activeSlot.value = { itemCode: sortedItems.value[0].itemCode, index: 0 }
  }
}

function backToMain() {
  setupPage.value = 'main'
}

function handleBack() {
  if (setupPage.value !== 'main') {
    backToMain()
    return
  }
  if (!beginNav()) return
  uni.navigateBack()
}

function returnToExistingMatchPageOrRedirect() {
  navigateToExistingMatchPage({
    pages: typeof getCurrentPages === 'function' ? getCurrentPages() : [],
    tournamentId: tournamentId.value,
    matchId: matchId.value,
    isRelayTemplate: isRelayTemplate.value,
    uniApi: uni,
  })
}

async function fetchLineup() {
  if (!matchId.value) return
  loading.value = true
  isError.value = false
  try {
    const data = await request('/api/v1/matches/' + matchId.value + '/team-lineup', { method: 'GET' })
    lineup.value = normalizeLineup(data)
    hydrateRelayOrders()
  } catch (_) {
    isError.value = true
  } finally {
    loading.value = false
  }
}

async function saveLineup() {
  if (!validateLineup()) {
    uni.showToast({ title: lineupValidationMessage(), icon: 'none' })
    return
  }
  await runAction(async () => {
    submitting.value = true
    try {
      if (isRelayTemplate.value) buildRelayItemsFromOrders()
      const payload = {
        items: sortedItems.value.map((item) => ({
          itemCode: item.itemCode,
          leftMemberIds: [...selectedIds(item, 'left')],
          rightMemberIds: [...selectedIds(item, 'right')],
        })),
      }
      const data = await request('/api/v1/matches/' + matchId.value + '/team-lineup', {
        method: 'PUT',
        data: payload,
      })
      lineup.value = normalizeLineup(data)
      hydrateRelayOrders()
      uni.showToast({ title: t.saved, icon: 'success' })
      setTimeout(() => {
        returnToExistingMatchPageOrRedirect()
      }, 350)
    } finally {
      submitting.value = false
    }
  })
}

function buildRelayItemsFromOrders() {
  if (!isRelayTemplate.value) return
  const leftOrder = usedRelayOrder('left').slice(0, relayMemberCount.value)
  const rightOrder = usedRelayOrder('right').slice(0, relayMemberCount.value)
  const segmentCount = relayMemberCount.value
  lineup.value.items = Array.from({ length: segmentCount }, (_, index) => {
    const nextIndex = (index + 1) % segmentCount
    return {
      displayOrder: index + 1,
      itemCode: 'R' + (index + 1),
      itemName: '第 ' + (index + 1) + ' 段',
      playerCount: 2,
      leftMemberIds: [leftOrder[index], leftOrder[nextIndex]],
      rightMemberIds: [rightOrder[index], rightOrder[nextIndex]],
      leftMembers: [],
      rightMembers: [],
      status: 0,
    }
  })
}

onLoad((options) => {
  matchId.value = options?.matchId || ''
  tournamentId.value = options?.tournamentId || ''
  if (!matchId.value) {
    uni.showToast({ title: t.missingMatchId, icon: 'none' })
    loading.value = false
    isError.value = true
    return
  }
  fetchLineup()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  height: 100vh;
  box-sizing: border-box;
  background: #13202d;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  padding-bottom: 124rpx;
}

.state-layer {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 40rpx;
}

.state-text {
  color: rgba(255, 255, 255, 0.7);
  font-size: 28rpx;
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border: none;
  border-radius: 14rpx;
  background: #ff8c00;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 800;
}

.retry-btn::after,
.ghost-btn::after,
.primary-btn::after {
  border: none;
}

.header {
  flex-shrink: 0;
  padding: 0 24rpx 20rpx;
  background: rgba(19, 32, 45, 0.98);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.header-top {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.back-btn {
  flex-shrink: 0;
  color: #ffb347;
  font-size: 26rpx;
}

.header-main {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 14rpx;
}

.title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 800;
}

.subtitle,
.hint {
  color: rgba(255, 255, 255, 0.62);
  font-size: 23rpx;
  line-height: 1.45;
}

.subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint {
  display: block;
  margin-top: 14rpx;
}

.content {
  flex: 1;
  min-height: 0;
  height: 0;
}

.main-panel,
.editor-panel {
  padding: 24rpx;
}


.entry-title,
.entry-desc,
.item-name,
.item-rule,
.slot-pos,
.slot-name {
  display: block;
}


.entry-list {
  margin-top: 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.entry-card {
  padding: 24rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 140, 0, 0.18);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
}

.entry-main {
  min-width: 0;
}

.entry-title {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 800;
}

.entry-desc {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 23rpx;
}

.entry-meta {
  flex-shrink: 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 800;
}

.entry-meta.complete {
  background: rgba(76, 217, 100, 0.16);
  color: #7ee787;
}

.slot-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.slot-row {
  padding: 18rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.055);
  display: grid;
  grid-template-columns: 170rpx 1fr;
  gap: 16rpx;
  align-items: center;
}

.slot-label {
  min-width: 0;
}

.item-name {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
}

.item-rule {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.55);
  font-size: 21rpx;
}

.slot-group {
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
}

.lineup-slot {
  min-height: 78rpx;
  box-sizing: border-box;
  padding: 10rpx 12rpx;
  border-radius: 14rpx;
  background: rgba(0, 0, 0, 0.16);
  border: 2rpx solid transparent;
}

.lineup-slot.active {
  border-color: #ff8c00;
  background: rgba(255, 140, 0, 0.14);
}

.lineup-slot.filled {
  background: rgba(255, 255, 255, 0.09);
}

.slot-pos {
  color: rgba(255, 255, 255, 0.5);
  font-size: 20rpx;
}

.slot-name {
  margin-top: 4rpx;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-panel {
  margin-top: 24rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: rgba(0, 0, 0, 0.14);
}

.roster-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.roster-title {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
}

.roster-tip {
  color: #ffb347;
  font-size: 22rpx;
  text-align: right;
}

.roster-grid {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.roster-member {
  max-width: 100%;
  min-height: 82rpx;
  box-sizing: border-box;
  padding: 14rpx 20rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.78);
  display: flex;
  align-items: center;
  gap: 8rpx;
  border: 2rpx solid transparent;
}

.roster-member.selected {
  border-color: rgba(255, 140, 0, 0.45);
}

.roster-member.active {
  background: #ff8c00;
  color: #13202d;
  font-weight: 800;
}

.member-name {
  max-width: 300rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 36rpx;
}

.captain-tag {
  flex-shrink: 0;
  font-size: 24rpx;
  opacity: 0.76;
}

.draft-empty {
  margin-top: 16rpx;
  color: rgba(255, 255, 255, 0.5);
  font-size: 23rpx;
}

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  display: flex;
  gap: 16rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border: none;
  border-radius: 18rpx;
  font-size: 28rpx;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.primary-btn {
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-weight: 900;
}
</style>
