/**
 * 排球混沌测试分块编排器 (Chunked Batch Orchestrator)
 *
 * 设计要点（鲁棒无人值守）：
 *   - 每块独立 vitest 进程；仿真器每场结束会强制 major GC（需
 *     NODE_OPTIONS=--expose-gc，本脚本已注入），单块可承载上百场；
 *   - 产物按 baseSeed 归档到 chunks/<baseSeed>/，互不冲突，可随时重跑；
 *   - 断点续跑：已存在且场数吻合的块产物直接合并并跳过，重复执行同一命令即可续传；
 *   - 块级超时：单块超过 --chunk-timeout 分钟强制终止并继续下一块，不会卡死整晚；
 *   - 增量合并：每块结束立即刷新总报告，中途中断也已保存全部已完成块。
 *
 * 用法:
 *   node scripts/run-fuzz-batch.mjs --total 600 --chunk 100 --base-seed 20260916
 * 产物:
 *   outputs/fuzz-volleyball/fuzz-summary.json              合并总报告（每块后增量刷新）
 *   outputs/fuzz-volleyball/fuzz-anomalies.json            合并异常详单
 *   outputs/fuzz-volleyball/chunks/<baseSeed>/chunk-XXX-*  每块原始产物与日志
 *   outputs/fuzz-volleyball/run-manifest.json              本次运行参数与进度
 */
import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(projectRoot, '..')
const outDir = path.join(repoRoot, 'outputs', 'fuzz-volleyball')

// ---------- 参数解析 ----------
const args = process.argv.slice(2)
let total = 600
let chunkSize = 100
let baseSeed = Math.floor(Math.random() * 1000000)
let chunkTimeoutMin = 90
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

log(`排球混沌测试分块编排: total=${total} chunk=${chunkSize} baseSeed=${baseSeed} 块超时=${chunkTimeoutMin}min`)
log(`产出目录: ${outDir}`)

