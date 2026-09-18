/**
 * 羽毛球混沌测试分块编排器 (Chunked Batch Orchestrator)
 *
 * 设计要点（鲁棒无人值守）：
 *   - 每块独立 vitest 进程；单块承载数十至上百场；
 *   - 产物按 baseSeed 归档到 chunks/<baseSeed>/，互不冲突，可随时重跑；
 *   - 断点续跑：已存在且场数吻合的块产物直接合并并跳过，重复执行同一命令即可续传；
 *   - 块级超时：单块超过 --chunk-timeout 分钟强制终止并继续下一块，不会卡死整晚；
 *   - 增量合并：每块结束立即刷新总报告，中途中断也已保存全部已完成块。
 *
 * 用法:
 *   node scripts/run-bad-fuzz-batch.mjs --total 600 --chunk 100 --base-seed 200000000
 * 产物:
 *   outputs/fuzz-badminton/fuzz-summary.json              合并总报告（每块后增量刷新）
 *   outputs/fuzz-badminton/fuzz-anomalies.json            合并异常详单
 *   outputs/fuzz-badminton/chunks/<baseSeed>/chunk-XXX-*  每块原始产物与日志
 *   outputs/fuzz-badminton/run-manifest.json              本次运行参数与进度
 */
import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(projectRoot, '..')
const outDir = path.join(repoRoot, 'outputs', 'fuzz-badminton')

// ---------- 参数解析 ----------
const args = process.argv.slice(2)
let total = 600
let chunkSize = 100
let baseSeed = 200000000
let chunkTimeoutMin = 30
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--total' && args[i + 1]) { total = parseInt(args[i + 1], 10); i++ }
  else if (args[i] === '--chunk' && args[i + 1]) { chunkSize = parseInt(args[i + 1], 10); i++ }
  else if (args[i] === '--base-seed' && args[i + 1]) { baseSeed = parseInt(args[i + 1], 10); i++ }
  else if (args[i] === '--chunk-timeout' && args[i + 1]) { chunkTimeoutMin = parseInt(args[i + 1], 10); i++ }
}

const chunksDir = path.join(outDir, 'chunks', String(baseSeed))
fs.mkdirSync(chunksDir, { recursive: true })

const manifestPath = path.join(outDir, 'run-manifest.json')

function log(msg) {
  const t = new Date().toISOString().slice(11, 19)
  console.log(`[${t}] ${msg}`)
}

log(`羽毛球混沌测试分块编排: total=${total} chunk=${chunkSize} baseSeed=${baseSeed} 块超时=${chunkTimeoutMin}min`)
log(`产出目录: ${outDir}`)

// ---------- 合并逻辑（从已归档块重建全量状态，天然支持续跑） ----------
const merged = {
  baseSeed,
  matchCount: 0,
  durationMs: 0,
  stats: {
    totalRallies: 0,
    totalUndos: 0,
    totalSideSwitches: 0,
    criticalMatches: 0,
    suspiciousMatches: 0,
    cleanMatches: 0,
  },
  coverage: {
    totalReloads: 0,
    decidingGameSideSwitched: 0,
    decidingGameSideKept: 0,
    manualSideSwitches: 0,
    deepUndos: 0,
    hostileProbes: 0,
    hostileRejected: 0,
    promptActiveProbes: 0,
    terminalProbes: 0,
    capHits: 0,
    retirements: 0,
    matchesDecidingGameReached: 0,
    gamesPlayedHistogram: {},
  },
  criticalSummary: [],
  suspiciousSummary: [],
}
const mergedAnomalies = { critical: [], suspicious: [] }

