import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { useActionLock } from '@/utils/interaction-guard'
import { request } from '@/utils/request'
import {
  buildHistoryEntry,
  buildLineupUrl,
  clearMatchState,
  cloneCourt,
  cloneLiberoSetup,
  cloneLiberoRuntime,
  createEmptyLiberoRuntime,
  createEmptyMatchState,
  formatTeamName,
  loadMatchState,
  MAX_HISTORY_ENTRIES,
  normalizeMatchState,
  normalizeParticipantSide,
  normalizeTeam,
  saveMatchState,
  swapMatchStateSides,
  toggleSide,
} from '../match-state'

const isThemeDebuggerEnabled = true
const THEME_DEBUG_STORAGE_KEY = 'volleyball_scoreboard_theme_debug_v1'
const THEME_MODE_STORAGE_KEY = 'volleyball_scoreboard_theme_mode_v1'
const THEME_DEVICE_PHONE = 'phone'
const THEME_DEVICE_PAD = 'pad'
const THEME_MODE_DARK = 'dark'
const THEME_MODE_LIGHT = 'light'
const DEFAULT_PHONE_DARK_THEME_DRAFT = Object.freeze({
  themeBase: '#003E50',
  themeBaseDeep: '#00123A',
  themeAccent: '#EC822F',
  themeAccentInk: '#194955',
  captain: '#2EC6FD',
  courtSurface: '#194955',
  rightScoreAccent: '#F49227',
  dangerAccent: '#F49227',
  textStrong: '#EEFFE0',
  surfaceGlass: '#002F00',
  shadowColor: '#000000',
  overlayMask: '#07121C',
  courtSlotAccent: '#F49227',
  rotationPanelSurface: '#005058',
})
const DEFAULT_PHONE_LIGHT_THEME_DRAFT = Object.freeze({
  themeBase: '#B7C5E4',
  themeBaseDeep: '#E8E8ED',
  themeAccent: '#860F36',
  themeAccentInk: '#194955',
  captain: '#146400',
  courtSurface: '#194955',
  rightScoreAccent: '#F49227',
  dangerAccent: '#DD0000',
  textStrong: '#190F55',
  surfaceGlass: '#003FBB',
  shadowColor: '#040000',
  overlayMask: '#05121C',
  courtSlotAccent: '#4D4527',
  rotationPanelSurface: '#C6C3FF',
})
const DEFAULT_PAD_DARK_THEME_DRAFT = Object.freeze({
  themeBase: '#225F6E',
  themeBaseDeep: '#143843',
  themeAccent: '#F4A53A',
  themeAccentInk: '#194955',
  captain: '#739C69',
  courtSurface: '#1E4F2B',
  rightScoreAccent: '#52C41A',
  dangerAccent: '#FF7A45',
  textStrong: '#FFFFFF',
  surfaceGlass: '#FFFFFF',
  shadowColor: '#000000',
  overlayMask: '#07121C',
  courtSlotAccent: '#008F8D',
  rotationPanelSurface: '#225F6E',
})
const DEFAULT_PAD_LIGHT_THEME_DRAFT = DEFAULT_PAD_DARK_THEME_DRAFT
const DEFAULT_THEME_DRAFT = DEFAULT_PHONE_DARK_THEME_DRAFT
const THEME_DEBUG_TOKENS = Object.freeze([
  { key: 'themeBase', label: '主背景' },
  { key: 'themeBaseDeep', label: '深背景' },
  { key: 'themeAccent', label: '强调色' },
  { key: 'themeAccentInk', label: '强调字色' },
  { key: 'captain', label: '队长高亮' },
  { key: 'courtSurface', label: '球场底色' },
  { key: 'rightScoreAccent', label: '右侧比分边框' },
  { key: 'dangerAccent', label: '危险按钮' },
  { key: 'textStrong', label: '主白字色' },
  { key: 'surfaceGlass', label: '面板玻璃色' },
  { key: 'shadowColor', label: '阴影色' },
  { key: 'overlayMask', label: '遮罩色' },
  { key: 'courtSlotAccent', label: '球场描边色' },
  { key: 'rotationPanelSurface', label: '轮次大框背景' },
])
const RGB_CHANNELS = Object.freeze([
  { key: 'r', label: 'R' },
  { key: 'g', label: 'G' },
  { key: 'b', label: 'B' },
])

