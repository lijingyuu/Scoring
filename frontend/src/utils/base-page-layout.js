/**
 * 基础竖屏页顶部安全区留白。
 *
 * 优先读取真机 safeAreaInsets.top，回退到 statusBarHeight，
 * 最后兜底为 0。可通过 extraTopRpx 追加额外留白（仅 index 赛事大厅使用）。
 */
function readSafeTopPx() {
  try {
    const info = typeof uni.getWindowInfo === 'function'
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      return safeInsetTop
    }
    const statusBarHeight = Number(info?.statusBarHeight)
    if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
      return statusBarHeight
    }
  } catch (_) {
    // noop
  }
  return 0
}

function rpxToPx(rpx) {
  try {
    if (typeof uni?.upx2px === 'function') {
      const px = Number(uni.upx2px(rpx))
      if (Number.isFinite(px) && px > 0) {
        return px
      }
    }
  } catch (_) {
    // noop
  }
  return Math.round(rpx / 2)
}

// ????????????????? import ?????
// ???????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????????? helper?
export function buildBasePortraitPageStyle(extraTopRpx = 0) {
  const safeTopPx = readSafeTopPx()
  const extraTopPx = extraTopRpx > 0 ? rpxToPx(extraTopRpx) : 0

  return {
    boxSizing: 'border-box',
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}