function mergeChunkFiles(summaryFile, anomaliesFile) {
  let ok = false
  if (fs.existsSync(summaryFile)) {
    const s = JSON.parse(fs.readFileSync(summaryFile, 'utf8'))
    if (s && typeof s.matchCount === 'number') {
      merged.matchCount += s.matchCount
      merged.durationMs += s.durationMs || 0
      for (const k of Object.keys(merged.stats)) merged.stats[k] += (s.stats && s.stats[k]) || 0
      if (s.coverage) {
        for (const ck of Object.keys(merged.coverage)) {
          if (ck === 'gamesPlayedHistogram') {
            for (const gk of Object.keys(s.coverage.gamesPlayedHistogram || {})) {
              merged.coverage.gamesPlayedHistogram[gk] = (merged.coverage.gamesPlayedHistogram[gk] || 0) + s.coverage.gamesPlayedHistogram[gk]
            }
          } else if (typeof merged.coverage[ck] === 'number') {
            merged.coverage[ck] += s.coverage[ck] || 0
          }
        }
      }
      if (Array.isArray(s.criticalSummary)) merged.criticalSummary.push(...s.criticalSummary)
      if (Array.isArray(s.suspiciousSummary)) merged.suspiciousSummary.push(...s.suspiciousSummary)
      ok = true
    }
  }
  if (fs.existsSync(anomaliesFile)) {
    const a = JSON.parse(fs.readFileSync(anomaliesFile, 'utf8'))
    if (Array.isArray(a.critical)) mergedAnomalies.critical.push(...a.critical)
    if (Array.isArray(a.suspicious)) mergedAnomalies.suspicious.push(...a.suspicious)
  }
  return ok
}

function writeMerged() {
  fs.writeFileSync(path.join(outDir, 'fuzz-summary.json'), JSON.stringify(merged, null, 2), 'utf8')
  const mergedAnomaliesPath = path.join(outDir, 'fuzz-anomalies.json')
  if (mergedAnomalies.critical.length > 0 || mergedAnomalies.suspicious.length > 0) {
    fs.writeFileSync(mergedAnomaliesPath, JSON.stringify(mergedAnomalies, null, 2), 'utf8')
  } else if (fs.existsSync(mergedAnomaliesPath)) {
    fs.unlinkSync(mergedAnomaliesPath)
  }
}

// ---------- 计划生成 ----------
const numChunks = Math.ceil(total / chunkSize)
const chunks = []
for (let c = 0; c < numChunks; c++) {
  const count = Math.min(chunkSize, total - c * chunkSize)
  // 保持与 runFuzzerBatch 的 matchSeed = baseSeed + i * 9973 一致的偏移公式
  const offsetSeed = baseSeed + c * chunkSize * 9973
  const id = `chunk-${String(c).padStart(3, '0')}`
  chunks.push({
    index: c,
    id,
    count,
    baseSeed: offsetSeed,
    summaryFile: path.join(chunksDir, `${id}-summary.json`),
    anomaliesFile: path.join(chunksDir, `${id}-anomalies.json`),
    logFile: path.join(chunksDir, `${id}-vitest.log`),
  })
}

// ---------- 预扫已完成块（续跑支撑） ----------
log(`检查断点续跑状态 (${chunks.length} 块)...`)
const pending = []
for (const ch of chunks) {
  if (fs.existsSync(ch.summaryFile)) {
    try {
      const s = JSON.parse(fs.readFileSync(ch.summaryFile, 'utf8'))
      if (s && s.matchCount === ch.count) {
        log(`  [已就绪] ${ch.id} (seed=${ch.baseSeed}, count=${ch.count}) -> 跳过执行，直接合并`)
        mergeChunkFiles(ch.summaryFile, ch.anomaliesFile)
        continue
      }
    } catch (_) {
      // 损坏文件，需重跑
    }
  }
  pending.push(ch)
}
writeMerged()

const manifest = {
  version: 1,
  startedAt: new Date().toISOString(),
  lastUpdatedAt: new Date().toISOString(),
  total,
  chunkSize,
  baseSeed,
  chunkTimeoutMin,
  totalChunks: chunks.length,
  completedChunks: chunks.length - pending.length,
  status: pending.length === 0 ? 'COMPLETED' : 'RUNNING',
  chunks: chunks.map(c => ({
    id: c.id,
    count: c.count,
    baseSeed: c.baseSeed,
    done: !pending.includes(c),
  })),
}
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')

if (pending.length === 0) {
  log(`全量 ${chunks.length} 块已全部存在且吻合，无需计算。合并视图已刷新。`)
  process.exit(0)
}

