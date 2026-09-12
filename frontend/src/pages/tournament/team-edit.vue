<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">编辑队伍</text>
    </view>

    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载队伍...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadTeam">重新加载</button>
    </view>

    <view v-else class="content">
      <view class="section-card">
        <text class="section-title">队名</text>
        <input
          class="name-input"
          v-model="teamNameInput"
          placeholder="输入新的队名"
          placeholder-class="input-placeholder"
          maxlength="30"
        />
        <button class="primary-btn" :disabled="saving" @click="saveTeamName">保存队名</button>
      </view>

      <view class="section-card">
        <text class="section-title">添加队员</text>
        <input
          class="name-input"
          v-model="newMember.name"
          placeholder="队员姓名"
          placeholder-class="input-placeholder"
          maxlength="20"
        />
        <view v-if="volleyball" class="member-extra">
          <input
            class="jersey-input"
            v-model="newMember.jerseyNumber"
            type="number"
            placeholder="球衣号"
            placeholder-class="input-placeholder"
            maxlength="3"
          />
        </view>
        <button class="primary-btn" :disabled="saving" @click="addMember">添加队员</button>
        <text class="hint-text">仅支持添加队员，暂不支持删除队员。</text>
      </view>

      <view class="section-card">
        <text class="section-title">现有队员（{{ sortedMembers.length }} 人）</text>
        <view v-if="sortedMembers.length" class="member-list">
          <view class="member-card" v-for="member in sortedMembers" :key="member.id">
            <template v-if="editingMemberId === member.id">
              <view class="member-edit">
                <input
                  class="edit-input edit-name"
                  v-model="editDraft.name"
                  placeholder="队员姓名"
                  placeholder-class="input-placeholder"
                  maxlength="20"
                />
                <input
                  v-if="volleyball"
                  class="edit-input edit-jersey"
                  v-model="editDraft.jerseyNumber"
                  type="number"
                  placeholder="球衣号"
                  placeholder-class="input-placeholder"
                  maxlength="3"
                />
              </view>
              <view class="edit-actions">
                <text class="edit-btn cancel" @click="cancelMemberEdit">取消</text>
                <text class="edit-btn save" @click="saveMemberEdit">保存</text>
              </view>
            </template>
            <template v-else>
              <view class="member-main">
                <text class="member-no" v-if="member.jerseyNumber">{{ member.jerseyNumber }}号</text>
                <text class="member-name">{{ member.name }}</text>
              </view>
              <view class="member-side">
                <view class="member-tags">
                  <text v-if="member.captain" class="member-tag captain">队长</text>
                  <text v-if="member.libero" class="member-tag libero">自由人</text>
                </view>
                <text class="edit-entry" @click="startMemberEdit(member)">编辑</text>
              </view>
            </template>
          </view>
        </view>
        <text v-else class="hint-text">暂无队员数据。</text>
        <text class="hint-text">可点击“编辑”修改队员姓名{{ volleyball ? '/号码' : '' }}；暂不支持删除队员。</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'
import { sortVolleyballMembers } from '@/utils/volleyball-team'
import { buildUpdateTeamPayload, isVolleyballSport, validateMemberEdit, validateNewMember } from '@/utils/team-edit'

