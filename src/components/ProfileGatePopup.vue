<template>
  <!-- 重要：v-if 绑定的是本地 ref，不是 import 的 reactive。 -->
  <!-- uni-app 编译到微信小程序时，跨模块 import 的 reactive 对象在 WXML 中可能无法响应变化。 -->
  <view v-if="visible" class="popup-mask" @click="handleCancel">
    <view class="popup-card" @click.stop>
      <view class="popup-title">{{ popupTitle }}</view>
      <view class="popup-desc">用于创建比赛、收藏比赛和裁判操作，可稍后再补全。</view>

      <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        <image v-if="localAvatarUrl" class="avatar-image" :src="localAvatarUrl" mode="aspectFill" />
        <text v-else>选择头像</text>
      </button>

      <input
        class="nickname-input"
        type="nickname"
        v-model="localNickname"
        placeholder="请输入昵称"
        @input="onNicknameInput"
      />

      <view class="popup-actions">
        <button class="ghost-btn" @click="handleCancel">{{ cancelText }}</button>
        <button class="primary-btn" :loading="submitting" @click="handleSubmit">保存资料</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { authState, closeProfilePopup, submitProfile } from '@/store/auth'

// ============================================================
// 所有模板绑定均使用本地 ref —— 不直接引用 import 的 reactive 对象。
// 原因：uni-app 编译为微信小程序 WXML 时，模板中 import 进来的
// reactive() 对象可能被编译为静态初始值（false/''），导致 v-if
// 永远不成立、setData 从不触发、弹窗永远不出现。
// ============================================================

// -- 弹窗显隐（从全局状态同步到本地 ref）--
const visible = ref(false)

// -- 表单字段（本地副本，提交前不污染全局）--
const localNickname = ref('')
const localAvatarUrl = ref('')

// -- 提交中状态（本地副本）--
const submitting = ref(false)

// -- 资料是否已完善（本地副本，影响标题和按钮文案）--
const isProfileCompleted = ref(false)

// -- 计算属性（基于本地 ref，模板中不使用 authState）--
const popupTitle = computed(() =>
  isProfileCompleted.value ? '修改个人资料' : '完善个人资料'
)
const cancelText = computed(() =>
  isProfileCompleted.value ? '取消' : '暂不完善'
)

// ============================================================
// 全局 → 本地：监听 authState 变化，同步到本地 ref
// ============================================================
watch(
  () => authState.popupVisible,
  (val) => {
    visible.value = val
    if (val) {
      localNickname.value = authState.nickname || ''
      localAvatarUrl.value = authState.avatarUrl || ''
      isProfileCompleted.value = !!authState.profileCompleted
    }
  },
  { immediate: true }
)

watch(
  () => authState.loading,
  (val) => {
    submitting.value = val
  },
  { immediate: true }
)

// ============================================================
// 事件处理
// ============================================================
function onChooseAvatar(event) {
  localAvatarUrl.value = event?.detail?.avatarUrl || ''
}

// 微信 type="nickname" 可能不触发标准 input 事件，
// 这里额外监听 @input 兜底捕获值变化。
function onNicknameInput(event) {
  const val = event?.detail?.value
  if (val !== undefined) {
    localNickname.value = val
  }
}

function handleCancel() {
  closeProfilePopup()
}

async function handleSubmit() {
  if (!localNickname.value.trim()) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  if (!localAvatarUrl.value) {
    uni.showToast({ title: '请选择头像', icon: 'none' })
    return
  }
  // 提交前先把本地值写回全局状态（submitProfile 内部会读全局作为 fallback）
  authState.nickname = localNickname.value.trim()
  authState.avatarUrl = localAvatarUrl.value
  await submitProfile(localNickname.value.trim(), localAvatarUrl.value)
}
</script>

<style scoped>
.popup-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}

.popup-card {
  width: 100%;
  background: #194955;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  padding: 40rpx 32rpx 54rpx;
  box-sizing: border-box;
  border-top: 1rpx solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 -18rpx 40rpx rgba(0, 0, 0, 0.24), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
}

.popup-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
}

.popup-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.72);
}

.avatar-btn {
  margin-top: 28rpx;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  padding: 0;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.avatar-btn::after,
.ghost-btn::after,
.primary-btn::after {
  border: none;
}

.avatar-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
}

.nickname-input {
  margin-top: 28rpx;
  height: 84rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 28rpx;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.popup-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  border: none;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.86);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.primary-btn {
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.28);
}
</style>
