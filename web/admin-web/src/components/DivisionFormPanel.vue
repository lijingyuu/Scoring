<template>
  <div class="division-form-panel">
    <div class="division-card-head">
      <span class="division-index">组别 {{ index }}</span>
      <input v-model.trim="draft.name" class="division-name-input" maxlength="64" placeholder="例如 男单组" />
      <button v-if="canRemove" class="text-action danger" type="button" @click="emit('remove')">删除组别</button>
    </div>

    <div class="field-grid three division-format-grid">
      <label>
        <span>赛制</span>
        <select v-model.number="draft.tournamentType">
          <option :value="0">淘汰赛</option>
          <option :value="1">小组赛 + 淘汰赛</option>
          <option :value="2">循环赛</option>
        </select>
      </label>
      <label v-if="draft.tournamentType === 0">
        <span>淘汰轮数</span>
        <input v-model.number="draft.knockoutRounds" type="number" min="1" max="10" />
        <small class="muted">该轮数需要 {{ roundsHint }} 名选手</small>
      </label>
      <template v-if="draft.tournamentType === 1">
        <label>
          <span>淘汰名额</span>
          <select v-model.number="draft.knockoutSlots">
            <option :value="2">2</option>
            <option :value="4">4</option>
            <option :value="8">8</option>
            <option :value="16">16</option>
            <option :value="32">32</option>
          </select>
        </label>
        <label>
          <span>每组出线</span>
          <select v-model.number="draft.qualifiersPerGroup">
            <option :value="1">1</option>
            <option :value="2">2</option>
          </select>
        </label>
      </template>
      <label v-if="draft.tournamentType === 2">
        <span>轮次</span>
        <select v-model.number="draft.roundRobinRounds">
          <option :value="1">单循环</option>
          <option :value="2">双循环</option>
        </select>
      </label>
      <label v-if="draft.tournamentType !== 2" class="inline-toggle division-third-place">
        <input v-model="draft.thirdPlaceEnabled" type="checkbox" />
        <span>季军赛</span>
      </label>
    </div>

    <div class="field-grid four division-rule-grid">
      <label>
        <span>总局数</span>
        <select v-model.number="draft.rule.bestOf" @change="setBestOf(draft.rule, draft.rule.bestOf)">
          <option :value="1">一局</option>
          <option :value="3">三局两胜</option>
          <option :value="5">五局三胜</option>
        </select>
      </label>
      <label>
        <span>胜局</span>
        <input :value="Math.floor(Number(draft.rule.bestOf) / 2) + 1" type="number" readonly />
      </label>
      <label>
        <span>每局分</span>
        <input v-model.number="draft.rule.pointsToWin" type="number" min="1" />
      </label>
      <label>
        <span>追分</span>
        <select v-model="draft.rule.enableDeuce">
          <option :value="true">开启</option>
          <option :value="false">关闭</option>
        </select>
      </label>
      <label>
        <span>封顶</span>
        <input v-model.number="draft.rule.capPoint" type="number" min="1" @change="clampCapPoint" />
      </label>
    </div>

    <div v-if="draft.tournamentType === 1" class="division-knockout-rule">
      <p class="division-knockout-rule-title">淘汰赛规则</p>
      <div class="field-grid four division-rule-grid">
        <label>
          <span>总局数</span>
          <select v-model.number="draft.knockoutRule.bestOf">
            <option :value="1">一局</option>
            <option :value="3">三局两胜</option>
            <option :value="5">五局三胜</option>
          </select>
        </label>
        <label>
          <span>胜局</span>
          <input :value="Math.floor(Number(draft.knockoutRule.bestOf) / 2) + 1" type="number" readonly />
        </label>
        <label>
          <span>每局分</span>
          <input v-model.number="draft.knockoutRule.pointsToWin" type="number" min="1" />
        </label>
        <label>
          <span>追分</span>
          <select v-model="draft.knockoutRule.enableDeuce">
            <option :value="true">开启</option>
            <option :value="false">关闭</option>
          </select>
        </label>
        <label>
          <span>封顶</span>
          <input v-model.number="draft.knockoutRule.capPoint" type="number" min="1" />
        </label>
      </div>
    </div>

    <label class="division-players-label">
      <span>选手名单</span>
      <textarea
        v-model="draft.playersText"
        placeholder="每行一名选手，可在前面加种子序号，例如：1 张三"
      ></textarea>
      <small class="muted">已识别 {{ playerCount }} 名选手</small>
    </label>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  // 组别草稿对象（父级 divisionDrafts 的元素），组件内直接改其属性
  draft: { type: Object, required: true },
  // 展示用序号（1-based）
  index: { type: Number, default: 1 },
  canRemove: { type: Boolean, default: false },
})

const emit = defineEmits(['remove'])

const playerCount = computed(() => String(props.draft.playersText || '')
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter(Boolean)
  .length)

const roundsHint = computed(() => {
  const n = Number(props.draft.knockoutRounds)
  if (!Number.isInteger(n) || n < 1 || n > 10) return '-'
  const minExclusive = n === 1 ? 1 : 2 ** (n - 1)
  return `${minExclusive + 1}~${2 ** n}`
})

function setBestOf(rule, bestOf) {
  rule.bestOf = Number(bestOf)
  rule.gamesToWin = Math.floor(rule.bestOf / 2) + 1
}

function clampCapPoint() {
  const rule = props.draft.rule
  const pointsToWin = Number(rule.pointsToWin) || 1
  const cap = Number(rule.capPoint)
  if (!Number.isFinite(cap)) {
    rule.capPoint = Math.min(99, pointsToWin + 1)
    return
  }
  rule.capPoint = Math.max(pointsToWin + 1, Math.min(99, Math.round(cap)))
}

// 循环赛没有淘汰阶段，季军赛无意义，自动关闭（沿用原创建页 onDivisionTypeChange 行为）
watch(() => props.draft.tournamentType, (type) => {
  if (Number(type) === 2) props.draft.thirdPlaceEnabled = false
})
</script>

<style scoped>
.division-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.division-card-head .division-index {
  font-weight: 600;
  white-space: nowrap;
}
.division-card-head .division-name-input {
  flex: 1;
}
.division-rule-grid,
.division-players-label {
  margin-top: 2px;
}
.division-players-label {
  display: grid;
  gap: 6px;
}
.division-players-label textarea {
  min-height: 72px;
}
.division-third-place {
  align-self: end;
}
.division-knockout-rule {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(var(--slate-rgb), 0.12);
}
.division-knockout-rule-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(var(--slate-rgb), 0.72);
}
</style>
