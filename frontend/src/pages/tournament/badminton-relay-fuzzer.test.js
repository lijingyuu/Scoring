import { describe, it, expect } from 'vitest'
import {
  computeCurrentSegmentIndex,
  computeTargetScore,
  computeSegmentTarget,
  isRelayMatchEnded,
  isSegmentTargetReached,
  shouldAdvanceSegment,
  toggleSidesSwapped,
  visualToLogicalSide,
  buildScoreState,
  parseScoreState,
  appendSegmentScore,
  buildRelayItemsFromOrders,
  validateRelayChain,
  buildRelayOrderFromItems,
  validateRelayLineup,
  buildFullSnapshot,
} from './relay-scoring'

// 简单的 Mulberry32 PRNG
function createPRNG(seed) {
  let s = (seed ^ 0xdeadbeef) >>> 0
  return {
    next() {
      s = (s + 0x6d2b79f5) | 0
      let t = Math.imul(s ^ (s >>> 15), 1 | s)
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    },
    randInt(min, max) {
      return Math.floor(this.next() * (max - min + 1)) + min
    },
    randBool(p = 0.5) {
      return this.next() < p
    },
    choice(arr) {
      return arr[this.randInt(0, arr.length - 1)]
    },
  }
}

describe('Badminton Relay Match State Machine Fuzzer', () => {
  it('fuzzes relay chain generation and cyclic invariant validation', () => {
    const prng = createPRNG(1001)

    for (let i = 0; i < 50; i++) {
      const memberCount = prng.randInt(3, 10)
      const leftMembers = Array.from({ length: memberCount }, (_, idx) => `L_${idx}`)
      const rightMembers = Array.from({ length: memberCount }, (_, idx) => `R_${idx}`)

      const items = buildRelayItemsFromOrders(leftMembers, rightMembers, memberCount)
      expect(items.length).toBe(memberCount)

      // 提取左队 pairs 并验证环状相邻连续性
      const leftPairs = items.map((it) => it.leftMemberIds)
      const validRes = validateRelayChain(leftPairs)
      expect(validRes.valid).toBe(true)

      // 猴子篡改探针：打乱某一段相邻接力队员，断言 chain 破损必定被捕获
      if (memberCount >= 4) {
        const tamperedPairs = leftPairs.map((p) => [...p])
        tamperedPairs[1][1] = 'BROKEN_MEMBER_ID'
        const brokenRes = validateRelayChain(tamperedPairs)
        expect(brokenRes.valid).toBe(false)
        expect(brokenRes.reason).toContain('chain broken')
      }
    }
  })

  it('fuzzes full relay match chase scoring and segment progression', () => {
    const prng = createPRNG(2002)

    for (let match = 1; match <= 20; match++) {
      const baseScore = prng.choice([10, 11, 15])
      const memberCount = prng.choice([3, 5, 6, 8])
      const targetScore = computeTargetScore(baseScore, memberCount, memberCount)

      let leftScore = 0
      let rightScore = 0
      const segmentScores = []
      let segmentSwitchPending = false
      let sidesSwapped = false
      let lastScoredSide = ''
      const history = []

      let steps = 0
      const maxSteps = 500

      while (!isRelayMatchEnded(0, targetScore, leftScore, rightScore) && steps < maxSteps) {
        steps++
        const currentSegIndex = computeCurrentSegmentIndex(segmentScores.length, memberCount)
        const currentSegNo = currentSegIndex + 1
        const segmentTarget = computeSegmentTarget(baseScore, currentSegNo, targetScore)

        // 优先处理换段
        if (segmentSwitchPending) {
          // 保存已完段的分数
          appendSegmentScore(segmentScores, currentSegNo, leftScore, rightScore)
          segmentSwitchPending = false
          continue
        }

        const roll = prng.next()

        // 1) 撤销 (10%)
        if (roll < 0.10 && history.length > 0) {
          const prevState = history.pop()
          const parsed = parseScoreState(prevState)
          leftScore = parsed.leftScore
          rightScore = parsed.rightScore
          segmentSwitchPending = parsed.segmentSwitchPending
          sidesSwapped = parsed.sidesSwapped
          lastScoredSide = parsed.lastScoredSide
          continue
        }

        // 2) 换边 (5%)
        if (roll >= 0.10 && roll < 0.15) {
          history.push(buildScoreState(leftScore, rightScore, segmentScores, segmentSwitchPending, sidesSwapped, lastScoredSide))
          sidesSwapped = toggleSidesSwapped(sidesSwapped)
          continue
        }

        // 3) 正常得分 (85%)
        history.push(buildScoreState(leftScore, rightScore, segmentScores, segmentSwitchPending, sidesSwapped, lastScoredSide))
        const scoreSide = prng.choice(['left', 'right'])
        if (scoreSide === 'left') {
          leftScore++
        } else {
          rightScore++
        }
        lastScoredSide = scoreSide

        // 判定分段目标是否达成
        if (isSegmentTargetReached(leftScore, rightScore, segmentTarget)) {
          if (isRelayMatchEnded(0, targetScore, leftScore, rightScore)) {
            // 全场比赛结束
            appendSegmentScore(segmentScores, currentSegNo, leftScore, rightScore)
            break
          } else {
            segmentSwitchPending = true
          }
        }
      }

      // 不变式断言
      expect(isRelayMatchEnded(0, targetScore, leftScore, rightScore)).toBe(true)
      const maxFinal = Math.max(leftScore, rightScore)
      expect(maxFinal).toBeGreaterThanOrEqual(targetScore)
      expect(segmentScores.length).toBeLessThanOrEqual(memberCount)
    }
  })
})
