// 主题清单：与 styles.css 的 html[data-theme='...'] 主题块一一对应。
// ThemeSwitcher（菜单）与 main.js（挂载前应用/失效回退）共用，新增主题两处同步。
export const THEMES = [
  { id: 'obsidian', label: '曜石 · 极简', swatchBg: '#0c100f', swatchAccent: '#2dd4bf' },
  { id: 'navy', label: '深海 · 蓝调', swatchBg: '#0b1220', swatchAccent: '#38bdf8' },
  { id: 'aurora', label: '极光', swatchBg: '#061a20', swatchAccent: '#34d399' },
  { id: 'cyberpunk', label: '赛博朋克', swatchBg: '#0d0221', swatchAccent: '#ff2a6d' },
  { id: 'catppuccin', label: '猫咖 · 柔和', swatchBg: '#1e1e2e', swatchAccent: '#cba6f7' },
  { id: 'nord', label: '北欧霜蓝', swatchBg: '#2e3440', swatchAccent: '#88c0d0' },
  { id: 'rose-pine', label: '玫瑰松', swatchBg: '#191724', swatchAccent: '#ebbcba' },
]

export const DEFAULT_THEME = 'obsidian'
export const THEME_KEY = 'scoring_admin_theme'

/** localStorage 里存了已删除主题时回退到默认 */
export function normalizeTheme(id) {
  return THEMES.some((theme) => theme.id === id) ? id : DEFAULT_THEME
}
