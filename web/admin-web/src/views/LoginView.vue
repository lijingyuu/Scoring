<template>
  <main class="auth-shell">
    <section class="auth-panel">
      <div>
        <p class="eyebrow">Eunomia Admin</p>
        <h1>赛事后台</h1>
        <p class="muted">用网页完成赛前录入和管理，现场操作继续交给小程序。</p>
      </div>

      <div class="tabs three-tabs">
        <button :class="{ active: mode === 'wechat' }" @click="switchMode('wechat')">微信扫码</button>
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">账号登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </div>

      <div v-if="mode === 'wechat'" class="qr-panel">
        <div class="qr-box" :class="{ clickable: expired }" @click="expired && startQrLogin()">
          <img v-if="qrImage" :src="qrImage" alt="微信小程序码" class="qr-image" />
          <div v-else class="qr-placeholder">生成中...</div>
          <div v-if="expired" class="qr-mask">
            <span>二维码已过期</span>
            <span class="qr-refresh">点击刷新</span>
          </div>
        </div>

        <p v-if="qrPhase === 'scanned'" class="qr-hint scanned">
          <img v-if="scannedAvatar" :src="scannedAvatar" class="scanned-avatar" alt="" />
          <span>{{ scannedNickname ? `${scannedNickname} 已扫码` : '已扫码' }}，请在手机上点击「确认授权」</span>
        </p>
        <p v-else class="qr-hint">请使用微信「扫一扫」扫描上方小程序码</p>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p class="muted qr-tip">扫码确认后将以你的小程序账号登录，两端数据互通；还没有账号则会自动创建。</p>
      </div>

      <form v-else class="form-stack" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model.trim="form.username" autocomplete="username" placeholder="3-32位字母、数字或下划线" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" autocomplete="current-password" type="password" placeholder="至少6位" />
        </label>
        <label v-if="mode === 'register'">
          <span>昵称</span>
          <input v-model.trim="form.nickname" autocomplete="nickname" placeholder="用于显示创建者资料" />
        </label>

        <p v-if="error" class="error-text">{{ error }}</p>
        <button class="primary-action" :disabled="loading">
          {{ loading ? '处理中...' : mode === 'login' ? '登录后台' : '创建账号' }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMe, passwordLogin, pcLoginStatus, pcQrCode, register, setToken } from '../services/api'

const POLL_INTERVAL_MS = 1500

const router = useRouter()
const mode = ref('wechat')
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

// 微信扫码登录状态
const qrImage = ref('')
const qrTicket = ref('')
const qrPhase = ref('') // '' 生成中 | 'created' 待扫码 | 'scanned' 已扫码待确认
const expired = ref(false)
const scannedNickname = ref('')
const scannedAvatar = ref('')
let pollTimer = null

onMounted(() => {
  if (mode.value === 'wechat') {
    startQrLogin()
  }
})

onUnmounted(stopPolling)

function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  error.value = ''
  if (next === 'wechat') {
    startQrLogin()
  } else {
    stopPolling()
  }
}

async function startQrLogin() {
  stopPolling()
  qrImage.value = ''
  qrTicket.value = ''
  qrPhase.value = ''
  expired.value = false
  scannedNickname.value = ''
  scannedAvatar.value = ''
  error.value = ''
  try {
    const data = await pcQrCode()
    qrTicket.value = data.ticket
    qrImage.value = data.qrImage
    qrPhase.value = 'created'
    pollTimer = setInterval(pollStatus, POLL_INTERVAL_MS)
  } catch (err) {
    error.value = err?.message || '二维码生成失败，请稍后重试'
  }
}

async function pollStatus() {
  if (!qrTicket.value || expired.value) return
  try {
    const data = await pcLoginStatus(qrTicket.value)
    if (data.status === 'SCANNED') {
      qrPhase.value = 'scanned'
      scannedNickname.value = data.nickname || ''
      scannedAvatar.value = data.avatarUrl || ''
      return
    }
    if (data.status === 'EXPIRED') {
      stopPolling()
      expired.value = true
      return
    }
    if (data.status === 'CONFIRMED' && data.token) {
      stopPolling()
      setToken(data.token)
      await fetchMe()
      router.replace('/lobby')
    }
    // CREATED → 继续轮询；CONSUMED（token 已被取走）在正常流程不会出现，忽略
  } catch (_) {
    // 网络波动不打断轮询，等下一拍
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const payload = {
      username: form.username,
      password: form.password,
      ...(mode.value === 'register' ? { nickname: form.nickname } : {}),
    }
    const data = mode.value === 'register'
      ? await register(payload)
      : await passwordLogin(payload)
    setToken(data.token)
    await fetchMe()
    router.replace('/lobby')
  } catch (err) {
    error.value = err?.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.three-tabs {
  grid-template-columns: repeat(3, 1fr);
}

.qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-top: 6px;
}

.qr-box {
  position: relative;
  width: 224px;
  height: 224px;
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.qr-box.clickable {
  cursor: pointer;
}

.qr-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.qr-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 14px;
}

.qr-mask {
  position: absolute;
  inset: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #374151;
  font-size: 14px;
}

.qr-refresh {
  color: #c2410c;
  font-weight: 600;
  text-decoration: underline;
}

.qr-hint {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
  text-align: center;
}

.qr-hint.scanned {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
}

.scanned-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
}

.qr-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  text-align: center;
}
</style>
