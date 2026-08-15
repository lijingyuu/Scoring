<template>
  <view class="page" @touchmove.stop.prevent="() => {}">
    <view class="sign-shell">
      <view class="sign-header">
        <text class="sign-title">请{{ signLabel }}签字</text>
        <button class="icon-btn" :disabled="saving" @click="cancelSignature">×</button>
      </view>

      <view class="canvas-frame" :style="canvasFrameStyle">
        <canvas
          canvas-id="signatureCaptureCanvas"
          id="signatureCaptureCanvas"
          class="sign-canvas"
          disable-scroll="true"
          @touchstart="onSignTouchStart"
          @touchmove="onSignTouchMove"
          @touchend="onSignTouchEnd"
          @touchcancel="onSignTouchEnd"
        />
      </view>

      <view class="sign-actions">
        <button class="sign-btn sign-btn--secondary" :disabled="saving" @click="clearSignature">清空</button>
        <button class="sign-btn sign-btn--secondary" :disabled="saving" @click="cancelSignature">取消</button>
        <button class="sign-btn sign-btn--primary" :disabled="saving" @click="confirmSignature">{{ saving ? '保存中...' : '确认' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onLoad, onReady, onUnload } from '@dcloudio/uni-app'
import { buildSignatureResultEvent } from '@/utils/signature-capture'

const EXPORT_WIDTH = 960
const EXPORT_HEIGHT = 320
const SIGN_RATIO = 3

const eventKey = ref('')
const signLabel = ref('')
const saving = ref(false)
const signCtx = ref(null)
const signDrawing = ref(false)
const signCanvasSize = ref({ width: 0, height: 0 })
const strokes = ref([])
const currentStroke = ref([])
const resultEmitted = ref(false)

const canvasFrameStyle = computed(() => {
  const size = signCanvasSize.value
  if (!size.width || !size.height) return ''
  return {
    width: size.width + 'px',
    height: size.height + 'px',
  }
})

onLoad((options = {}) => {
  eventKey.value = cleanText(decodeURIComponent(options.eventKey || ''))
  signLabel.value = cleanText(decodeURIComponent(options.label || ''))
})

onReady(() => {
  initCanvasSize()
  nextTick(() => initSignCanvas())
})

onUnload(() => {
  emitSignatureResult({ cancelled: true })
})

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function initCanvasSize() {
  const info = uni.getSystemInfoSync()
  const windowWidth = Number(info.windowWidth || 0)
  const windowHeight = Number(info.windowHeight || 0)
  const safeAreaInsets = info.safeAreaInsets || {}
  const safeLeft = Number(safeAreaInsets.left || 0)
  const safeRight = Number(safeAreaInsets.right || 0)
  const safeTop = Number(safeAreaInsets.top || 0)
  const safeBottom = Number(safeAreaInsets.bottom || 0)

  const horizontalPadding = 32 + safeLeft + safeRight
  const verticalChrome = 160 + safeTop + safeBottom
  const availableWidth = Math.max(240, windowWidth - horizontalPadding)
  const availableHeight = Math.max(120, windowHeight - verticalChrome)

  const width = Math.min(availableWidth, availableHeight * SIGN_RATIO)
  const height = width / SIGN_RATIO
  signCanvasSize.value = {
    width: Math.round(width),
    height: Math.round(height),
  }
}

function initSignCanvas() {
  const ctx = uni.createCanvasContext('signatureCaptureCanvas')
  ctx.setStrokeStyle('#1d252e')
  ctx.setLineWidth(4)
  ctx.setLineCap('round')
  ctx.setLineJoin('round')
  signCtx.value = ctx
}

function canvasPoint(touch) {
  if (touch && typeof touch.x === 'number' && typeof touch.y === 'number') {
    return { x: touch.x, y: touch.y }
  }
  return { x: 0, y: 0 }
}

function onSignTouchStart(e) {
  if (saving.value) return
  const ctx = signCtx.value
  if (!ctx) return
  signDrawing.value = true
  const pt = canvasPoint(e.touches?.[0] || {})
  currentStroke.value = [pt]
  ctx.beginPath()
  ctx.arc(pt.x, pt.y, 2, 0, Math.PI * 2)
  ctx.setFillStyle('#1d252e')
  ctx.fill()
  ctx.draw(true)
}

function onSignTouchMove(e) {
  if (!signDrawing.value || saving.value) return
  const ctx = signCtx.value
  if (!ctx) return
  const pt = canvasPoint(e.touches?.[0] || {})
  const points = currentStroke.value
  if (!points.length) return
  const last = points[points.length - 1]
  const dist = Math.sqrt((pt.x - last.x) ** 2 + (pt.y - last.y) ** 2)
  if (dist < 1.5) return
  points.push(pt)
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
  ctx.clearRect(0, 0, size.width || 9999, size.height || 9999)
  ctx.setStrokeStyle('#1d252e')
  ctx.setLineWidth(4)
  ctx.setLineCap('round')
  ctx.setLineJoin('round')
  strokes.value.forEach((stroke) => {
    if (!stroke.length) return
    ctx.beginPath()
    ctx.moveTo(stroke[0].x, stroke[0].y)
    for (let index = 1; index < stroke.length; index += 1) {
      ctx.lineTo(stroke[index].x, stroke[index].y)
    }
    ctx.stroke()
  })
  ctx.draw(false, callback)
}

function clearSignature() {
  if (saving.value) return
  const ctx = signCtx.value
  const size = signCanvasSize.value
  strokes.value = []
  currentStroke.value = []
  if (ctx) {
    ctx.clearRect(0, 0, size.width || 9999, size.height || 9999)
    ctx.draw()
  }
}

function cancelSignature() {
  if (saving.value) return
  emitSignatureResult({ cancelled: true })
  uni.navigateBack()
}

function confirmSignature() {
  if (saving.value) return
  if (!strokes.value.length) {
    uni.showToast({ title: '签名内容为空，请先绘制', icon: 'none' })
    return
  }
  saving.value = true
  uni.showLoading({ title: '保存中...' })
  redrawStrokes(() => exportSignature())
}

function exportSignature() {
  uni.canvasToTempFilePath({
    canvasId: 'signatureCaptureCanvas',
    fileType: 'png',
    quality: 1,
    destWidth: EXPORT_WIDTH,
    destHeight: EXPORT_HEIGHT,
    success: async (res) => {
      try {
        const dataUrl = await tempFileToDataUrl(res.tempFilePath)
        uni.hideLoading()
        saving.value = false
        emitSignatureResult({ dataUrl })
        uni.navigateBack()
      } catch (error) {
        uni.hideLoading()
        saving.value = false
        uni.showToast({ title: error?.message || '保存签名失败，请重试', icon: 'none' })
      }
    },
    fail: () => {
      uni.hideLoading()
      saving.value = false
      uni.showToast({ title: '保存签名失败，请重试', icon: 'none' })
    },
  })
}

function emitSignatureResult(payload) {
  if (resultEmitted.value || !eventKey.value) return
  resultEmitted.value = true
  uni.$emit(buildSignatureResultEvent(eventKey.value), payload)
}

function tempFileToDataUrl(tempFilePath) {
  const filePath = cleanText(tempFilePath)
  if (!filePath) return Promise.reject(new Error('签名图片为空'))
  if (/^data:image\//.test(filePath)) return Promise.resolve(filePath)

  if (typeof uni.getFileSystemManager === 'function') {
    return new Promise((resolve, reject) => {
      uni.getFileSystemManager().readFile({
        filePath,
        encoding: 'base64',
        success: (res) => resolve('data:image/png;base64,' + res.data),
        fail: () => reject(new Error('读取签名失败，请重试')),
      })
    })
  }

  if (typeof fetch === 'function' && typeof FileReader !== 'undefined') {
    return fetch(filePath)
      .then((res) => res.blob())
      .then((blob) => new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result || ''))
        reader.onerror = () => reject(new Error('读取签名失败，请重试'))
        reader.readAsDataURL(blob)
      }))
  }

  return Promise.reject(new Error('当前环境不支持保存签名'))
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 16px;
  background: #101a25;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sign-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.sign-header {
  width: 100%;
  max-width: 1200px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sign-title {
  color: #ffffff;
  font-size: 22px;
  font-weight: 700;
}

.icon-btn {
  width: 44px;
  height: 44px;
  line-height: 44px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
  font-size: 28px;
}

.icon-btn::after,
.sign-btn::after {
  border: none;
}

.canvas-frame {
  max-width: 1200px;
  border-radius: 14px;
  overflow: hidden;
  border: 2px solid rgba(255, 179, 71, 0.68);
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.35);
}

.sign-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.sign-actions {
  width: 100%;
  max-width: 1200px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sign-btn {
  height: 44px;
  line-height: 44px;
  border: none;
  border-radius: 12px;
  font-size: 17px;
  font-weight: 700;
}

.sign-btn--secondary {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.sign-btn--primary {
  background: #ffb347;
  color: #13202d;
}

.sign-btn[disabled],
.icon-btn[disabled] {
  opacity: 0.62;
}
</style>
