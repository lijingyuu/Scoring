#!/usr/bin/env node
/**
 * 排球混沌测试夜间值守 runner（确定性，供定时任务每 30 分钟调用）。
 *
 * 职责：
 *   1. 检测是否有测试批次仍在运行（.current.json 记录 PID），在跑则只记录状态后退出；
 *   2. 评估上一轮已完成产物（manifest / summary / journal）并追加夜间评估记录；
 *   3. 按轮换队列启动下一层测试：
 *        scoreboard → 计分混沌百场批（后台分离进程，跨触发存活）
 *        backend    → 后端赛制引擎混沌（JUnit，同步，秒级）
 *        flow       → 跨局填写流混沌（vitest，同步，分钟级）
 *   4. 全部状态与产物集中 outputs/fuzz-volleyball/night/<日期>/。
 *
 * 用法：node scripts/run-night-chaos.mjs --mode auto|status
 */

import { spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const FRONTEND = path.join(REPO, 'frontend')
const OUT_DIR = path.join(REPO, 'outputs', 'fuzz-volleyball')
function localDate() {
  // 本地日期（避免 UTC 日期在 00:00~08:00 时段把 journal 落到前一日期目录）
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const NIGHT_DIR = path.join(OUT_DIR, 'night', localDate())
// state/current 与日期无关：跨午夜时段（23:30 启动的批次到 00:30 结束）必须
// 保持"在跑"记忆，且 evaluatedBatches 不能因换日而失忆导致重复评估/跳种子
const NIGHT_ROOT = path.join(OUT_DIR, 'night')
const STATE_FILE = path.join(NIGHT_ROOT, 'state.json')
const CURRENT_FILE = path.join(NIGHT_ROOT, '.current.json')
const EVAL_FILE = path.join(NIGHT_ROOT, 'evaluation.jsonl')

const LAYERS = ['scoreboard', 'backend', 'flow']
const SCOREBOARD_BASE_SEED_STEP = 10_000_000
const SCOREBOARD_BASE_SEED_FIRST = 70_000_000

function now() {
  return new Date().toISOString()
}

function ensureDirs() {
  fs.mkdirSync(NIGHT_DIR, { recursive: true })
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (_) {
    return fallback
  }
}

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, JSON.stringify(data, null, 2), 'utf8')
}

function pidAlive(pid) {
  if (!pid) return false
  try {
    process.kill(pid, 0)
    return true
  } catch (_) {
    return false
  }
}

function appendEvaluation(entry) {
  fs.mkdirSync(path.dirname(EVAL_FILE), { recursive: true })
  fs.appendFileSync(EVAL_FILE, JSON.stringify({ at: now(), ...entry }), 'utf8')
  fs.appendFileSync(EVAL_FILE, '\n', 'utf8')
}

function isProcessAlive(file) {
  const current = readJson(file, null)
  if (!current) return { running: false }
  if (pidAlive(current.pid)) {
    return { running: true, current }
  }
  return { running: false, current, finished: true }
}

function nextScoreboardBaseSeed(state) {
  const completed = state.scoreboardBatchesCompleted || 0
  return SCOREBOARD_BASE_SEED_FIRST + completed * SCOREBOARD_BASE_SEED_STEP
}

function startScoreboard(state) {
  const baseSeed = nextScoreboardBaseSeed(state)
  const logFile = path.join(NIGHT_DIR, `scoreboard-${baseSeed}.log`)
  const out = fs.openSync(logFile, 'a')
  // 批次脚本以 frontend 为工作目录（其产物路径按 ../outputs 解析）
  const child = spawn('node', ['scripts/run-fuzz-batch.mjs', '--total', '100', '--chunk', '100',
    '--base-seed', String(baseSeed)], {
    cwd: FRONTEND,
    detached: true,
    stdio: ['ignore', out, out],
    shell: true,
    env: { ...process.env, FUZZ_SUMMARY_DIR: OUT_DIR },
  })
  child.unref()
  fs.closeSync(out)
  const current = { pid: child.pid, layer: 'scoreboard', baseSeed, startedAt: now(), logFile }
  writeJson(CURRENT_FILE, current)
  state.currentBatch = { layer: 'scoreboard', baseSeed, startedAt: current.startedAt }
  appendEvaluation({ event: 'scoreboard-started', baseSeed, pid: child.pid })
  return current
}

function runBackend(state) {
  const chaosSeed = String(Math.floor(Math.random() * 1e12))
  const logFile = path.join(NIGHT_DIR, `backend-engines-${chaosSeed}.log`)
  const result = spawnSync('mvn', ['-q', 'test', `-Dtest=TournamentEngineChaosTest`,
    `-Dchaos.seed=${chaosSeed}`, '-DfailIfNoTests=true'], {
    cwd: path.join(REPO, 'backend'),
    encoding: 'utf8',
    timeout: 10 * 60 * 1000,
    shell: true,
  })
  fs.writeFileSync(logFile, `exit=${result.status}\nstdout/stderr:\n${result.stdout || ''}\n${result.stderr || ''}`, 'utf8')
  const failed = result.status !== 0
  appendEvaluation({
    event: failed ? 'backend-failed' : 'backend-passed',
    chaosSeed,
    exitCode: result.status,
    logFile: path.relative(REPO, logFile),
  })
  state.lastBackend = { at: now(), ok: !failed, chaosSeed }
  return failed
}

