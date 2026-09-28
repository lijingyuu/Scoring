/**
 * 排球状态机无头混沌测试执行器 (CLI Runner)
 * 运行方式:
 *   node scripts/run-fuzz.mjs --matches 100 --seed 42
 *   node scripts/run-fuzz.mjs --matches 1000
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawn } from 'node:child_process'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')

// 解析命令行参数
const args = process.argv.slice(2)
let matchCount = 100
let baseSeed = Math.floor(Math.random() * 1000000)

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--matches' && args[i + 1]) {
    matchCount = parseInt(args[i + 1], 10)
    i++
  } else if (args[i] === '--seed' && args[i + 1]) {
    baseSeed = parseInt(args[i + 1], 10)
    i++
  }
}

console.log('====================================================================')
console.log(`🏐 Eunomia 排球状态机无头混沌测试 (Headless Fuzzer)`)
console.log(`- 目标场次: ${matchCount} 场`)
console.log(`- 随机基准种子 (Base Seed): ${baseSeed}`)
console.log(`- 环境: Node.js + Vitest Headless Engine`)
console.log('====================================================================\n')

const env = {
  ...process.env,
  FUZZ_MATCHES: String(matchCount),
  FUZZ_SEED: String(baseSeed),
  FUZZ_SUMMARY_DIR: path.resolve(projectRoot, '../outputs/fuzz-volleyball'),
}

// 调用 vitest 运行 scoreboard-fuzzer.test.js
const vitestProcess = spawn(
  'npx',
  ['vitest', 'run', 'src/pages/volleyball/scoreboard-fuzzer.test.js'],
  {
    cwd: projectRoot,
    env,
    stdio: 'inherit',
    shell: true,
  }
)

vitestProcess.on('exit', (code) => {
  console.log('\n--------------------------------------------------------------------')
  if (code === 0) {
    console.log(`✅ 混沌测试全部通过！共运行 ${matchCount} 场排球比赛，未违反任何硬性不变式。`)
  } else {
    console.log(`⚠️ 混沌测试发现了潜在逻辑异常 (Exit code: ${code})。`)
    console.log(`📋 详细异常轨迹已记录至: outputs/fuzz-volleyball/fuzz-anomalies.json`)
    console.log(`💡 可在夜间指派 Agent 读取异常文件，复现并出具修复代码。`)
  }
  console.log('--------------------------------------------------------------------')
  process.exit(code || 0)
})
