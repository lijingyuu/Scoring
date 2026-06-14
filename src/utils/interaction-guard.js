import { onUnmounted, ref, watch } from 'vue'

export function useDelayedTapGate(source, delay = 120) {
  const interactive = ref(false)
  let timer = null

  function clearTimer() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  function arm() {
    clearTimer()
    interactive.value = false
    timer = setTimeout(() => {
      timer = null
      interactive.value = true
    }, delay)
  }

  watch(
    () => (typeof source === 'function' ? source() : source?.value),
    (visible) => {
      if (visible) {
        arm()
      } else {
        clearTimer()
        interactive.value = false
      }
    },
    { immediate: true }
  )

  onUnmounted(() => {
    clearTimer()
  })

  return {
    interactive,
  }
}

export function useActionLock(defaultDuration = 350) {
  const locked = ref(false)
  let timer = null

  function clearTimer() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  function release() {
    clearTimer()
    locked.value = false
  }

  function begin(duration = defaultDuration) {
    if (locked.value) return false
    locked.value = true
    clearTimer()
    if (duration !== Infinity) {
      timer = setTimeout(() => {
        release()
      }, duration)
    }
    return true
  }

  async function run(action) {
    if (locked.value) return
    locked.value = true
    clearTimer()
    try {
      return await action()
    } finally {
      release()
    }
  }

  onUnmounted(() => {
    clearTimer()
  })

  return {
    locked,
    begin,
    release,
    run,
  }
}
