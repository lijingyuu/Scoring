<template>
  <main class="auth-shell">
    <section class="auth-panel">
      <div>
        <p class="eyebrow">Eunomia Admin</p>
        <h1>赛事后台</h1>
        <p class="muted">用网页完成赛前录入和管理，现场操作继续交给小程序。</p>
      </div>

      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="form-stack" @submit.prevent="submit">
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMe, passwordLogin, register, setToken } from '../services/api'

const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

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