function runFlow(state) {
  const flowSeed = String(Math.floor(Math.random() * 1e12))
  const iters = 6
  const logFile = path.join(NIGHT_DIR, `lineup-flow-${flowSeed}.log`)
  const result = spawnSync('npx', ['vitest', 'run', 'src/pages/volleyball/lineup-flow-fuzzer.test.js'], {
    cwd: path.join(REPO, 'frontend'),
    encoding: 'utf8',
    timeout: 20 * 60 * 1000,
    shell: true,
    env: {
      ...process.env,
      FUZZ_FLOW_ITERS: String(iters),
      FUZZ_FLOW_SEED: flowSeed,
      FUZZ_FLOW_OUT: NIGHT_DIR,
    },
  })
  fs.writeFileSync(logFile, `exit=${result.status}\nstdout/stderr:\n${result.stdout || ''}\n${result.stderr || ''}`, 'utf8')
  const failed = result.status !== 0
  appendEvaluation({
    event: failed ? 'flow-failed' : 'flow-passed',
    flowSeed,
    iters,
    exitCode: result.status,
    logFile: path.relative(REPO, logFile),
  })
  state.lastFlow = { at: now(), ok: !failed, flowSeed }
  return failed
}

function evaluateScoreboardBatch(state) {
  const manifest = readJson(path.join(OUT_DIR, 'run-manifest.json'), null)
  if (!manifest || !manifest.finishedAt || !Array.isArray(manifest.chunks) || manifest.chunks.length === 0) {
    return { completed: false }
  }
  const startedAt = Date.parse(manifest.startedAt)
  const known = state.evaluatedBatches || {}
  if (known[manifest.startedAt]) {
    return { completed: false, alreadyEvaluated: true }
  }
  const summary = readJson(path.join(OUT_DIR, 'fuzz-summary.json'), {})
  const entry = {
    event: 'scoreboard-completed',
    baseSeed: manifest.baseSeed,
    startedAt: manifest.startedAt,
    finishedAt: manifest.finishedAt,
    matches: summary.matchCount,
    stats: summary.stats,
    coverage: summary.coverage || null,
    chunkExitCodes: manifest.chunks.map((c) => c.exitCode),
  }
  appendEvaluation(entry)
  known[manifest.startedAt] = true
  state.evaluatedBatches = known
  state.scoreboardBatchesCompleted = (state.scoreboardBatchesCompleted || 0) + 1
  return { completed: true, entry }
}

function main() {
  const mode = process.argv.includes('--mode') ? process.argv[process.argv.indexOf('--mode') + 1] : 'auto'
  ensureDirs()
  const state = readJson(STATE_FILE, {
    scoreboardBatchesCompleted: 0,
    evaluatedBatches: {},
    nextLayerIndex: 0,
  })

  if (mode === 'status') {
    const { running, current } = isProcessAlive(CURRENT_FILE)
    console.log(JSON.stringify({ running, current, state }, null, 2))
    return
  }

  // 1. 是否有批次在跑
  const { running, current, finished } = isProcessAlive(CURRENT_FILE)
  if (running) {
    const minutes = Math.round((Date.now() - Date.parse(current.startedAt)) / 60000)
    console.log(`[runner] 批次仍在运行: layer=${current.layer} baseSeed=${current.baseSeed || ''} ` +
      `已运行 ${minutes} 分钟 (pid=${current.pid})。本轮不启动新任务。`)
    appendEvaluation({ event: 'still-running', layer: current.layer, minutes })
    return
  }
  if (finished && current) {
    // 上一批刚结束：清掉 current 标记，稍后评估
    try {
      fs.unlinkSync(CURRENT_FILE)
    } catch (_) { /* ignore */ }
  }

  // 2. 评估已完成的计分批次
  const evaluation = evaluateScoreboardBatch(state)
  if (evaluation.completed) {
    console.log(`[runner] 计分批次完成并已评估: baseSeed=${evaluation.entry.baseSeed} ` +
      `matches=${evaluation.entry.matches} stats=${JSON.stringify(evaluation.entry.stats)}`)
  }

  // 3. 轮换启动下一层
  let failures = 0
  const layer = LAYERS[state.nextLayerIndex % LAYERS.length]
  state.nextLayerIndex = (state.nextLayerIndex % LAYERS.length) + 1
  console.log(`[runner] 本轮启动层: ${layer}`)

  if (layer === 'scoreboard') {
    const current = startScoreboard(state)
    console.log(`[runner] 计分混沌批已后台启动: baseSeed=${current.baseSeed} pid=${current.pid}（预计约 55~75 分钟）`)
  } else if (layer === 'backend') {
    failures += runBackend(state) ? 1 : 0
    console.log('[runner] 后端赛制引擎混沌完成')
  } else if (layer === 'flow') {
    failures += runFlow(state) ? 1 : 0
    console.log('[runner] 跨局填写流混沌完成')
  }

  writeJson(STATE_FILE, state)
  console.log(`[runner] 本轮结束 at ${now()} failures=${failures}`)
  if (failures > 0 && process.exitCode !== 1) {
    process.exitCode = 1
  }
}

main()
