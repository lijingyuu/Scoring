<template>
  <div ref="rootEl" class="theme-switcher">
    <button
      class="ghost-action small theme-toggle"
      type="button"
      :aria-expanded="open"
      aria-label="切换配色主题"
      @click="open = !open"
    >
      ◐ {{ currentTheme.label }}
    </button>
    <div v-if="open" class="theme-menu" role="listbox" aria-label="配色主题">
      <button
        v-for="theme in THEMES"
        :key="theme.id"
        type="button"
        class="theme-option"
        :class="{ active: theme.id === currentId }"
        role="option"
        :aria-selected="theme.id === currentId"
        @click="apply(theme.id)"
      >
        <span class="theme-swatches">
          <span class="theme-dot" :style="{ background: theme.swatchBg }"></span>
          <span class="theme-dot" :style="{ background: theme.swatchAccent }"></span>
        </span>
        <span>{{ theme.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { DEFAULT_THEME, THEMES, THEME_KEY, normalizeTheme } from '../themes'

const currentId = ref(normalizeTheme(localStorage.getItem(THEME_KEY)))
const open = ref(false)
const rootEl = ref(null)
const currentTheme = computed(() => THEMES.find((theme) => theme.id === currentId.value) || THEMES[0])

function apply(themeId) {
  currentId.value = themeId
  document.documentElement.dataset.theme = themeId
  localStorage.setItem(THEME_KEY, themeId)
  open.value = false
}

function onDocClick(event) {
  if (open.value && rootEl.value && !rootEl.value.contains(event.target)) {
    open.value = false
  }
}

onMounted(() => {
  document.documentElement.dataset.theme = currentId.value
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<style scoped>
.theme-switcher {
  position: relative;
}

.theme-toggle {
  white-space: nowrap;
}

.theme-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 70;
  display: grid;
  gap: 2px;
  min-width: 190px;
  max-height: min(430px, 62vh);
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(var(--bg-deep-rgb), 0.98);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  overflow-y: auto;
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 6px 10px;
  border: 0;
  border-radius: 6px;
  color: var(--text);
  font-size: 13px;
  text-align: left;
  background: transparent;
}

.theme-option:hover {
  background: rgba(var(--tint-rgb), 0.08);
}

.theme-option.active {
  color: var(--accent);
  font-weight: 700;
}

.theme-swatches {
  display: flex;
  flex: none;
  align-items: center;
}

.theme-dot {
  width: 12px;
  height: 12px;
  border: 1px solid var(--line);
  border-radius: 50%;
}

.theme-dot + .theme-dot {
  margin-left: -5px;
}
</style>