// ---------- 合并逻辑（从已归档块重建全量状态，天然支持续跑） ----------
const merged = {
  baseSeed,
  matchCount: 0,
  durationMs: 0,
  stats: {
    totalRallies: 0,
    totalSubstitutions: 0,
    totalUndos: 0,
    criticalMatches: 0,
    suspiciousMatches: 0,
    cleanMatches: 0,
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
  if (mergedAnomalies.critical.length > 0 || mergedAnomalies.suspicious.length > 0) {
    fs.writeFileSync(
      path.join(outDir, 'fuzz-anomalies.json'),
      JSON.stringify(mergedAnomalies, null, 2),
      'utf8'
    )
  }
}

// ---------- 分块执行 ----------
const manifest = {
  total,
  chunkSize,
  baseSeed,
  chunkTimeoutMin,
  command: `node scripts/run-fuzz-batch.mjs --total ${total} --chunk ${chunkSize} --base-seed ${baseSeed}`,
  startedAt: new Date().toISOString(),
  chunks: [],
}
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')

const chunkCount = Math.ceil(total / chunkSize)
const runStartedAt = Date.now()

for (let c = 0; c < chunkCount; c++) {
  const matchesInChunk = Math.min(chunkSize, total - c * chunkSize)
  // 与 runFuzzerBatch 的 seed 公式对齐：块内第 i 场全局序号 g = c*chunkSize + i
  // chunk 内 seed = (baseSeed + c*chunkSize*9973) + i*9973 = baseSeed + g*9973，全局连续
  const chunkSeed = baseSeed + c * chunkSize * 9973
  const chunkTag = `chunk-${String(c).padStart(3, '0')}`
  const chunkSummaryFile = path.join(chunksDir, `${chunkTag}-summary.json`)
  const chunkAnomaliesFile = path.join(chunksDir, `${chunkTag}-anomalies.json`)
  const chunkLogFile = path.join(chunksDir, `${chunkTag}-vitest.log`)

  // 断点续跑：本块产物已存在且场数吻合 → 跳过
  let preValid = false
  if (fs.existsSync(chunkSummaryFile)) {
    try {
      const prev = JSON.parse(fs.readFileSync(chunkSummaryFile, 'utf8'))
      preValid = prev && prev.matchCount === matchesInChunk
    } catch (_) { /* 损坏文件当作不存在，重跑 */ }
  }
  if (preValid) {
    mergeChunkFiles(chunkSummaryFile, chunkAnomaliesFile)
    log(`>>> ${chunkTag} 已有完整产物，跳过 (累计 ${merged.matchCount}/${total})`)
    continue
  }

  const elapsedMin = ((Date.now() - runStartedAt) / 60000).toFixed(1)
  log(`>>> ${chunkTag} 开始 (${c + 1}/${chunkCount}, ${matchesInChunk} 场, seed=${chunkSeed}, 已耗时=${elapsedMin}min)`)

  // 清理上一块遗留的中间产物，防止误归档
  const srcSummary = path.join(outDir, 'fuzz-summary.json')
  const srcAnomalies = path.join(outDir, 'fuzz-anomalies.json')
  if (fs.existsSync(srcSummary)) fs.rmSync(srcSummary)
  if (fs.existsSync(srcAnomalies)) fs.rmSync(srcAnomalies)

  const env = {
    ...process.env,
    FUZZ_MATCHES: String(matchesInChunk),
    FUZZ_SEED: String(chunkSeed),
    NODE_OPTIONS: '--expose-gc --max-old-space-size=4096',
  }

  const exitCode = await new Promise((resolve) => {
    const out = fs.openSync(chunkLogFile, 'a')
    fs.appendFileSync(chunkLogFile, `\n==== run started ${new Date().toISOString()} (matches=${matchesInChunk}, seed=${chunkSeed}) ====\n`)
    const child = spawn('npx', ['vitest', 'run', 'src/pages/volleyball/scoreboard-fuzzer.test.js'], {
      cwd: projectRoot,
      env,
      stdio: ['ignore', out, out],
      shell: true,
    })
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      log(`!!! ${chunkTag} 超过 ${chunkTimeoutMin} 分钟，强制终止`)
      if (process.platform === 'win32') {
        spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore', shell: true })
      } else {
        child.kill('SIGKILL')
      }
    }, chunkTimeoutMin * 60 * 1000)
    child.on('exit', (code) => {
      clearTimeout(timer)
      fs.closeSync(out)
      fs.appendFileSync(chunkLogFile, `==== run exited code=${code} timedOut=${timedOut} at ${new Date().toISOString()} ====\n`)
      resolve(timedOut ? -99 : code)
    })
    child.on('error', (err) => {
      clearTimeout(timer)
      fs.closeSync(out)
      fs.appendFileSync(chunkLogFile, `==== spawn error: ${err.message} ====\n`)
      resolve(-1)
    })
  })

  // 归档本块产物：以「落盘 summary 且场数吻合」为准，不信任退出码
  let chunkOk = false
  if (fs.existsSync(srcSummary)) {
    fs.renameSync(srcSummary, chunkSummaryFile)
    if (fs.existsSync(srcAnomalies)) fs.renameSync(srcAnomalies, chunkAnomaliesFile)
    chunkOk = mergeChunkFiles(chunkSummaryFile, chunkAnomaliesFile)
  }

  manifest.chunks.push({
    tag: chunkTag,
    seed: chunkSeed,
    matches: matchesInChunk,
    exitCode,
    ok: chunkOk,
  })
  manifest.updatedAt = new Date().toISOString()
  manifest.mergedStats = { ...merged.stats }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')
  writeMerged()

  if (chunkOk) {
    log(`<<< ${chunkTag} 完成 exit=${exitCode} | 累计 ${merged.matchCount}/${total} 场, critical=${merged.stats.criticalMatches}, suspicious=${merged.stats.suspiciousMatches}`)
  } else {
    log(`!!! ${chunkTag} 失败 exit=${exitCode}，产物不完整，详见 ${chunkLogFile}`)
  }
}

manifest.finishedAt = new Date().toISOString()
manifest.mergedStats = { ...merged.stats }
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8')

const totalMin = ((Date.now() - runStartedAt) / 60000).toFixed(1)
log('====================================================================')
log(`全部结束: 有效 ${merged.matchCount}/${total} 场, 耗时 ${totalMin} 分钟`)
log(`critical=${merged.stats.criticalMatches} suspicious=${merged.stats.suspiciousMatches} clean=${merged.stats.cleanMatches}`)
log(`总报告: ${path.join(outDir, 'fuzz-summary.json')}`)
if (mergedAnomalies.critical.length || mergedAnomalies.suspicious.length) {
  log(`异常详单: ${path.join(outDir, 'fuzz-anomalies.json')}`)
}