function normalizeHexColor(value) {
  const text = String(value || '').trim().replace(/^#/, '')
  if (/^[0-9a-fA-F]{3}$/.test(text)) {
    return `#${text.split('').map((item) => item + item).join('').toUpperCase()}`
  }
  if (/^[0-9a-fA-F]{6}$/.test(text)) {
    return `#${text.toUpperCase()}`
  }
  return ''
}

function clampColorChannel(value) {
  return Math.max(0, Math.min(255, Math.round(Number(value) || 0)))
}

function hexToRgb(hex) {
  const normalized = normalizeHexColor(hex) || '#000000'
  const value = normalized.slice(1)
  return {
    r: parseInt(value.slice(0, 2), 16),
    g: parseInt(value.slice(2, 4), 16),
    b: parseInt(value.slice(4, 6), 16),
  }
}

function rgbToHex(rgb) {
  const channels = ['r', 'g', 'b'].map((key) => clampColorChannel(rgb[key]).toString(16).padStart(2, '0').toUpperCase())
  return `#${channels.join('')}`
}

function toRgbText(hex) {
  const rgb = hexToRgb(hex)
  return `${rgb.r}, ${rgb.g}, ${rgb.b}`
}

function getDefaultThemeDraftByDevice(device, mode = THEME_MODE_DARK) {
  if (device === THEME_DEVICE_PAD) {
    return mode === THEME_MODE_LIGHT ? DEFAULT_PAD_LIGHT_THEME_DRAFT : DEFAULT_PAD_DARK_THEME_DRAFT
  }
  return mode === THEME_MODE_LIGHT ? DEFAULT_PHONE_LIGHT_THEME_DRAFT : DEFAULT_PHONE_DARK_THEME_DRAFT
}

function normalizeThemeMode(mode) {
  return mode === THEME_MODE_LIGHT ? THEME_MODE_LIGHT : THEME_MODE_DARK
}

function cloneThemeDraft(source, fallbackDevice = THEME_DEVICE_PHONE, fallbackMode = THEME_MODE_DARK) {
  const fallbackDraft = getDefaultThemeDraftByDevice(fallbackDevice, fallbackMode)
  return THEME_DEBUG_TOKENS.reduce((state, item) => {
    state[item.key] = normalizeHexColor(source?.[item.key]) || fallbackDraft[item.key]
    return state
  }, {})
}

export function useScoreboard() {
  const loading = ref(true)
  const isError = ref(false)
  const errorText = ref('加载失败')
  const tournamentId = ref('')
  const matchId = ref('')
  const info = ref({})
  const leftTeam = ref({ name: '主队', members: [] })
  const rightTeam = ref({ name: '客队', members: [] })
  const pageQuery = ref({})
  const displaySideSwapped = ref(false)
  const screenLeftParticipantSide = ref('left')

  const leftScore = ref(0)
  const rightScore = ref(0)
  const leftGameWins = ref(0)
  const rightGameWins = ref(0)
  const currentGameNo = ref(1)
  const gameScores = ref([])
  const serveSide = ref('left')
  const currentGameStartServeSide = ref('left')
  const leftTimeouts = ref(2)
  const rightTimeouts = ref(2)
  const leftCourt = ref(Array(6).fill(''))
  const rightCourt = ref(Array(6).fill(''))
  const baseLeftCourt = ref(Array(6).fill(''))
  const baseRightCourt = ref(Array(6).fill(''))
  const leftLiberoSetup = ref({ pairIndexes: [], libero1Id: '', libero2Id: '' })
  const rightLiberoSetup = ref({ pairIndexes: [], libero1Id: '', libero2Id: '' })
  const leftLiberoRuntime = ref(createEmptyLiberoRuntime())
  const rightLiberoRuntime = ref(createEmptyLiberoRuntime())
  const leftCaptainMemberId = ref('')
  const rightCaptainMemberId = ref('')
  const matchEvents = ref([])
  const nextEventSeq = ref(1)
  const lastSyncedEventSeq = ref(0)
  const lineupReady = ref(false)
  const finalGameSideSwitchPending = ref(false)
  const finalGameSideSwitchHandled = ref(false)
  const keepCurrentDisplaySideCountdown = ref(0)
  const resetMatchCountdown = ref(0)
  const historyStack = ref([])
  const retiredSide = ref('')
  const matchEnded = ref(false)
  const winnerName = ref('')
  const selectedBench = ref({ side: '', memberId: '' })
  const captainPromptQueue = ref([])
  const captainCandidateMemberId = ref('')
  const windowWidth = ref(0)
  const windowHeight = ref(0)

  const isH5PortraitPreview = ref(false)
  const previewScale = ref(1)
  const previewOffsetX = ref(0)
  const previewOffsetY = ref(0)
  const themeServerSaving = ref(false)
  const themeDebuggerCollapsed = ref(true)
  const themeMode = ref(THEME_MODE_DARK)
  const isThemeModePickerOpen = ref(false)
  const activeThemeToken = ref(THEME_DEBUG_TOKENS[0].key)
  const themeDraft = reactive({ ...DEFAULT_THEME_DRAFT })
  const themeHexInputs = reactive(buildThemeHexInputState(DEFAULT_THEME_DRAFT))
  const themeServerDrafts = reactive({
    phone: {
      dark: null,
      light: null,
    },
    pad: {
      dark: null,
      light: null,
    },
    legacy: null,
  })
  const rgbChannels = RGB_CHANNELS
  const { locked: resetMatchRunning, run: runResetMatch } = useActionLock()

  let eventFlushTimer = null
  let eventFlushPromise = null
  let keepCurrentDisplaySideTimer = null
  let resetMatchCountdownTimer = null

  const currentTargetPoints = computed(() => {
    const finalGameNo = Number(info.value.bestOf || 3)
    return currentGameNo.value === finalGameNo ? 15 : 25
  })

  const isLocked = computed(() => !!retiredSide.value || matchEnded.value)
  const isDecidingGame = computed(() => currentGameNo.value === Number(info.value.bestOf || 3))
  const leftDisplayTeam = computed(() => leftTeam.value)
  const rightDisplayTeam = computed(() => rightTeam.value)
  const leftDisplayTeamName = computed(() => formatTeamName(leftDisplayTeam.value.name))
  const rightDisplayTeamName = computed(() => formatTeamName(rightDisplayTeam.value.name))
  const leftDisplayScore = computed(() => leftScore.value)
  const rightDisplayScore = computed(() => rightScore.value)
  const leftDisplayGameWins = computed(() => leftGameWins.value)
  const rightDisplayGameWins = computed(() => rightGameWins.value)
  const displayServeSide = computed(() => serveSide.value)
  const winnerDisplayName = computed(() => formatTeamName(winnerName.value))
  const leftCourtDisplaySlots = computed(() => buildCourtDisplaySlots('left'))
  const rightCourtDisplaySlots = computed(() => buildCourtDisplaySlots('right'))
  const captainPromptSide = computed(() => captainPromptQueue.value[0] || '')
  const isCaptainPromptActive = computed(() => !!captainPromptSide.value)
  const captainPromptCandidates = computed(() => buildOnCourtMembers(captainPromptSide.value))
  const isFinalGameSideSwitchPromptActive = computed(() => finalGameSideSwitchPending.value)
  const canKeepCurrentDisplaySide = computed(() => isFinalGameSideSwitchPromptActive.value && keepCurrentDisplaySideCountdown.value === 0)
  const keepCurrentDisplaySideLabel = computed(() => (
    keepCurrentDisplaySideCountdown.value > 0
      ? `保持当前位置(${keepCurrentDisplaySideCountdown.value})`
      : '保持当前位置'
  ))
  const canResetMatch = computed(() => isLocked.value && resetMatchCountdown.value === 0 && !resetMatchRunning.value)
  const resetMatchLabel = computed(() => (
    resetMatchCountdown.value > 0
      ? `重新开始(${resetMatchCountdown.value})`
      : '重新开始'
  ))
  const captainPromptTeamName = computed(() => {
    if (captainPromptSide.value === 'left') return leftDisplayTeamName.value
    if (captainPromptSide.value === 'right') return rightDisplayTeamName.value
    return ''
  })
  const orientation = computed(() => (windowWidth.value >= windowHeight.value ? 'landscape' : 'portrait'))
  const isTablet = computed(() => Math.min(windowWidth.value || 0, windowHeight.value || 0) >= 720)
  const themeDevice = computed(() => (isTablet.value ? THEME_DEVICE_PAD : THEME_DEVICE_PHONE))
  const sizeBand = computed(() => {
    if (!isTablet.value) return 'phone'
    if (orientation.value === 'portrait') {
      return windowWidth.value <= 820 ? 'pad-portrait-sm' : 'pad-portrait-lg'
    }
    if (windowWidth.value <= 1228) return 'pad-landscape-sm'
    if (windowWidth.value <= 1400) return 'pad-landscape-md'
    return 'pad-landscape-lg'
  })
  const pageClassNames = computed(() => [
    isTablet.value ? 'is-tablet' : 'is-phone',
    `is-${orientation.value}`,
    sizeBand.value,
    `theme-${themeMode.value}`,
  ])
  const themeModeOptions = Object.freeze([
    { key: THEME_MODE_DARK, label: '深色' },
    { key: THEME_MODE_LIGHT, label: '浅色' },
  ])
  const themeModeLabel = computed(() => (themeMode.value === THEME_MODE_LIGHT ? '背景色b' : '背景色a'))
  const themeTokenOptions = computed(() => THEME_DEBUG_TOKENS.map((item) => ({
    ...item,
    value: themeDraft[item.key],
  })))
  const activeThemeTokenMeta = computed(() => THEME_DEBUG_TOKENS.find((item) => item.key === activeThemeToken.value) || THEME_DEBUG_TOKENS[0])
  const activeThemeRgb = computed(() => hexToRgb(themeDraft[activeThemeToken.value] || DEFAULT_THEME_DRAFT.themeBase))
  const themeStyleVars = computed(() => buildThemeStyleVars(themeDraft))
  const sliderTrackBackgroundColor = computed(() => `rgba(${toRgbText(themeDraft.surfaceGlass)}, 0.18)`)
  const useLandscapePreview = computed(() => isH5PortraitPreview.value && lineupReady.value)
  const previewPageStyle = computed(() => {
    if (!useLandscapePreview.value) return {}
    return {
      transform: `translate(${previewOffsetX.value}px, ${previewOffsetY.value}px) scale(${previewScale.value})`,
      transformOrigin: 'top left',
    }
  })
  const rootPageStyle = computed(() => ({
    ...previewPageStyle.value,
    ...themeStyleVars.value,
  }))

  const displayGameScores = computed(() => gameScores.value.map((item) => ({ ...item })))
  const finishedGameScores = computed(() => displayGameScores.value.map((item) => `${item.leftScore}:${item.rightScore}`))
  const scoreSummary = computed(() => finishedGameScores.value.join(', '))
  const finalGameSideSwitchScoreText = computed(() => `${leftDisplayScore.value}:${rightDisplayScore.value}`)

  function buildThemeStyleVars(draft) {
    return {
      '--theme-base': draft.themeBase,
      '--theme-base-deep': draft.themeBaseDeep,
      '--theme-accent': draft.themeAccent,
      '--theme-accent-ink': draft.themeAccentInk,
      '--theme-base-rgb': toRgbText(draft.themeBase),
      '--theme-base-deep-rgb': toRgbText(draft.themeBaseDeep),
      '--theme-accent-rgb': toRgbText(draft.themeAccent),
      '--captain': draft.captain,
      '--captain-rgb': toRgbText(draft.captain),
      '--court-surface': draft.courtSurface,
      '--right-score-accent-rgb': toRgbText(draft.rightScoreAccent),
      '--danger-accent-rgb': toRgbText(draft.dangerAccent),
      '--text-strong': draft.textStrong,
      '--text-strong-rgb': toRgbText(draft.textStrong),
      '--surface-glass': draft.surfaceGlass,
      '--surface-glass-rgb': toRgbText(draft.surfaceGlass),
      '--shadow-color-rgb': toRgbText(draft.shadowColor),
      '--overlay-mask-rgb': toRgbText(draft.overlayMask),
      '--court-slot-accent-rgb': toRgbText(draft.courtSlotAccent),
      '--rotation-panel-surface-rgb': toRgbText(draft.rotationPanelSurface),
    }
  }

  function buildThemeHexInputState(source) {
    return THEME_DEBUG_TOKENS.reduce((state, item) => {
      state[item.key] = normalizeHexColor(source[item.key]) || DEFAULT_THEME_DRAFT[item.key]
      return state
    }, {})
  }

  function normalizeThemeDevice(device) {
    return device === THEME_DEVICE_PAD ? THEME_DEVICE_PAD : THEME_DEVICE_PHONE
  }

  function themeDraftSnapshot() {
    return THEME_DEBUG_TOKENS.reduce((state, item) => {
      state[item.key] = themeDraft[item.key]
      return state
    }, {})
  }

  function getThemeStorageKey(device = themeDevice.value, mode = themeMode.value) {
    const normalizedDevice = normalizeThemeDevice(device)
    const normalizedMode = normalizeThemeMode(mode)
    const matchKey = matchId.value || 'default'
    return `${THEME_DEBUG_STORAGE_KEY}_${matchKey}_${normalizedDevice}_${normalizedMode}`
  }

  function getThemeModeStorageKey() {
    const matchKey = matchId.value || 'default'
    return `${THEME_MODE_STORAGE_KEY}_${matchKey}`
  }

  function readThemeDraftFromStorage(device = themeDevice.value, mode = themeMode.value, includeLegacy = false) {
    try {
      const normalizedMode = normalizeThemeMode(mode)
      const cached = uni.getStorageSync(getThemeStorageKey(device, mode))
      if (cached && typeof cached === 'object') {
        return cached
      }
      if (!includeLegacy || normalizedMode !== THEME_MODE_DARK) {
        return null
      }
      const legacyDeviceCache = uni.getStorageSync(`${THEME_DEBUG_STORAGE_KEY}_${normalizeThemeDevice(device)}`)
      if (legacyDeviceCache && typeof legacyDeviceCache === 'object') {
        return legacyDeviceCache
      }
      const legacyCached = uni.getStorageSync(THEME_DEBUG_STORAGE_KEY)
      return legacyCached && typeof legacyCached === 'object' ? legacyCached : null
    } catch (_) {
      return null
    }
  }

  function readThemeModeFromStorage() {
    try {
      const cached = uni.getStorageSync(getThemeModeStorageKey())
      return normalizeThemeMode(cached)
    } catch (_) {
      return THEME_MODE_DARK
    }
  }

  function persistThemeMode(nextMode = themeMode.value) {
    try {
      uni.setStorageSync(getThemeModeStorageKey(), normalizeThemeMode(nextMode))
    } catch (_) {
      // ignore theme mode cache errors
    }
  }

  function getServerThemeDraft(device = themeDevice.value, mode = themeMode.value) {
    const normalizedDevice = normalizeThemeDevice(device)
    const normalizedMode = normalizeThemeMode(mode)
    return themeServerDrafts[normalizedDevice]?.[normalizedMode] || null
  }

  function resolveThemeDraftForDevice(device = themeDevice.value, mode = themeMode.value, options = {}) {
    const normalizedDevice = normalizeThemeDevice(device)
    const normalizedMode = normalizeThemeMode(mode)
    if (options.preferStorage !== false) {
      const cachedDraft = readThemeDraftFromStorage(normalizedDevice, normalizedMode, options.includeLegacy === true)
      if (cachedDraft) {
        return cloneThemeDraft(cachedDraft, normalizedDevice, normalizedMode)
      }
    }

    const serverDraft = getServerThemeDraft(normalizedDevice, normalizedMode)
    if (serverDraft) {
      return cloneThemeDraft(serverDraft, normalizedDevice, normalizedMode)
    }

    if (normalizedDevice === THEME_DEVICE_PHONE && normalizedMode === THEME_MODE_LIGHT) {
      return cloneThemeDraft(DEFAULT_PHONE_LIGHT_THEME_DRAFT, normalizedDevice, normalizedMode)
    }

    if (normalizedMode === THEME_MODE_DARK && options.includeLegacy === true && themeServerDrafts.legacy) {
      return cloneThemeDraft(themeServerDrafts.legacy, normalizedDevice, normalizedMode)
    }

    return null
  }

  function applyThemeDraftForDevice(device = themeDevice.value, mode = themeMode.value, options = {}) {
    const normalizedDevice = normalizeThemeDevice(device)
    const normalizedMode = normalizeThemeMode(mode)
    const nextDraft = resolveThemeDraftForDevice(normalizedDevice, normalizedMode, options) || getDefaultThemeDraftByDevice(normalizedDevice, normalizedMode)
    applyThemeDraft(nextDraft)
    if (options.persistApplied === true) {
      persistThemeDraft(normalizedDevice, normalizedMode)
    }
  }

  function syncThemeHexInputs() {
    for (const item of THEME_DEBUG_TOKENS) {
      themeHexInputs[item.key] = themeDraft[item.key]
    }
  }

  function persistThemeDraft(device = themeDevice.value, mode = themeMode.value) {
    if (!isThemeDebuggerEnabled) return
    try {
      uni.setStorageSync(getThemeStorageKey(device, mode), themeDraftSnapshot())
    } catch (_) {
      // ignore theme debug cache errors
    }
  }

  function applyThemeDraft(nextDraft) {
    const fallbackDraft = getDefaultThemeDraftByDevice(themeDevice.value, themeMode.value)
    for (const item of THEME_DEBUG_TOKENS) {
      themeDraft[item.key] = normalizeHexColor(nextDraft?.[item.key]) || fallbackDraft[item.key]
    }
    syncThemeHexInputs()
  }

  function restoreThemeDraft(device = themeDevice.value, mode = themeMode.value) {
    if (!isThemeDebuggerEnabled) return
    applyThemeDraftForDevice(device, mode, {
      preferStorage: true,
      includeLegacy: true,
      persistApplied: false,
    })
  }

  function setThemeTokenColor(key, value, options = {}) {
    const normalized = normalizeHexColor(value)
    if (!normalized || !Object.prototype.hasOwnProperty.call(themeDraft, key)) {
      return false
    }
    themeDraft[key] = normalized
    if (options.syncInput !== false) {
      themeHexInputs[key] = normalized
    }
    if (options.persist !== false) {
      persistThemeDraft(themeDevice.value, themeMode.value)
    }
    return true
  }

  function setActiveThemeToken(key) {
    if (themeDraft[key]) {
      activeThemeToken.value = key
    }
  }

  function handleThemeHexInput(key, value) {
    themeHexInputs[key] = String(value || '').toUpperCase()
    const applied = setThemeTokenColor(key, value, { syncInput: false })
    if (applied) {
      themeHexInputs[key] = themeDraft[key]
    }
  }

  function normalizeThemeHexInput(key) {
    themeHexInputs[key] = themeDraft[key]
  }

  function updateThemeChannel(channelKey, value, persist) {
    const nextRgb = {
      ...activeThemeRgb.value,
      [channelKey]: clampColorChannel(value),
    }
    setThemeTokenColor(activeThemeToken.value, rgbToHex(nextRgb), {
      persist,
      syncInput: true,
    })
  }

  function previewActiveThemeChannel(channelKey, value) {
    updateThemeChannel(channelKey, value, false)
  }

  function commitActiveThemeChannel(channelKey, value) {
    updateThemeChannel(channelKey, value, true)
  }

  function toggleThemeDebugger() {
    themeDebuggerCollapsed.value = !themeDebuggerCollapsed.value
  }

  function openThemeModePicker() {
    uni.showActionSheet({
      itemList: themeModeOptions.map((item) => item.label),
      success: ({ tapIndex }) => {
        const selected = themeModeOptions[tapIndex]
        if (selected) {
          setThemeMode(selected.key)
        }
      },
    })
  }

  function closeThemeModePicker() {
    isThemeModePickerOpen.value = false
  }

  function setThemeMode(nextMode) {
    const normalizedMode = normalizeThemeMode(nextMode)
    if (themeMode.value === normalizedMode) {
      closeThemeModePicker()
      persistThemeMode(normalizedMode)
      return
    }
    themeMode.value = normalizedMode
    persistThemeMode(normalizedMode)
    restoreThemeDraft(themeDevice.value, normalizedMode)
    closeThemeModePicker()
  }

  function resetThemeDraft() {
    const normalizedDevice = normalizeThemeDevice(themeDevice.value)
    const normalizedMode = normalizeThemeMode(themeMode.value)
    const resetDraft = resolveThemeDraftForDevice(normalizedDevice, normalizedMode, {
      preferStorage: false,
      includeLegacy: true,
    }) || getDefaultThemeDraftByDevice(normalizedDevice, normalizedMode)
    applyThemeDraft(resetDraft)
    try {
      uni.removeStorageSync(getThemeStorageKey(normalizedDevice, normalizedMode))
    } catch (_) {
      // ignore theme debug cache errors
    }
    uni.showToast({
      title: '已重置本地配色',
      icon: 'none',
      duration: 1200,
    })
  }

  async function loadThemeDraftFromServer() {
    if (!matchId.value) return false
    try {
      const data = await request('/api/v1/matches/' + matchId.value + '/theme-config', {
        method: 'GET',
        silent: true,
      })
      themeServerDrafts.phone.dark = data?.phoneTheme && typeof data.phoneTheme === 'object' ? data.phoneTheme : null
      themeServerDrafts.phone.light = data?.phoneLightTheme && typeof data.phoneLightTheme === 'object' ? data.phoneLightTheme : null
      themeServerDrafts.pad.dark = data?.padTheme && typeof data.padTheme === 'object' ? data.padTheme : null
      themeServerDrafts.pad.light = data?.padLightTheme && typeof data.padLightTheme === 'object' ? data.padLightTheme : null
      themeServerDrafts.legacy = data?.theme && typeof data.theme === 'object' ? data.theme : null
      const nextDraft = resolveThemeDraftForDevice(themeDevice.value, themeMode.value, {
        preferStorage: false,
        includeLegacy: true,
      })
      if (!nextDraft) {
        return false
      }
      applyThemeDraft(nextDraft)
      persistThemeDraft()
      return true
    } catch (_) {
      return false
    }
  }

  async function saveThemeDraftToServer() {
    if (!matchId.value) {
      uni.showToast({
        title: '缺少比赛ID',
        icon: 'none',
        duration: 1500,
      })
      return
    }
    if (themeServerSaving.value) return

    themeServerSaving.value = true
    try {
      await request('/api/v1/matches/' + matchId.value + '/theme-config', {
        method: 'PUT',
        data: {
          device: themeDevice.value,
          mode: themeMode.value,
          theme: themeDraftSnapshot(),
        },
      })
      themeServerDrafts[themeDevice.value][themeMode.value] = themeDraftSnapshot()
      uni.showToast({
        title: '已保存到后端',
        icon: 'success',
        duration: 1200,
      })
    } catch (_) {
      // request handles toast
    } finally {
      themeServerSaving.value = false
    }
  }

  function buildThemeVarExport() {
    const vars = buildThemeStyleVars(themeDraftSnapshot())
    return [
      '.state-page,',
      '.scoreboard-page {',
      `  --theme-base-rgb: ${vars['--theme-base-rgb']};`,
      `  --theme-base-deep-rgb: ${vars['--theme-base-deep-rgb']};`,
      `  --theme-base: ${vars['--theme-base']};`,
      `  --theme-base-deep: ${vars['--theme-base-deep']};`,
      `  --theme-accent-rgb: ${vars['--theme-accent-rgb']};`,
      `  --theme-accent: ${vars['--theme-accent']};`,
      `  --theme-accent-ink: ${vars['--theme-accent-ink']};`,
      `  --captain-rgb: ${vars['--captain-rgb']};`,
      `  --captain: ${vars['--captain']};`,
      `  --court-surface: ${vars['--court-surface']};`,
      `  --right-score-accent-rgb: ${vars['--right-score-accent-rgb']};`,
      `  --danger-accent-rgb: ${vars['--danger-accent-rgb']};`,
      `  --text-strong: ${vars['--text-strong']};`,
      `  --text-strong-rgb: ${vars['--text-strong-rgb']};`,
      `  --surface-glass: ${vars['--surface-glass']};`,
      `  --surface-glass-rgb: ${vars['--surface-glass-rgb']};`,
      `  --shadow-color-rgb: ${vars['--shadow-color-rgb']};`,
      `  --overlay-mask-rgb: ${vars['--overlay-mask-rgb']};`,
      `  --court-slot-accent-rgb: ${vars['--court-slot-accent-rgb']};`,
      `  --rotation-panel-surface-rgb: ${vars['--rotation-panel-surface-rgb']};`,
      '}',
    ].join('\n')
  }

  function copyThemeVariables() {
    uni.setClipboardData({
      data: buildThemeVarExport(),
      success: () => {
        uni.showToast({
          title: '已复制变量',
          icon: 'success',
          duration: 1200,
        })
      },
    })
  }

  function toActualSide(side) {
    return side === 'right' ? 'right' : 'left'
  }

  function toDisplaySide(side) {
    return side === 'right' ? 'right' : 'left'
  }

  function getParticipantSideByScreenSide(side) {
    const normalizedSide = side === 'right' ? 'right' : 'left'
    if (screenLeftParticipantSide.value === 'right') {
      return toggleSide(normalizedSide)
    }
    return normalizedSide
  }

  function getScreenSideByParticipantSide(side) {
    const normalizedSide = normalizeParticipantSide(side)
    if (screenLeftParticipantSide.value === 'right') {
      return toggleSide(normalizedSide)
    }
    return normalizedSide
  }

  function toParticipantWinnerSide(screenSide) {
    return getParticipantSideByScreenSide(screenSide)
  }

  function toParticipantGameScore(item = {}) {
    if (screenLeftParticipantSide.value === 'right') {
      return {
        ...item,
        leftScore: Number(item.rightScore || 0),
        rightScore: Number(item.leftScore || 0),
        winnerSide: item.winnerSide ? toggleSide(item.winnerSide) : item.winnerSide,
      }
    }
    return {
      ...item,
      leftScore: Number(item.leftScore || 0),
      rightScore: Number(item.rightScore || 0),
      winnerSide: item.winnerSide || '',
    }
  }

  function toParticipantStateSnapshot(rawState) {
    const normalized = normalizeMatchState(rawState)
    if (normalizeParticipantSide(normalized.screenLeftParticipantSide) === 'left') {
      return normalized
    }
    return swapMatchStateSides({
      ...normalized,
      screenLeftParticipantSide: 'right',
    })
  }

  function applyWindowMetrics(size = {}) {
    const nextWidth = Number(size.windowWidth || size.width || 0)
    const nextHeight = Number(size.windowHeight || size.height || 0)
    if (nextWidth > 0) {
      windowWidth.value = nextWidth
    }
    if (nextHeight > 0) {
      windowHeight.value = nextHeight
    }
  }

  function syncWindowMetrics() {
    try {
      if (typeof uni.getWindowInfo === 'function') {
        applyWindowMetrics(uni.getWindowInfo())
        return
      }
      if (typeof uni.getSystemInfoSync === 'function') {
        const info = uni.getSystemInfoSync()
        applyWindowMetrics({
          windowWidth: info.windowWidth,
          windowHeight: info.windowHeight,
        })
      }
    } catch (_) {
      // ignore metric errors
    }
  }

  function handleWindowResize(res) {
    applyWindowMetrics(res?.size || res || {})
    updateH5PortraitPreview()
  }

  function updateH5PortraitPreview() {
    // #ifdef H5
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight
    isH5PortraitPreview.value = viewportHeight > viewportWidth
    if (!isH5PortraitPreview.value) {
      previewScale.value = 1
      previewOffsetX.value = 0
      previewOffsetY.value = 0
      return
    }
    const designWidth = 1280
    const designHeight = 720
    const scale = Math.min(viewportWidth / designWidth, viewportHeight / designHeight)
    previewScale.value = scale
    previewOffsetX.value = (viewportWidth - designWidth * scale) / 2
    previewOffsetY.value = (viewportHeight - designHeight * scale) / 2
    // #endif
  }

  function buildCourtDisplaySlots(side) {
    const actualSide = toActualSide(side)
    const court = actualSide === 'right' ? rightCourt.value : leftCourt.value
    const labels = side === 'right'
      ? ['2号位', '1号位', '3号位', '6号位', '4号位', '5号位']
      : ['5号位', '4号位', '6号位', '3号位', '1号位', '2号位']
    const order = side === 'right'
      ? [2, 5, 1, 4, 0, 3]
      : [3, 0, 4, 1, 5, 2]
    return order.map((dataIndex, index) => ({
      key: `${side}_${dataIndex}`,
      dataIndex,
      label: labels[index],
      memberId: court[dataIndex] || '',
      isLibero: isActiveLiberoOnSlot(side, dataIndex, court[dataIndex] || ''),
    }))
  }

  const SLOT_OPPOSITE_MAP = {
    0: 5,
    1: 4,
    2: 3,
    3: 2,
    4: 1,
    5: 0,
  }

  function getCourtBySide(side) {
    const actualSide = toActualSide(side)
    return actualSide === 'right' ? rightCourt.value : leftCourt.value
  }

  function setCourtBySide(side, court) {
    const actualSide = toActualSide(side)
    if (actualSide === 'right') {
      rightCourt.value = court
    } else {
      leftCourt.value = court
    }
  }

  function getBaseCourtBySide(side) {
    const actualSide = toActualSide(side)
    return actualSide === 'right' ? baseRightCourt.value : baseLeftCourt.value
  }

  function getLiberoSetupBySide(side) {
    const actualSide = toActualSide(side)
    return actualSide === 'right' ? rightLiberoSetup.value : leftLiberoSetup.value
  }

  function getLiberoRuntimeBySide(side) {
    const actualSide = toActualSide(side)
    return actualSide === 'right' ? rightLiberoRuntime.value : leftLiberoRuntime.value
  }

  function setLiberoRuntimeBySide(side, runtime) {
    const normalized = cloneLiberoRuntime(runtime)
    const actualSide = toActualSide(side)
    if (actualSide === 'right') {
      rightLiberoRuntime.value = normalized
    } else {
      leftLiberoRuntime.value = normalized
    }
  }

  function getCaptainBySide(side) {
    const actualSide = toActualSide(side)
    return actualSide === 'right' ? rightCaptainMemberId.value : leftCaptainMemberId.value
  }

  function setCaptainBySide(side, memberId) {
    const actualSide = toActualSide(side)
    if (actualSide === 'right') {
      rightCaptainMemberId.value = memberId || ''
    } else {
      leftCaptainMemberId.value = memberId || ''
    }
  }

  function originalCaptainMemberId(side) {
    const actualSide = toActualSide(side)
    const team = actualSide === 'right' ? rightTeam.value : leftTeam.value
    return (team.members || []).find((member) => member.captain)?.id || ''
  }

  function buildOnCourtMembers(side) {
    if (!side) return []
    const court = getCourtBySide(side)
    const positionLabels = ['4号位', '3号位', '2号位', '5号位', '6号位', '1号位']
    return court
      .map((memberId, index) => {
        const member = memberById(side, memberId)
        if (!member) return null
        return {
          ...member,
          slotIndex: index,
          positionLabel: positionLabels[index] || '',
        }
      })
      .filter(Boolean)
  }

  function isCurrentCaptain(side, memberId) {
    return !!memberId && getCaptainBySide(side) === memberId
  }

  function removeCaptainPrompt(side) {
    if (!side) return
    captainPromptQueue.value = captainPromptQueue.value.filter((item) => item !== side)
    captainCandidateMemberId.value = captainPromptCandidates.value[0]?.id || ''
  }

  function ensureCaptainPrompt(side) {
    if (!side) return
    if (!captainPromptQueue.value.includes(side)) {
      captainPromptQueue.value = [...captainPromptQueue.value, side]
    }
    if (!captainCandidateMemberId.value) {
      captainCandidateMemberId.value = buildOnCourtMembers(side)[0]?.id || ''
    }
  }

  function clonePayload(payload) {
    return JSON.parse(JSON.stringify(payload || {}))
  }

  function hasPendingEvents() {
    return matchEvents.value.some((item) => item.syncStatus !== 'synced')
  }

  function appendMatchEvent(type, payload, options = {}) {
    const participantPayload = clonePayload(payload)
    const event = {
      seq: nextEventSeq.value,
      type,
      gameNo: currentGameNo.value,
      leftScore: toParticipantGameScore({ leftScore: leftScore.value, rightScore: rightScore.value }).leftScore,
      rightScore: toParticipantGameScore({ leftScore: leftScore.value, rightScore: rightScore.value }).rightScore,
      serveSide: getParticipantSideByScreenSide(serveSide.value),
      payload: participantPayload,
      syncStatus: 'pending',
    }
    matchEvents.value.push(event)
    nextEventSeq.value += 1
    if (options.scheduleFlush !== false) {
      scheduleEventFlush()
    }
    return event
  }

  function scheduleEventFlush(delay = 800) {
    if (!matchId.value || !hasPendingEvents()) return
    if (eventFlushTimer) {
      clearTimeout(eventFlushTimer)
    }
    eventFlushTimer = setTimeout(() => {
      eventFlushTimer = null
      flushPendingEvents()
    }, delay)
  }

  async function flushPendingEvents() {
    if (!matchId.value || !hasPendingEvents()) {
      return true
    }
    if (eventFlushPromise) {
      return eventFlushPromise
    }

    const pendingEvents = matchEvents.value
      .filter((item) => item.syncStatus !== 'synced')
      .map((item) => ({
        eventSeq: item.seq,
        eventType: item.type,
        gameNo: item.gameNo,
        leftScore: item.leftScore,
        rightScore: item.rightScore,
        serveSide: item.serveSide,
        payloadJson: JSON.stringify(item.payload || {}),
      }))

    if (!pendingEvents.length) {
      return true
    }

    eventFlushPromise = request('/api/v1/matches/' + matchId.value + '/events', {
      method: 'PUT',
      data: { events: pendingEvents },
      silent: true,
    })
      .then(() => {
        const syncedSeqs = new Set(pendingEvents.map((item) => item.eventSeq))
        let maxSyncedSeq = lastSyncedEventSeq.value
        matchEvents.value = matchEvents.value.map((item) => {
          if (!syncedSeqs.has(item.seq)) return item
          maxSyncedSeq = Math.max(maxSyncedSeq, item.seq)
          return {
            ...item,
            syncStatus: 'synced',
          }
        })
        lastSyncedEventSeq.value = maxSyncedSeq
        persistState()
        return true
      })
      .catch(() => false)
      .finally(() => {
        eventFlushPromise = null
      })

    return eventFlushPromise
  }

  function getRuntimeRoleEntries(runtime, setup) {
    return [
      {
        slotField: 'role1SlotIndex',
        playerField: 'role1PlayerId',
        liberoId: setup.libero1Id || '',
      },
      {
        slotField: 'role2SlotIndex',
        playerField: 'role2PlayerId',
        liberoId: setup.libero2Id || '',
      },
    ].map((item) => ({
      ...item,
      slotIndex: runtime[item.slotField],
      playerId: runtime[item.playerField] || '',
    }))
  }

  function getBoundLiberoIds(setup) {
    return [setup.libero1Id || '', setup.libero2Id || ''].filter(Boolean)
  }

  function isOppositePair(slotIndex, oppositeIndex) {
    return Number.isInteger(slotIndex) && slotIndex >= 0 && slotIndex < 6 && oppositeSlotIndex(slotIndex) === oppositeIndex
  }

  function buildRoleSeeds(side, setup) {
    const baseCourt = cloneCourt(getBaseCourtBySide(side))
    return setup.pairIndexes
      .map((slotIndex) => {
        const memberId = baseCourt[slotIndex] || ''
        const member = memberById(side, memberId)
        return {
          slotIndex,
          memberId,
          jerseyNumber: Number(member?.jerseyNumber || 999),
        }
      })
      .filter((item) => item.memberId)
      .sort((left, right) => {
        if (left.jerseyNumber !== right.jerseyNumber) {
          return left.jerseyNumber - right.jerseyNumber
        }
        return left.slotIndex - right.slotIndex
      })
  }

  function runtimeHasDuplicateCourtMembers(side) {
    const seen = new Set()
    for (const memberId of cloneCourt(getCourtBySide(side)).filter(Boolean)) {
      if (seen.has(memberId)) {
        return true
      }
      seen.add(memberId)
    }
    return false
  }

  function isLiberoRuntimeComplete(runtime) {
    return (
      runtime.role1SlotIndex >= 0 &&
      runtime.role2SlotIndex >= 0 &&
      !!runtime.role1PlayerId &&
      !!runtime.role2PlayerId
    )
  }

  function isTeamLiberoRuntimeValid(side, runtime, setup) {
    if (setup.pairIndexes.length !== 2) {
      return false
    }
    if (!isOppositePair(setup.pairIndexes[0], setup.pairIndexes[1])) {
      return false
    }
    if (!isLiberoRuntimeComplete(runtime)) {
      return false
    }
    if (!isOppositePair(runtime.role1SlotIndex, runtime.role2SlotIndex)) {
      return false
    }

    const memberIds = new Set(Array.from(memberMap(side).values()).map((member) => member.id))
    const boundLiberoIds = new Set(getBoundLiberoIds(setup))
    if (!memberIds.has(runtime.role1PlayerId) || !memberIds.has(runtime.role2PlayerId)) {
      return false
    }
    if (runtime.role1PlayerId === runtime.role2PlayerId) {
      return false
    }
    if (boundLiberoIds.has(runtime.role1PlayerId) || boundLiberoIds.has(runtime.role2PlayerId)) {
      return false
    }
    return !runtimeHasDuplicateCourtMembers(side)
  }

  function oppositeSlotIndex(slotIndex) {
    return SLOT_OPPOSITE_MAP[slotIndex] ?? -1
  }

  function rotateSlotIndex(slotIndex) {
    if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex >= 6) {
      return -1
    }
    const nextSlotIndexMap = {
      0: 1,
      1: 2,
      2: 5,
      3: 0,
      4: 3,
      5: 4,
    }
    return nextSlotIndexMap[slotIndex] ?? -1
  }

  function isFrontSlot(slotIndex) {
    return slotIndex >= 0 && slotIndex <= 2
  }

  function detectRoleSlotIndex(currentCourt, playerId, liberoId, fallbackSlotIndex) {
    const playerIndex = playerId ? currentCourt.indexOf(playerId) : -1
    if (playerIndex >= 0) {
      return playerIndex
    }
    const liberoIndex = liberoId ? currentCourt.indexOf(liberoId) : -1
    if (liberoIndex >= 0) {
      return liberoIndex
    }
    return fallbackSlotIndex
  }

  function buildInitialLiberoRuntime(side) {
    const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
    if (setup.pairIndexes.length !== 2) {
      return createEmptyLiberoRuntime()
    }

    const currentCourt = cloneCourt(getCourtBySide(side))
    const seeds = buildRoleSeeds(side, setup)

    if (seeds.length !== 2) {
      return createEmptyLiberoRuntime()
    }

    const runtime = createEmptyLiberoRuntime()
    runtime.role1SlotIndex = detectRoleSlotIndex(currentCourt, seeds[0].memberId, setup.libero1Id, seeds[0].slotIndex)
    runtime.role2SlotIndex = detectRoleSlotIndex(currentCourt, seeds[1].memberId, setup.libero2Id, seeds[1].slotIndex)

    if (runtime.role1SlotIndex === runtime.role2SlotIndex) {
      runtime.role2SlotIndex = oppositeSlotIndex(runtime.role1SlotIndex)
    }
    if (runtime.role1SlotIndex < 0 && runtime.role2SlotIndex >= 0) {
      runtime.role1SlotIndex = oppositeSlotIndex(runtime.role2SlotIndex)
    }
    if (runtime.role2SlotIndex < 0 && runtime.role1SlotIndex >= 0) {
      runtime.role2SlotIndex = oppositeSlotIndex(runtime.role1SlotIndex)
    }
    if (runtime.role1SlotIndex < 0) {
      runtime.role1SlotIndex = seeds[0].slotIndex
    }
    if (runtime.role2SlotIndex < 0) {
      runtime.role2SlotIndex = seeds[1].slotIndex
    }

    runtime.role1PlayerId = seeds[0].memberId
    runtime.role2PlayerId = seeds[1].memberId

    const role1CurrentMember = currentCourt[runtime.role1SlotIndex] || ''
    if (role1CurrentMember && role1CurrentMember !== setup.libero1Id) {
      runtime.role1PlayerId = role1CurrentMember
    }
    const role2CurrentMember = currentCourt[runtime.role2SlotIndex] || ''
    if (role2CurrentMember && role2CurrentMember !== setup.libero2Id) {
      runtime.role2PlayerId = role2CurrentMember
    }

    return cloneLiberoRuntime(runtime)
  }

  function ensureTeamLiberoRuntime(side) {
    const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
    const currentRuntime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
    if (setup.pairIndexes.length !== 2) {
      const hasRuntime =
        currentRuntime.role1SlotIndex >= 0 ||
        currentRuntime.role2SlotIndex >= 0 ||
        currentRuntime.role1PlayerId ||
        currentRuntime.role2PlayerId
      if (hasRuntime) {
        setLiberoRuntimeBySide(side, createEmptyLiberoRuntime())
        return true
      }
      return false
    }

    if (isTeamLiberoRuntimeValid(side, currentRuntime, setup)) {
      return false
    }

    setLiberoRuntimeBySide(side, buildInitialLiberoRuntime(side))
    return true
  }

  function ensureAllLiberoRuntimeReady() {
    const leftChanged = ensureTeamLiberoRuntime('left')
    const rightChanged = ensureTeamLiberoRuntime('right')
    return leftChanged || rightChanged
  }

  function rotateTeamLiberoRuntime(side) {
    const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
    runtime.role1SlotIndex = rotateSlotIndex(runtime.role1SlotIndex)
    runtime.role2SlotIndex = rotateSlotIndex(runtime.role2SlotIndex)
    setLiberoRuntimeBySide(side, runtime)
  }

  function shouldRoleUseLibero(side, slotIndex, liberoId) {
    const actualSide = toActualSide(side)
    if (!liberoId) {
      return false
    }
    if (isFrontSlot(slotIndex)) {
      return false
    }
    if (slotIndex === 5) {
      return serveSide.value !== actualSide
    }
    return true
  }

  function compareLiberoAssignmentPriority(left, right) {
    if (left.shouldUseLibero !== right.shouldUseLibero) {
      return left.shouldUseLibero ? 1 : -1
    }
    if (left.slotIndex === 5 && right.slotIndex !== 5) {
      return 1
    }
    if (right.slotIndex === 5 && left.slotIndex !== 5) {
      return -1
    }
    return right.slotIndex - left.slotIndex
  }

  function buildLiberoAssignments(side) {
    const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
    if (setup.pairIndexes.length !== 2) {
      return []
    }

    const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
    return getRuntimeRoleEntries(runtime, setup)
      .filter((role) => role.slotIndex >= 0 && role.slotIndex < 6)
      .map((role) => ({
        ...role,
        shouldUseLibero: shouldRoleUseLibero(side, role.slotIndex, role.liberoId),
      }))
  }

  function isActiveLiberoOnSlot(side, slotIndex, memberId) {
    if (!memberId) {
      return false
    }

    const assignments = buildLiberoAssignments(side)
    const liberoAssignmentMap = new Map()
    for (const assignment of assignments) {
      if (!assignment.shouldUseLibero || !assignment.liberoId) {
        continue
      }
      const current = liberoAssignmentMap.get(assignment.liberoId)
      if (!current || compareLiberoAssignmentPriority(assignment, current) > 0) {
        liberoAssignmentMap.set(assignment.liberoId, assignment)
      }
    }

    return assignments.some((assignment) => {
      return (
        assignment.slotIndex === slotIndex &&
        assignment.liberoId === memberId &&
        assignment.shouldUseLibero &&
        liberoAssignmentMap.get(assignment.liberoId) === assignment
      )
    })
  }

  function settleTeamLibero(side) {
    ensureTeamLiberoRuntime(side)

    const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
    if (setup.pairIndexes.length !== 2) {
      return false
    }

    const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
    const court = cloneCourt(getCourtBySide(side))
    const boundLiberoIds = new Set(getBoundLiberoIds(setup))
    let changed = false
    const assignments = []

    for (const role of getRuntimeRoleEntries(runtime, setup)) {
      if (role.slotIndex < 0 || role.slotIndex >= 6) {
        continue
      }

      const currentMemberId = court[role.slotIndex] || ''
      if (
        currentMemberId &&
        currentMemberId !== role.liberoId &&
        !boundLiberoIds.has(currentMemberId) &&
        currentMemberId !== runtime[role.playerField]
      ) {
        runtime[role.playerField] = currentMemberId
        changed = true
      }

      const currentPlayerId = runtime[role.playerField] || ''
      if (!currentPlayerId) {
        continue
      }

      const shouldUseLibero = shouldRoleUseLibero(side, role.slotIndex, role.liberoId)
      assignments.push({
        ...role,
        currentPlayerId,
        shouldUseLibero,
        targetMemberId: shouldUseLibero ? role.liberoId || currentPlayerId : currentPlayerId,
      })
    }

    const liberoAssignmentMap = new Map()
    for (const assignment of assignments) {
      if (!assignment.shouldUseLibero || !assignment.liberoId) {
        continue
      }
      const current = liberoAssignmentMap.get(assignment.liberoId)
      if (!current || compareLiberoAssignmentPriority(assignment, current) > 0) {
        liberoAssignmentMap.set(assignment.liberoId, assignment)
      }
    }

    for (const assignment of assignments) {
      const preferredAssignment = assignment.liberoId ? liberoAssignmentMap.get(assignment.liberoId) : null
      const targetMemberId =
        preferredAssignment === assignment
          ? assignment.targetMemberId
          : assignment.currentPlayerId
      if (targetMemberId && court[assignment.slotIndex] !== targetMemberId) {
        court[assignment.slotIndex] = targetMemberId
        changed = true
      }
    }

    if (changed) {
      setLiberoRuntimeBySide(side, runtime)
      setCourtBySide(side, court)
    }
    return changed
  }

  function settleAllLiberoStates() {
    const leftChanged = settleTeamLibero('left')
    const rightChanged = settleTeamLibero('right')
    return leftChanged || rightChanged
  }

  function buildSnapshot() {
    return normalizeMatchState({
      displaySideSwapped: displaySideSwapped.value,
      screenLeftParticipantSide: screenLeftParticipantSide.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      leftGameWins: leftGameWins.value,
      rightGameWins: rightGameWins.value,
      currentGameNo: currentGameNo.value,
      gameScores: gameScores.value.map((item) => ({ ...item })),
      serveSide: serveSide.value,
      currentGameStartServeSide: currentGameStartServeSide.value,
      leftTimeouts: leftTimeouts.value,
      rightTimeouts: rightTimeouts.value,
      leftCourt: leftCourt.value,
      rightCourt: rightCourt.value,
      baseLeftCourt: baseLeftCourt.value,
      baseRightCourt: baseRightCourt.value,
      leftLiberoSetup: leftLiberoSetup.value,
      rightLiberoSetup: rightLiberoSetup.value,
      leftLiberoRuntime: leftLiberoRuntime.value,
      rightLiberoRuntime: rightLiberoRuntime.value,
      leftCaptainMemberId: leftCaptainMemberId.value,
      rightCaptainMemberId: rightCaptainMemberId.value,
      matchEvents: matchEvents.value,
      nextEventSeq: nextEventSeq.value,
      lastSyncedEventSeq: lastSyncedEventSeq.value,
      draftLeftCourt: baseLeftCourt.value,
      draftRightCourt: baseRightCourt.value,
      draftServeSide: serveSide.value,
      lineupReady: lineupReady.value,
      finalGameSideSwitchPending: finalGameSideSwitchPending.value,
      finalGameSideSwitchHandled: finalGameSideSwitchHandled.value,
      retiredSide: retiredSide.value,
      matchEnded: matchEnded.value,
      winnerName: winnerName.value,
      historyStack: historyStack.value,
    })
  }

  function buildHistorySnapshot() {
    return buildHistoryEntry({
      displaySideSwapped: displaySideSwapped.value,
      screenLeftParticipantSide: screenLeftParticipantSide.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      leftGameWins: leftGameWins.value,
      rightGameWins: rightGameWins.value,
      currentGameNo: currentGameNo.value,
      gameScores: gameScores.value.map((item) => ({ ...item })),
      serveSide: serveSide.value,
      currentGameStartServeSide: currentGameStartServeSide.value,
      leftTimeouts: leftTimeouts.value,
      rightTimeouts: rightTimeouts.value,
      leftCourt: leftCourt.value,
      rightCourt: rightCourt.value,
      baseLeftCourt: baseLeftCourt.value,
      baseRightCourt: baseRightCourt.value,
      leftLiberoSetup: leftLiberoSetup.value,
      rightLiberoSetup: rightLiberoSetup.value,
      leftLiberoRuntime: leftLiberoRuntime.value,
      rightLiberoRuntime: rightLiberoRuntime.value,
      leftCaptainMemberId: leftCaptainMemberId.value,
      rightCaptainMemberId: rightCaptainMemberId.value,
      matchEvents: matchEvents.value,
      nextEventSeq: nextEventSeq.value,
      lastSyncedEventSeq: lastSyncedEventSeq.value,
      draftLeftCourt: baseLeftCourt.value,
      draftRightCourt: baseRightCourt.value,
      draftServeSide: serveSide.value,
      lineupReady: lineupReady.value,
      finalGameSideSwitchPending: finalGameSideSwitchPending.value,
      finalGameSideSwitchHandled: finalGameSideSwitchHandled.value,
      retiredSide: retiredSide.value,
      matchEnded: matchEnded.value,
      winnerName: winnerName.value,
    })
  }

  function applyState(state) {
    const normalized = normalizeMatchState(state)
    displaySideSwapped.value = normalized.displaySideSwapped
    screenLeftParticipantSide.value = normalizeParticipantSide(normalized.screenLeftParticipantSide)
    leftScore.value = normalized.leftScore
    rightScore.value = normalized.rightScore
    leftGameWins.value = normalized.leftGameWins
    rightGameWins.value = normalized.rightGameWins
    currentGameNo.value = normalized.currentGameNo
    gameScores.value = normalized.gameScores
    serveSide.value = normalized.serveSide
    currentGameStartServeSide.value = normalized.currentGameStartServeSide
    leftTimeouts.value = normalized.leftTimeouts
    rightTimeouts.value = normalized.rightTimeouts
    leftCourt.value = cloneCourt(normalized.leftCourt)
    rightCourt.value = cloneCourt(normalized.rightCourt)
    baseLeftCourt.value = cloneCourt(normalized.baseLeftCourt)
    baseRightCourt.value = cloneCourt(normalized.baseRightCourt)
    leftLiberoSetup.value = cloneLiberoSetup(normalized.leftLiberoSetup)
    rightLiberoSetup.value = cloneLiberoSetup(normalized.rightLiberoSetup)
    leftLiberoRuntime.value = cloneLiberoRuntime(normalized.leftLiberoRuntime)
    rightLiberoRuntime.value = cloneLiberoRuntime(normalized.rightLiberoRuntime)
    leftCaptainMemberId.value = normalized.leftCaptainMemberId || ''
    rightCaptainMemberId.value = normalized.rightCaptainMemberId || ''
    matchEvents.value = Array.isArray(normalized.matchEvents) ? normalized.matchEvents.map((item) => ({ ...item, payload: clonePayload(item.payload) })) : []
    nextEventSeq.value = Number(normalized.nextEventSeq || 1)
    lastSyncedEventSeq.value = Number(normalized.lastSyncedEventSeq || 0)
    lineupReady.value = normalized.lineupReady
    finalGameSideSwitchPending.value = !!normalized.finalGameSideSwitchPending
    finalGameSideSwitchHandled.value = !!normalized.finalGameSideSwitchHandled
    retiredSide.value = normalized.retiredSide
    matchEnded.value = normalized.matchEnded
    winnerName.value = normalized.winnerName
    historyStack.value = normalized.historyStack
  }

  function persistState() {
    historyStack.value = historyStack.value.slice(-MAX_HISTORY_ENTRIES)
    saveMatchState(matchId.value, buildSnapshot())
  }

  function memberMap(side) {
    const actualSide = toActualSide(side)
    const team = actualSide === 'right' ? rightTeam.value : leftTeam.value
    const map = new Map()
    for (const member of team.members || []) {
      map.set(member.id, member)
    }
    return map
  }

  function memberById(side, memberId) {
    if (!memberId) return null
    return memberMap(side).get(memberId) || null
  }

  function jerseyText(side, memberId) {
    const member = memberById(side, memberId)
    return member ? String(member.jerseyNumber) : '--'
  }

  function isOnCourt(side, memberId) {
    const court = getCourtBySide(side)
    return court.includes(memberId)
  }

  function buildRosterSnapshotPayload() {
    return {
      leftMembers: ((normalizeParticipantSide(screenLeftParticipantSide.value) === 'left' ? leftTeam.value : rightTeam.value).members || []).map((member) => ({
        id: member.id,
        name: member.name,
        jerseyNumber: member.jerseyNumber,
        captain: !!member.captain,
        libero: !!member.libero,
      })),
      rightMembers: ((normalizeParticipantSide(screenLeftParticipantSide.value) === 'left' ? rightTeam.value : leftTeam.value).members || []).map((member) => ({
        id: member.id,
        name: member.name,
        jerseyNumber: member.jerseyNumber,
        captain: !!member.captain,
        libero: !!member.libero,
      })),
    }
  }

  function buildLineupSnapshotPayload() {
    const participantState = toParticipantStateSnapshot(buildSnapshot())
    return {
      left: {
        court: cloneCourt(participantState.leftCourt),
        middlePairIndexes: [...(participantState.leftLiberoSetup?.pairIndexes || [])],
        libero1Id: participantState.leftLiberoSetup?.libero1Id || '',
        libero2Id: participantState.leftLiberoSetup?.libero2Id || '',
      },
      right: {
        court: cloneCourt(participantState.rightCourt),
        middlePairIndexes: [...(participantState.rightLiberoSetup?.pairIndexes || [])],
        libero1Id: participantState.rightLiberoSetup?.libero1Id || '',
        libero2Id: participantState.rightLiberoSetup?.libero2Id || '',
      },
      serveSide: participantState.serveSide,
    }
  }

  function ensureBootstrapEvents() {
    let changed = false
    if (!matchEvents.value.some((item) => item.type === 'roster_snapshot')) {
      appendMatchEvent('roster_snapshot', buildRosterSnapshotPayload(), { scheduleFlush: false })
      changed = true
    }
    if (!matchEvents.value.some((item) => item.type === 'lineup_snapshot' && item.gameNo === currentGameNo.value)) {
      appendMatchEvent('lineup_snapshot', buildLineupSnapshotPayload(), { scheduleFlush: false })
      changed = true
    }
    return changed
  }

  function syncCaptainState(options = {}) {
    const recordAutoEvent = !!options.recordAutoEvent
    let changed = false
    captainPromptQueue.value = []
    for (const side of ['left', 'right']) {
      const originalCaptainId = originalCaptainMemberId(side)
      const currentCaptainId = getCaptainBySide(side)
      const originalCaptainOnCourt = originalCaptainId && isOnCourt(side, originalCaptainId)
      if (originalCaptainOnCourt) {
        if (currentCaptainId !== originalCaptainId) {
          setCaptainBySide(side, originalCaptainId)
          changed = true
          if (recordAutoEvent) {
            appendMatchEvent('captain_change', {
              side: getParticipantSideByScreenSide(side),
              captainMemberId: originalCaptainId,
              originalCaptainMemberId: originalCaptainId,
              source: 'auto',
            })
          }
        }
        continue
      }

      if (currentCaptainId && isOnCourt(side, currentCaptainId)) {
        continue
      }

      if (currentCaptainId) {
        setCaptainBySide(side, '')
        changed = true
      }
      if (lineupReady.value && buildOnCourtMembers(side).length === 6) {
        captainPromptQueue.value.push(side)
      }
    }
    captainCandidateMemberId.value = captainPromptCandidates.value[0]?.id || ''
    return changed
  }

  function confirmCaptainSelection() {
    const side = captainPromptSide.value
    if (!side) return
    const member = captainPromptCandidates.value.find((item) => item.id === captainCandidateMemberId.value)
    if (!member) {
      uni.showToast({ title: '请先选择场上队长', icon: 'none' })
      return
    }
    setCaptainBySide(side, member.id)
    appendMatchEvent('captain_change', {
      side: getParticipantSideByScreenSide(side),
      captainMemberId: member.id,
      originalCaptainMemberId: originalCaptainMemberId(side),
      source: 'manual',
    })
    removeCaptainPrompt(side)
    persistState()
    scheduleEventFlush(200)
  }

  function pushHistory() {
    historyStack.value.push(buildHistorySnapshot())
    historyStack.value = historyStack.value.slice(-MAX_HISTORY_ENTRIES)
  }

  function syncRuntimeSideCollections() {
    captainPromptQueue.value = captainPromptQueue.value.map((side) => toggleSide(side))
    if (selectedBench.value.side) {
      selectedBench.value = {
        ...selectedBench.value,
        side: toggleSide(selectedBench.value.side),
      }
    }
  }

  function swapSides(reason) {
    const previousLeftTeam = leftTeam.value
    const previousRightTeam = rightTeam.value
    const swapped = swapMatchStateSides(buildSnapshot())
    applyState(swapped)
    leftTeam.value = previousRightTeam
    rightTeam.value = previousLeftTeam
    syncRuntimeSideCollections()
    appendMatchEvent('side_switch', {
      reason: reason || 'manual',
      screenLeftParticipantSide: screenLeftParticipantSide.value,
    })
  }

  function resetFinalGameSideSwitchState() {
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = false
  }

  function clearKeepCurrentDisplaySideCountdown() {
    if (keepCurrentDisplaySideTimer) {
      clearInterval(keepCurrentDisplaySideTimer)
      keepCurrentDisplaySideTimer = null
    }
    keepCurrentDisplaySideCountdown.value = 0
  }

  function startKeepCurrentDisplaySideCountdown() {
    clearKeepCurrentDisplaySideCountdown()
    keepCurrentDisplaySideCountdown.value = 5
    keepCurrentDisplaySideTimer = setInterval(() => {
      if (keepCurrentDisplaySideCountdown.value <= 1) {
        clearKeepCurrentDisplaySideCountdown()
        return
      }
      keepCurrentDisplaySideCountdown.value -= 1
    }, 1000)
  }

  function clearResetMatchCountdown() {
    if (resetMatchCountdownTimer) {
      clearInterval(resetMatchCountdownTimer)
      resetMatchCountdownTimer = null
    }
    resetMatchCountdown.value = 0
  }

  function startResetMatchCountdown() {
    clearResetMatchCountdown()
    resetMatchCountdown.value = 10
    resetMatchCountdownTimer = setInterval(() => {
      if (resetMatchCountdown.value <= 1) {
        clearResetMatchCountdown()
        return
      }
      resetMatchCountdown.value -= 1
    }, 1000)
  }

  function shouldTriggerFinalGameSideSwitchPrompt(score) {
    return isDecidingGame.value && !finalGameSideSwitchPending.value && !finalGameSideSwitchHandled.value && Number(score) === 8
  }

  function openFinalGameSideSwitchPrompt() {
    finalGameSideSwitchPending.value = true
  }

  function keepCurrentDisplaySide() {
    if (!canKeepCurrentDisplaySide.value) return
    clearKeepCurrentDisplaySideCountdown()
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = true
    persistState()
  }

  function confirmDisplaySideSwitch() {
    if (!finalGameSideSwitchPending.value) return
    pushHistory()
    swapSides('deciding_game_mid_switch')
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = true
    persistState()
  }

  function selectBench(side, memberId) {
    if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    if (isOnCourt(side, memberId)) return
    const same = selectedBench.value.side === side && selectedBench.value.memberId === memberId
    selectedBench.value = same ? { side: '', memberId: '' } : { side, memberId }
  }

  function handleCourtSlot(side, index) {
    if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    if (selectedBench.value.side !== side || !selectedBench.value.memberId) return
    const actualSide = toActualSide(side)
    const previousCourt = actualSide === 'left' ? leftCourt.value : rightCourt.value
    const outMemberId = previousCourt[index] || ''
    const inMemberId = selectedBench.value.memberId
    pushHistory()
    if (actualSide === 'left') {
      leftCourt.value.splice(index, 1, inMemberId)
    } else {
      rightCourt.value.splice(index, 1, inMemberId)
    }
    settleTeamLibero(side)
    selectedBench.value = { side: '', memberId: '' }
    appendMatchEvent('substitution', {
      side: getParticipantSideByScreenSide(actualSide),
      outMemberId,
      inMemberId,
    })
    syncCaptainState({ recordAutoEvent: true })
    persistState()
  }

  function rotateCourt(side) {
    const source = side === 'right' ? rightCourt.value.slice() : leftCourt.value.slice()
    const rotated = [source[3], source[0], source[1], source[4], source[5], source[2]]
    if (side === 'right') {
      rightCourt.value = rotated
    } else {
      leftCourt.value = rotated
    }
  }

  function checkWinCondition(myScore, opponentScore) {
    return myScore >= currentTargetPoints.value && myScore - opponentScore >= 2
  }

  function goToNextLineup() {
    const nextServeParticipantSide = toggleSide(getParticipantSideByScreenSide(currentGameStartServeSide.value))
    const state = swapMatchStateSides(buildSnapshot())
    const nextServeSide = state.screenLeftParticipantSide === 'right'
      ? toggleSide(nextServeParticipantSide)
      : nextServeParticipantSide
    state.lineupReady = false
    state.finalGameSideSwitchPending = false
    state.finalGameSideSwitchHandled = false
    state.draftLeftCourt = cloneCourt(state.baseLeftCourt)
    state.draftRightCourt = cloneCourt(state.baseRightCourt)
    state.draftServeSide = nextServeSide
    state.matchEvents = [
      ...(Array.isArray(state.matchEvents) ? state.matchEvents : []),
      {
        seq: Number(state.nextEventSeq || 1),
        type: 'side_switch',
        gameNo: Number(state.currentGameNo || 1),
        leftScore: 0,
        rightScore: 0,
        serveSide: nextServeParticipantSide,
        payload: {
          reason: 'between_games',
          screenLeftParticipantSide: state.screenLeftParticipantSide,
        },
        syncStatus: 'pending',
      },
    ]
    state.nextEventSeq = Number(state.nextEventSeq || 1) + 1
    saveMatchState(matchId.value, state)
    uni.redirectTo({
      url: buildLineupUrl({
        ...pageQuery.value,
        serveSide: nextServeSide,
      }),
    })
  }

  function finishGame(winnerSide) {
    gameScores.value.push({
      gameNo: currentGameNo.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      winnerSide,
    })

    if (winnerSide === 'left') {
      leftGameWins.value += 1
    } else {
      rightGameWins.value += 1
    }

    if (leftGameWins.value >= Number(info.value.gamesToWin || 2) || rightGameWins.value >= Number(info.value.gamesToWin || 2)) {
      winnerName.value = leftGameWins.value > rightGameWins.value ? leftTeam.value.name : rightTeam.value.name
      matchEnded.value = true
      persistState()
      return
    }

    currentGameNo.value += 1
    leftScore.value = 0
    rightScore.value = 0
    leftTimeouts.value = 2
    rightTimeouts.value = 2
    resetFinalGameSideSwitchState()
    selectedBench.value = { side: '', memberId: '' }
    persistState()
    goToNextLineup()
  }

  function addScore(side) {
    if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    pushHistory()
    const actualSide = toActualSide(side)

    if (actualSide === 'left') {
      leftScore.value += 1
    } else {
      rightScore.value += 1
    }

    if (serveSide.value !== actualSide) {
      rotateCourt(actualSide)
      rotateTeamLiberoRuntime(side)
      serveSide.value = actualSide
    }

    settleAllLiberoStates()

    const myScore = actualSide === 'left' ? leftScore.value : rightScore.value
    const opponentScore = actualSide === 'left' ? rightScore.value : leftScore.value
    if (shouldTriggerFinalGameSideSwitchPrompt(myScore)) {
      openFinalGameSideSwitchPrompt()
      persistState()
      return
    }
    if (checkWinCondition(myScore, opponentScore)) {
      finishGame(actualSide)
      return
    }
    persistState()
  }

  function undo() {
    if (!historyStack.value.length || isLocked.value || isFinalGameSideSwitchPromptActive.value) return
    const snapshot = historyStack.value.pop()
    applyState(snapshot)
    syncCaptainState({ recordAutoEvent: false })
    persistState()
    scheduleEventFlush(200)
  }

  function useTimeout(side) {
    if (isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    const actualSide = toActualSide(side)
    if (actualSide === 'left') {
      if (leftTimeouts.value <= 0) return
      pushHistory()
      leftTimeouts.value -= 1
    } else {
      if (rightTimeouts.value <= 0) return
      pushHistory()
      rightTimeouts.value -= 1
    }
    appendMatchEvent('timeout', { side: getParticipantSideByScreenSide(actualSide) })
    persistState()
  }

  function openTimeoutSheet() {
    if (isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    const options = []
    const sides = []
    if ((toActualSide('left') === 'left' ? leftTimeouts.value : rightTimeouts.value) > 0) {
      options.push(`${leftDisplayTeamName.value}暂停`)
      sides.push('left')
    }
    if ((toActualSide('right') === 'left' ? leftTimeouts.value : rightTimeouts.value) > 0) {
      options.push(`${rightDisplayTeamName.value}暂停`)
      sides.push('right')
    }
    if (!options.length) return
    uni.showActionSheet({
      itemList: options,
      success: (res) => {
        const side = sides[res.tapIndex]
        if (side) useTimeout(side)
      },
    })
  }

  function retire(side) {
    if (isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    const actualSide = toActualSide(side)
    uni.showModal({
      title: '确认退赛',
      content: `确认 ${side === 'left' ? leftDisplayTeamName.value : rightDisplayTeamName.value} 退赛？`,
      success: (res) => {
        if (!res.confirm) return
        pushHistory()
        retiredSide.value = actualSide
        if (actualSide === 'left') {
          rightGameWins.value = Number(info.value.gamesToWin || 2)
          winnerName.value = rightTeam.value.name
        } else {
          leftGameWins.value = Number(info.value.gamesToWin || 2)
          winnerName.value = leftTeam.value.name
        }
        matchEnded.value = true
        persistState()
      },
    })
  }

  function openRetireSheet() {
    if (isLocked.value || isCaptainPromptActive.value || isFinalGameSideSwitchPromptActive.value) return
    uni.showActionSheet({
      itemList: [`${leftDisplayTeamName.value}退赛`, `${rightDisplayTeamName.value}退赛`],
      success: (res) => {
        if (res.tapIndex === 0) retire('left')
        if (res.tapIndex === 1) retire('right')
      },
    })
  }

  async function resetMatch() {
    if (!canResetMatch.value) return
    await runResetMatch(async () => {
      clearResetMatchCountdown()
      if (!matchId.value) {
        clearMatchState(matchId.value)
        uni.redirectTo({
          url: buildLineupUrl(pageQuery.value),
        })
        return
      }

      uni.showLoading({ title: '重新开始中...', mask: true })
      try {
        await request('/api/v1/matches/' + matchId.value + '/restart', {
          method: 'PUT',
        })
        clearMatchState(matchId.value)
        uni.redirectTo({
          url: buildLineupUrl(pageQuery.value),
        })
      } catch (_) {
        // request helper already handles toast
      } finally {
        uni.hideLoading()
      }
    })
  }

  async function syncAndBack() {
    if (!matchId.value) {
      uni.showToast({ title: '缺少比赛 ID', icon: 'none' })
      return
    }

    let winnerSide = ''
    if (retiredSide.value) {
      winnerSide = retiredSide.value === 'left' ? 'right' : 'left'
    } else if (leftGameWins.value > rightGameWins.value) {
      winnerSide = 'left'
    } else if (rightGameWins.value > leftGameWins.value) {
      winnerSide = 'right'
    }

    if (!winnerSide) {
      uni.showToast({ title: '未分出胜负，无法同步', icon: 'none' })
      return
    }

    const eventSynced = await flushPendingEvents()
    if (!eventSynced) {
      uni.showToast({ title: '比赛记录未完整同步，请稍后重试', icon: 'none' })
      return
    }

    const participantWinnerSide = toParticipantWinnerSide(winnerSide)
    const participantRetiredSide = retiredSide.value ? getParticipantSideByScreenSide(retiredSide.value) : null
    const participantGameScores = gameScores.value.map((item) => toParticipantGameScore(item))
    const participantTotals = toParticipantGameScore({
      leftScore: leftScore.value,
      rightScore: rightScore.value,
    })
    const participantGameWins = screenLeftParticipantSide.value === 'right'
      ? { left: rightGameWins.value, right: leftGameWins.value }
      : { left: leftGameWins.value, right: rightGameWins.value }

    try {
      await request('/api/v1/matches/' + matchId.value + '/finish', {
        method: 'PUT',
        data: {
          winnerSide: participantWinnerSide,
          leftScore: participantTotals.leftScore,
          rightScore: participantTotals.rightScore,
          leftGameWins: participantGameWins.left,
          rightGameWins: participantGameWins.right,
          gameScores: participantGameScores,
          retiredSide: participantRetiredSide,
        },
      })
      uni.showToast({ title: '结算成功', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 1000)
    } catch (_) {
      // request handles toast
    }
  }

  async function loadMatch() {
    if (!tournamentId.value || !matchId.value) {
      isError.value = true
      errorText.value = '缺少比赛参数'
      loading.value = false
      return
    }

    loading.value = true
    isError.value = false

    try {
      const data = await request('/api/v1/tournaments/' + tournamentId.value + '/bracket', { method: 'GET' })
      info.value = {
        id: data.id,
        bestOf: Number(data.bestOf || 3),
        gamesToWin: Number(data.gamesToWin || 2),
      }
      const match = (Array.isArray(data.matches) ? data.matches : []).find((item) => item.id === matchId.value)
      if (!match) {
        throw new Error('未找到比赛记录')
      }

      const participantMap = new Map()
      for (const participant of Array.isArray(data.players) ? data.players : []) {
        participantMap.set(participant.id, normalizeTeam(participant))
      }

      leftTeam.value = participantMap.get(match.leftPlayerId) || { name: '主队', members: [] }
      rightTeam.value = participantMap.get(match.rightPlayerId) || { name: '客队', members: [] }

      if (leftTeam.value.members.length < 6 || rightTeam.value.members.length < 6) {
        throw new Error('双方队伍都至少需要 6 名队员')
      }

      const cached = loadMatchState(matchId.value)
      if (!cached || !cached.lineupReady) {
        uni.redirectTo({
          url: buildLineupUrl(pageQuery.value),
        })
        return
      }
      applyState(cached)
      if (screenLeftParticipantSide.value === 'right') {
        const currentLeftTeam = leftTeam.value
        leftTeam.value = rightTeam.value
        rightTeam.value = currentLeftTeam
      }
      await loadThemeDraftFromServer()
      const isNewGameEntry = !matchEvents.value.some((item) => item.type === 'lineup_snapshot' && item.gameNo === currentGameNo.value)
      if (isNewGameEntry) {
        leftCaptainMemberId.value = ''
        rightCaptainMemberId.value = ''
      }
      const initialized = ensureAllLiberoRuntimeReady()
      const settled = settleAllLiberoStates()
      const bootstrapped = ensureBootstrapEvents()
      const captainChanged = syncCaptainState({ recordAutoEvent: true })
      if (initialized || settled || bootstrapped || captainChanged) {
        persistState()
      }
      scheduleEventFlush(200)
    } catch (error) {
      isError.value = true
      errorText.value = error?.message || '加载排球记分牌失败'
    } finally {
      loading.value = false
    }
  }

  restoreThemeDraft()

  watch(themeDevice, (nextDevice, prevDevice) => {
    if (!prevDevice || nextDevice === prevDevice) return
    restoreThemeDraft(nextDevice, themeMode.value)
  })

  watch(
    () => finalGameSideSwitchPending.value,
    (visible) => {
      if (visible) {
        startKeepCurrentDisplaySideCountdown()
      } else {
        clearKeepCurrentDisplaySideCountdown()
      }
    },
    { immediate: true }
  )

  watch(
    () => isLocked.value,
    (locked) => {
      if (locked) {
        startResetMatchCountdown()
      } else {
        clearResetMatchCountdown()
      }
    },
    { immediate: true }
  )

  onLoad((options) => {
    tournamentId.value = options?.tournamentId || ''
    matchId.value = options?.matchId || ''
    themeMode.value = readThemeModeFromStorage()
    restoreThemeDraft(themeDevice.value, themeMode.value)
    pageQuery.value = {
      tournamentId: options?.tournamentId || '',
      matchId: options?.matchId || '',
      leftName: options?.leftName || '',
      rightName: options?.rightName || '',
      bestOf: options?.bestOf || '',
      gamesToWin: options?.gamesToWin || '',
      pointsToWin: options?.pointsToWin || '',
      enableDeuce: options?.enableDeuce || '',
      capPoint: options?.capPoint || '',
    }
    syncWindowMetrics()
    updateH5PortraitPreview()
    if (typeof uni.onWindowResize === 'function') {
      uni.onWindowResize(handleWindowResize)
    }
    // #ifdef H5
    window.addEventListener('resize', updateH5PortraitPreview)
    // #endif
    loadMatch()
  })

  onUnmounted(() => {
    if (typeof uni.offWindowResize === 'function') {
      uni.offWindowResize(handleWindowResize)
    }
    // #ifdef H5
    window.removeEventListener('resize', updateH5PortraitPreview)
    // #endif
    if (eventFlushTimer) {
      clearTimeout(eventFlushTimer)
      eventFlushTimer = null
    }
    clearKeepCurrentDisplaySideCountdown()
    clearResetMatchCountdown()
  })

  onBackPress(() => {
    if (isFinalGameSideSwitchPromptActive.value) {
      uni.showToast({
        title: '请先处理换边提示',
        icon: 'none',
        duration: 2000,
      })
      return true
    }
    if (isLocked.value) {
      return false
    }
    uni.showToast({
      title: '比赛进行中，请先完成结算',
      icon: 'none',
      duration: 2000,
    })
    return true
  })

  return {
    // core state
    loading,
    isError,
    errorText,
    tournamentId,
    matchId,
    info,
    leftTeam,
    rightTeam,
    pageQuery,
    displaySideSwapped,
    // score state
    leftScore,
    rightScore,
    leftGameWins,
    rightGameWins,
    currentGameNo,
    gameScores,
    serveSide,
    leftTimeouts,
    rightTimeouts,
    leftCourt,
    rightCourt,
    historyStack,
    retiredSide,
    matchEnded,
    winnerName,
    selectedBench,
    lineupReady,
    finalGameSideSwitchPending,
    // captain
    captainPromptQueue,
    captainCandidateMemberId,
    // display computeds
    leftDisplayTeam,
    rightDisplayTeam,
    leftDisplayTeamName,
    rightDisplayTeamName,
    leftDisplayScore,
    rightDisplayScore,
    leftDisplayGameWins,
    rightDisplayGameWins,
    displayServeSide,
    winnerDisplayName,
    leftCourtDisplaySlots,
    rightCourtDisplaySlots,
    captainPromptSide,
    isCaptainPromptActive,
    captainPromptCandidates,
    isFinalGameSideSwitchPromptActive,
    canKeepCurrentDisplaySide,
    keepCurrentDisplaySideLabel,
    resetMatchCountdown,
    canResetMatch,
    resetMatchLabel,
    resetMatchRunning,
    captainPromptTeamName,
    isLocked,
    isDecidingGame,
    currentTargetPoints,
    finishedGameScores,
    scoreSummary,
    finalGameSideSwitchScoreText,
    displayGameScores,
    // screen adaptation
    windowWidth,
    windowHeight,
    orientation,
    isTablet,
    sizeBand,
    pageClassNames,
    useLandscapePreview,
    rootPageStyle,
    // theme debugger
    isThemeDebuggerEnabled,
    themeDebuggerCollapsed,
    themeMode,
    themeModeLabel,
    themeModeOptions,
    isThemeModePickerOpen,
    activeThemeToken,
    activeThemeTokenMeta,
    activeThemeRgb,
    themeDraft,
    themeHexInputs,
    themeTokenOptions,
    themeStyleVars,
    rgbChannels,
    sliderTrackBackgroundColor,
    themeServerSaving,
    // actions
    addScore,
    undo,
    openTimeoutSheet,
    openRetireSheet,
    selectBench,
    handleCourtSlot,
    jerseyText,
    isOnCourt,
    isCurrentCaptain,
    confirmCaptainSelection,
    keepCurrentDisplaySide,
    confirmDisplaySideSwitch,
    resetMatch,
    syncAndBack,
    loadMatch,
    handleWindowResize,
    // theme debugger actions
    setActiveThemeToken,
    handleThemeHexInput,
    normalizeThemeHexInput,
    previewActiveThemeChannel,
    commitActiveThemeChannel,
    toggleThemeDebugger,
    openThemeModePicker,
    closeThemeModePicker,
    setThemeMode,
    resetThemeDraft,
    saveThemeDraftToServer,
    copyThemeVariables,
  }
}