log(`尚需执行 ${pending.length} 块 (共 ${pending.reduce((acc, c) => acc + c.count, 0)} 场)`)

// ---------- 逐块子进程执行 ----------
function runChunk(ch) {
  return new Promise((resolve) => {
    log(`>>> 启动 ${ch.id} (${ch.count} 场, seed=${ch.baseSeed})`)
    const startTime = Date.now()
    const logStream = fs.createWriteStream(ch.logFile, { flags: 'w' })

    const tempSummaryDir = path.join(chunksDir, `temp-${ch.id}`)
    fs.mkdirSync(tempSummaryDir, { recursive: true })

    const env = {
      ...process.env,
      FUZZ_MATCHES: String(ch.count),
      FUZZ_BASE_SEED: String(ch.baseSeed),
      FUZZ_SUMMARY_DIR: tempSummaryDir,
      NODE_OPTIONS: `${process.env.NODE_OPTIONS || ''} --expose-gc --max-old-space-size=4096`,
    }

    const testFile = 'src/pages/scoreboard/badminton-fuzzer.test.js'
    const isWindows = process.platform === 'win32'
    const npmCmd = isWindows ? 'npm.cmd' : 'npm'

    const child = spawn(npmCmd, ['run', 'test', '--', testFile], {
      cwd: projectRoot,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: true,
    })

    child.stdout.pipe(logStream, { end: false })
    child.stderr.pipe(logStream, { end: false })

    const timeoutMs = chunkTimeoutMin * 60 * 1000
    const timer = setTimeout(() => {
      log(`⚠️ 块 ${ch.id} 超时 (${chunkTimeoutMin}min)！强杀子进程...`)
      logStream.write(`\n[WATCHDOG] Chunk exceeded ${chunkTimeoutMin} min timeout. Terminating.\n`)
      try {
        if (isWindows) {
          spawn('taskkill', ['/pid', String(child.pid), '/f', '/t'])
        } else {
          child.kill('SIGKILL')
        }
      } catch (_) {}
    }, timeoutMs)

    child.on('close', (code) => {
      clearTimeout(timer)
      logStream.end(`\n[EXIT] code=${code} duration=${Date.now() - startTime}ms\n`)

      const tempSummary = path.join(tempSummaryDir, 'fuzz-summary.json')
      const tempAnomalies = path.join(tempSummaryDir, 'fuzz-anomalies.json')
      if (fs.existsSync(tempSummary)) {
        fs.renameSync(tempSummary, ch.summaryFile)
      }
      if (fs.existsSync(tempAnomalies)) {
        fs.renameSync(tempAnomalies, ch.anomaliesFile)
      }
      try { fs.rmdirSync(tempSummaryDir) } catch (_) {}

      const ok = mergeChunkFiles(ch.summaryFile, ch.anomaliesFile)
      writeMerged()

      manifest.completedChunks++
      manifest.lastUpdatedAt = new Date().toISOString()
      const mChunk = manifest.chunks.find(c => c.id === ch.id)
      if (mChunk) {
        mChunk.done = ok
        mChunk.exitCode = code
        mChunk.durationMs = Date.now() - startTime
      }
      fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')

      log(`<<< 完成 ${ch.id} (exit=${code}, ok=${ok}, 累计已完成 ${manifest.completedChunks}/${manifest.totalChunks})`)
      resolve({ id: ch.id, code, ok })
    })
  })
}

for (let i = 0; i < pending.length; i++) {
  const ch = pending[i]
  const res = await runChunk(ch)
  if (!res.ok) {
    log(`⚠️ 警告：块 ${ch.id} 未生成有效的 summary 文件，请查看 ${ch.logFile}`)
  }
}

manifest.status = 'COMPLETED'
manifest.finishedAt = new Date().toISOString()
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')

log(`🎉 全部 ${chunks.length} 块执行完毕！`)
log(`总场数: ${merged.matchCount}, 干净场: ${merged.stats.cleanMatches}, 异常场: ${merged.stats.criticalMatches}, 总耗时: ${(merged.durationMs / 1000).toFixed(1)}s`)
