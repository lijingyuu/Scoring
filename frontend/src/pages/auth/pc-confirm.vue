<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn" @click="goHome">返回</text>
      <text class="title">网页端登录</text>
    </view>

    <view v-if="phase === 'loading'" class="state-layer">
      <text class="state-text">正在获取登录信息...</text>
    </view>

    <view v-else-if="phase === 'error'" class="state-layer">
      <text class="state-text state-error">{{ errorText }}</text>
      <button v-if="ticket" class="retry-btn" @click="init">重试</button>
      <button v-if="ticket" class="plain-btn" @click="goHome">返回首页</button>
      <button v-else class="retry-btn" @click="goHome">返回首页</button>
    </view>

    <view v-else-if="phase === 'confirm'" class="content">
      <view class="section-card confirm-card">
        <text class="confirm-icon">💻</text>
        <text class="confirm-title">电脑端申请登录</text>
        <text class="confirm-desc">确认后，电脑网页端将以你的小程序账号{{ nicknameText }}登录「赛事后台」。</text>
        <button class="primary-btn" :disabled="confirming" @click="confirmLogin">
          {{ confirming ? '确认中...' : '确认授权' }}
        </button>
        <button class="ghost-btn" @click="goHome">取消</button>
        <text class="hint-text">授权仅用于本次电脑端登录，票据 3 分钟内有效；如非本人操作，请点击取消。</text>
      </view>
    </view>

    <view v-else-if="phase === 'success'" class="state-layer">
      <text class="success-icon">✅</text>
      <text class="state-text">授权成功，请回到电脑继续操作</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { authState, ensureAuth, fetchProfile } from '@/store/auth'

// 小程序码 scene 固定为 32 位十六进制 ticket
const TICKET_PATTERN = /^[0-9a-f]{32}$/

// 与其他页面相同原因内联（utils/base-page-layout.js 在 mp-weixin 发行构建下
// 曾出现 "is not defined"/ENOENT，见 team-edit.vue 同款注释）
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

const phase = ref('loading') // loading | confirm | success | error
const errorText = ref('')
const confirming = ref(false)
const ticket = ref('')

const nicknameText = computed(() => {
  const name = authState.profile?.nickname || authState.nickname
  return name ? `（${name}）` : ''
})

onLoad((options) => {
  // 扫码进入时微信传入编码过的 scene，必须 decodeURIComponent
  const scene = options?.scene ? decodeURIComponent(options.scene) : ''
  if (!TICKET_PATTERN.test(scene)) {
    phase.value = 'error'
    errorText.value = '二维码参数无效，请回到电脑重新发起登录'
    return
  }
  ticket.value = scene
  init()
})

async function init() {
  phase.value = 'loading'
  try {
    await ensureAuth()
    try {
      await fetchProfile()
    } catch (_) {
      // 资料拉取失败不影响授权
    }
    // 上报扫码（幂等；失败不打断，confirm 允许 CREATED 直转 CONFIRMED）
    try {
      await request('/api/v1/auth/pc/scan', {
        method: 'POST',
        data: { ticket: ticket.value },
        silent: true,
      })
    } catch (_) {
      // noop
    }
    phase.value = 'confirm'
  } catch (error) {
    phase.value = 'error'
    errorText.value = error?.message || '登录信息获取失败，请重试'
  }
}

async function confirmLogin() {
  if (confirming.value) return
  confirming.value = true
  try {
    await request('/api/v1/auth/pc/confirm', {
      method: 'POST',
      data: { ticket: ticket.value },
    })
    phase.value = 'success'
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 2000)
  } catch (error) {
    uni.showToast({ title: error?.message || '授权失败', icon: 'none' })
    // 票据类不可恢复错误（过期/已用/无效/非扫码人）直接转错误页，重试点确认无意义
    if (/过期|已被使用|无效|请使用扫码/.test(error?.message || '')) {
      phase.value = 'error'
      errorText.value = error?.message || '二维码已失效，请回到电脑重新发起登录'
    }
  } finally {
    confirming.value = false
  }
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
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

.success-icon {
  font-size: 72rpx;
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

.plain-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 26rpx;
}

.plain-btn::after {
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

.confirm-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 32rpx;
}

.confirm-icon {
  font-size: 88rpx;
  margin-bottom: 20rpx;
}

.confirm-title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.confirm-desc {
  color: rgba(255, 255, 255, 0.72);
  font-size: 26rpx;
  line-height: 1.7;
  text-align: center;
  margin-bottom: 36rpx;
}

.primary-btn {
  margin-top: 8rpx;
  width: 100%;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: 30rpx;
  font-weight: 700;
}

.primary-btn::after {
  border: none;
}

.primary-btn[disabled] {
  opacity: 0.6;
}

.ghost-btn {
  margin-top: 16rpx;
  width: 100%;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 14rpx;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 26rpx;
}

.ghost-btn::after {
  border: none;
}

.hint-text {
  display: block;
  margin-top: 20rpx;
  color: rgba(255, 255, 255, 0.45);
  font-size: 22rpx;
  line-height: 1.6;
  text-align: center;
}
</style>
