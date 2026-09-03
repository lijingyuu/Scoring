<template>
  <view class="page" @touchmove.stop.prevent="() => {}">
    <view class="sign-shell">
      <view class="sign-header">
        <text class="sign-title">请{{ signLabel }}签字</text>
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

      <canvas
        canvas-id="signatureExportCanvas"
        id="signatureExportCanvas"
        class="export-canvas"
      />

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
import { onLoad, onReady, onResize, onUnload } from '@dcloudio/uni-app'
import { buildSignatureResultEvent } from '@/utils/signature-capture'

const EXPORT_WIDTH = 960
const EXPORT_HEIGHT = 320
const LAYOUT_HORIZONTAL_PADDING = 8
const LAYOUT_VERTICAL_CHROME = 96

const eventKey = ref('')
const signLabel = ref('')
const saving = ref(false)
const signCtx = ref(null)
const signDrawing = ref(false)
const signCanvasSize = ref({ width: 0, height: 0 })
const strokes = ref([])
const currentStroke = ref([])
const resultEmitted = ref(false)
let resizeTimer = null

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
  resizeAfterOrientationSettles()
  nextTick(() => {
    initSignCanvas()
    redrawStrokes()
  })
})

onResize((event) => {
  initCanvasSize(event)
  resizeAfterOrientationSettles()
})

onUnload(() => {
  if (resizeTimer) {
    clearTimeout(resizeTimer)
    resizeTimer = null
  }
  emitSignatureResult({ cancelled: true })
})

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function initCanvasSize(source) {
  const viewport = landscapeViewportSize(source || uni.getSystemInfoSync())
  const width = Math.max(280, viewport.width - LAYOUT_HORIZONTAL_PADDING)
  const height = Math.max(160, viewport.height - LAYOUT_VERTICAL_CHROME)
  setCanvasSize({
    width: Math.round(width),
    height: Math.round(height),
  })
}

function resizeAfterOrientationSettles() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    resizeTimer = null
    initCanvasSize()
  }, 240)
}

function landscapeViewportSize(source = {}) {
  const size = source.size || {}
  const windowWidth = Number(size.windowWidth || source.windowWidth || source.screenWidth || 0)
  const windowHeight = Number(size.windowHeight || source.windowHeight || source.screenHeight || 0)
  return {
    width: Math.max(windowWidth, windowHeight),
    height: Math.min(windowWidth, windowHeight),
  }
}

function setCanvasSize(nextSize) {
  const prevSize = signCanvasSize.value
  if (prevSize.width === nextSize.width && prevSize.height === nextSize.height) return
  if (prevSize.width && prevSize.height && strokes.value.length) {
    const scaleX = nextSize.width / prevSize.width
    const scaleY = nextSize.height / prevSize.height
    strokes.value = scaleStrokeList(strokes.value, scaleX, scaleY)
    currentStroke.value = scaleStroke(currentStroke.value, scaleX, scaleY)
  }
  signCanvasSize.value = nextSize
  if (signCtx.value) {
    nextTick(() => redrawStrokes())
  }
}

function scaleStrokeList(list, scaleX, scaleY) {
  return list.map((stroke) => scaleStroke(stroke, scaleX, scaleY))
}

function scaleStroke(stroke, scaleX, scaleY) {
  return stroke.map((point) => ({
    x: point.x * scaleX,
    y: point.y * scaleY,
  }))
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
  exportSignature()
}

function exportSignature() {
  drawExportCanvas(() => {
    uni.canvasToTempFilePath({
      canvasId: 'signatureExportCanvas',
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
  })
}

function drawExportCanvas(callback) {
  const sourceSize = signCanvasSize.value
  if (!sourceSize.width || !sourceSize.height) {
    callback()
    return
  }
  const exportCtx = uni.createCanvasContext('signatureExportCanvas')
  const scale = Math.min(EXPORT_WIDTH / sourceSize.width, EXPORT_HEIGHT / sourceSize.height)
  const offsetX = (EXPORT_WIDTH - sourceSize.width * scale) / 2
  const offsetY = (EXPORT_HEIGHT - sourceSize.height * scale) / 2

  exportCtx.clearRect(0, 0, EXPORT_WIDTH, EXPORT_HEIGHT)
  exportCtx.setStrokeStyle('#1d252e')
  exportCtx.setLineWidth(Math.max(3, 4 * scale))
  exportCtx.setLineCap('round')
  exportCtx.setLineJoin('round')
  strokes.value.forEach((stroke) => {
    if (!stroke.length) return
    exportCtx.beginPath()
    if (stroke.length === 1) {
      exportCtx.arc(offsetX + stroke[0].x * scale, offsetY + stroke[0].y * scale, Math.max(2, 2 * scale), 0, Math.PI * 2)
      exportCtx.setFillStyle('#1d252e')
      exportCtx.fill()
      return
    }
    exportCtx.moveTo(offsetX + stroke[0].x * scale, offsetY + stroke[0].y * scale)
    for (let index = 1; index < stroke.length; index += 1) {
      exportCtx.lineTo(offsetX + stroke[index].x * scale, offsetY + stroke[index].y * scale)
    }
    exportCtx.stroke()
  })
  exportCtx.draw(false, callback)
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
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  box-sizing: border-box;
  padding: 4px;
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
  gap: 6px;
}

.sign-header {
  width: 100%;
  height: 38px;
  box-sizing: border-box;
  padding-left: max(4px, env(safe-area-inset-left));
  padding-right: max(4px, env(safe-area-inset-right));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sign-title {
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(16, 26, 37, 0.72);
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  max-width: 58vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: auto;
}

.sign-btn::after {
  border: none;
}

.canvas-frame {
  box-sizing: border-box;
  max-width: none;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid rgba(255, 179, 71, 0.68);
  background: #ffffff;
  box-shadow: 0 12px 34px rgba(0, 0, 0, 0.34);
}

.sign-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.export-canvas {
  position: fixed;
  left: -9999px;
  top: -9999px;
  width: 960px;
  height: 320px;
  pointer-events: none;
}

.sign-actions {
  width: 100%;
  height: 38px;
  box-sizing: border-box;
  padding-left: max(4px, env(safe-area-inset-left));
  padding-right: max(4px, env(safe-area-inset-right));
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  flex-shrink: 0;
}

.sign-btn {
  min-width: 0;
  height: 38px;
  line-height: 38px;
  padding: 0 4px;
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sign-btn--secondary {
  background: rgba(16, 26, 37, 0.72);
  color: #ffffff;
}

.sign-btn--primary {
  background: #ffb347;
  color: #13202d;
}

.sign-btn[disabled] {
  opacity: 0.62;
}
</style>