// ???????????????????????? util?
// ????????????mp-weixin ????????/????????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop
    } else {
      const statusBarHeight = Number(info?.statusBarHeight)
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
        safeTopPx = statusBarHeight
      }
    }
  } catch (_) {
    // noop
  }

  let extraTopPx = 0
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2)
    try {
      if (typeof uni?.upx2px === "function") {
        const px = Number(uni.upx2px(extraTopRpx))
        if (Number.isFinite(px) && px > 0) {
          extraTopPx = px
        }
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}

const pageStyle = buildBasePortraitPageStyle()

const tournamentId = ref('')
const participantId = ref('')
const volleyball = ref(false)
const teamNameInput = ref('')
const newMember = reactive({ name: '', jerseyNumber: '' })
const members = ref([])
const editingMemberId = ref('')
const editDraft = reactive({ name: '', jerseyNumber: '' })
const saving = ref(false)
const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')

const sortedMembers = computed(() => sortVolleyballMembers(members.value))

function goBack() {
  navigateBackOrHome()
}

function toast(message) {
  uni.showToast({ title: message, icon: 'none' })
}

async function loadTeam() {
  if (!tournamentId.value || !participantId.value) {
    loading.value = false
    isError.value = true
    errorText.value = '缺少队伍参数'
    return
  }

  loading.value = true
  isError.value = false
  try {
    const data = await request('/api/v1/tournaments/' + tournamentId.value + '/teams', { method: 'GET' })
    if (!data?.creator) {
      throw new Error('仅创建者可以编辑队伍')
    }
    volleyball.value = isVolleyballSport(data?.sportType)
    const team = (Array.isArray(data?.teams) ? data.teams : []).find((item) => item.id === participantId.value)
    if (!team) {
      throw new Error('未找到对应队伍')
    }
    teamNameInput.value = team.name || ''
    members.value = Array.isArray(team.members) ? team.members : []
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载队伍失败'
  } finally {
    loading.value = false
  }
}

async function submitUpdate(payload, successMessage) {
  if (saving.value) return
  saving.value = true
  try {
    await request('/api/v1/tournaments/' + tournamentId.value + '/teams/' + participantId.value, {
      method: 'PUT',
      data: payload,
    })
    toast(successMessage)
    await loadTeam()
  } catch (error) {
    toast(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function saveTeamName() {
  const name = String(teamNameInput.value || '').trim()
  if (!name) {
    toast('队名不能为空')
    return
  }
  submitUpdate(buildUpdateTeamPayload({ teamName: name, rename: true, member: null, volleyball: false }), '队名已保存')
}

function addMember() {
  const error = validateNewMember(newMember, volleyball.value)
  if (error) {
    toast(error)
    return
  }
  submitUpdate(
    buildUpdateTeamPayload({ teamName: '', rename: false, member: newMember, volleyball: volleyball.value }),
    '队员已添加',
  )
  newMember.name = ''
  newMember.jerseyNumber = ''
}

function startMemberEdit(member) {
  editingMemberId.value = member.id
  editDraft.name = member.name || ''
  editDraft.jerseyNumber = member.jerseyNumber == null ? '' : String(member.jerseyNumber)
}

function cancelMemberEdit() {
  editingMemberId.value = ''
}

function saveMemberEdit() {
  const memberId = editingMemberId.value
  if (!memberId) return
  const original = members.value.find((item) => item.id === memberId)
  const draft = { memberId, name: editDraft.name, jerseyNumber: editDraft.jerseyNumber }
  const error = validateMemberEdit(draft, volleyball.value)
  if (error) {
    toast(error)
    return
  }
  const nameChanged = draft.name !== String(original?.name || '')
  const numberChanged = volleyball.value && Number(draft.jerseyNumber) !== original?.jerseyNumber
  if (!nameChanged && !numberChanged) {
    editingMemberId.value = ''
    return
  }
  submitUpdate(
    buildUpdateTeamPayload({ teamName: '', rename: false, member: null, memberUpdate: draft, volleyball: volleyball.value }),
    '队员信息已保存',
  )
  editingMemberId.value = ''
}

onLoad((options) => {
  tournamentId.value = options?.tournamentId || ''
  participantId.value = options?.participantId || ''
  volleyball.value = isVolleyballSport(options?.sportType)
  loadTeam()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 40rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.18), transparent 34%),
    linear-gradient(180deg, #13202d 0%, #0f1822 100%);
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

.state-layer {
  min-height: calc(100vh - 140rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
}

.state-text {
  color: rgba(255, 255, 255, 0.76);
  font-size: 28rpx;
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.section-card {
  padding: 24rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.section-title {
  display: block;
  margin-bottom: 18rpx;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
}

.name-input,
.jersey-input {
  height: 76rpx;
  padding: 0 20rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  color: #ffffff;
  font-size: 28rpx;
}

.input-placeholder {
  color: rgba(255, 255, 255, 0.38);
}

.member-extra {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 16rpx;
}

.jersey-input {
  width: 180rpx;
}

.primary-btn {
  margin-top: 20rpx;
  width: 100%;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: 28rpx;
  font-weight: 700;
}

.primary-btn::after {
  border: none;
}

.primary-btn[disabled] {
  opacity: 0.6;
}

.hint-text {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.45);
  font-size: 22rpx;
  line-height: 1.6;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.member-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.07);
}

.member-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.member-no {
  min-width: 88rpx;
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.member-name {
  color: #ffffff;
  font-size: 28rpx;
}

.member-tags {
  display: flex;
  gap: 10rpx;
  flex-shrink: 0;
}

.member-side {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
}

.edit-entry {
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
  padding: 8rpx 4rpx;
}

.member-edit {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex: 1;
  min-width: 0;
}

.edit-input {
  height: 64rpx;
  padding: 0 16rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 179, 71, 0.35);
  color: #ffffff;
  font-size: 26rpx;
}

.edit-name {
  flex: 1;
  min-width: 0;
}

.edit-jersey {
  width: 140rpx;
  flex-shrink: 0;
}

.edit-actions {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex-shrink: 0;
}

.edit-btn {
  font-size: 24rpx;
  font-weight: 700;
  padding: 10rpx 8rpx;
}

.edit-btn.cancel {
  color: rgba(255, 255, 255, 0.55);
}

.edit-btn.save {
  color: #ff8c00;
}

.member-tag {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.member-tag.captain {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
}

.member-tag.libero {
  background: rgba(82, 196, 26, 0.16);
  color: #95de64;
}
</style>
