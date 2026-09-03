import { ref } from 'vue'

const SCORE_VOICE_MUTED_STORAGE_KEY = 'volleyball_score_voice_muted_v1'
const REMOTE_XIAOXIAO_AUDIO_BASE = 'https://api.eunomia.cc/audio_xiaoxiao'

let activeAudioContext = null
let activePlaybackToken = 0

function resolveAudioUrl(name) {
  const key = name == null ? '' : String(name)
  if (!key) return ''
  const number = Number(key)
  if (Number.isInteger(number)) {
    if (number >= 31 && number <= 200) {
      return REMOTE_XIAOXIAO_AUDIO_BASE + '/' + number + '.mp3'
    }
    if (number > 200) {
      console.warn('[score voice] unsupported score audio:', number)
      return ''
    }
  }
  return '/static/audio_xiaoxiao/' + key + '.mp3'
}

function readMutedPreference() {
  try {
    return !!uni.getStorageSync(SCORE_VOICE_MUTED_STORAGE_KEY)
  } catch (_) {
    return false
  }
}

function writeMutedPreference(value) {
  try {
    uni.setStorageSync(SCORE_VOICE_MUTED_STORAGE_KEY, !!value)
  } catch (_) {
    // noop
  }
}

function stopActiveAudio() {
  activePlaybackToken += 1
  if (!activeAudioContext) return
  try {
    activeAudioContext.stop()
  } catch (_) {
    // noop
  }
  try {
    activeAudioContext.destroy()
  } catch (_) {
    // noop
  }
  activeAudioContext = null
}

function playAudio(src, token) {
  return new Promise((resolve) => {
    if (typeof uni === 'undefined' || typeof uni.createInnerAudioContext !== 'function') {
      resolve(false)
      return
    }

    const audio = uni.createInnerAudioContext()
    activeAudioContext = audio
    let settled = false

    const finish = (ok) => {
      if (settled) return
      settled = true
      if (activeAudioContext === audio) {
        activeAudioContext = null
      }
      try {
        audio.stop()
      } catch (_) {
        // noop
      }
      try {
        audio.destroy()
      } catch (_) {
        // noop
      }
      resolve(ok)
    }

    try {
      audio.obeyMuteSwitch = false
      audio.src = src
      audio.onEnded(() => {
        if (token !== activePlaybackToken) return finish(false)
        finish(true)
      })
      audio.onError(() => {
        finish(false)
      })
      audio.play()
    } catch (_) {
      finish(false)
    }
  })
}

async function playScoreSegments(segments) {
  if (!Array.isArray(segments) || !segments.length) return false
  const token = activePlaybackToken + 1
  stopActiveAudio()
  activePlaybackToken = token

  for (const name of segments) {
    if (token !== activePlaybackToken) return false
    const src = resolveAudioUrl(name)
    if (!src) continue
    const ok = await playAudio(src, token)
    if (!ok) {
      if (token !== activePlaybackToken) return false
      console.warn('[score voice] failed to play audio:', src)
      continue
    }
  }

  return true
}

export function useScoreAnnouncer() {
  const isMuted = ref(readMutedPreference())

  function setMuted(nextValue) {
    const next = !!nextValue
    if (isMuted.value === next) return
    isMuted.value = next
    writeMutedPreference(next)
    if (next) {
      stopActiveAudio()
    }
  }

  function toggleMuted() {
    setMuted(!isMuted.value)
  }

  function announceScore(scorerSide, scorerScore, opponentScore, options = {}) {
    if (scorerSide !== 'left' && scorerSide !== 'right') return false
    if (isMuted.value) return false
    const firstScore = Number(scorerScore)
    const secondScore = Number(opponentScore)
    if (!Number.isFinite(firstScore) || !Number.isFinite(secondScore)) return false

    const segments = []
    if (options.isServiceOver) {
      segments.push('change_serve')
    }
    segments.push(firstScore, 'bi', secondScore)
    if (options.isMatchPoint) {
      segments.push('match_point')
    } else if (options.isGamePoint) {
      segments.push('game_point')
    }
    return playScoreSegments(segments)
  }

  function destroyScoreAnnouncer() {
    stopActiveAudio()
  }

  return {
    isMuted,
    setMuted,
    toggleMuted,
    announceScore,
    destroyScoreAnnouncer,
  }
}
