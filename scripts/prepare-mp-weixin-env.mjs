import fs from 'node:fs'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'

const API_PORT = 8080
const ENV_FILE = path.resolve('.env.local')
const DEVELOPMENT_KEY = 'VITE_API_BASE_URL_DEVELOPMENT'

function isPrivateIPv4(ip) {
  return /^10\./.test(ip)
    || /^192\.168\./.test(ip)
    || /^172\.(1[6-9]|2\d|3[0-1])\./.test(ip)
}

function scoreInterface(name, address) {
  const lowerName = name.toLowerCase()
  let score = 0

  if (/wlan|wi-?fi|wireless/.test(lowerName)) score += 40
  if (/ethernet|lan/.test(lowerName)) score += 20
  if (isPrivateIPv4(address)) score += 10
  if (/^192\.168\./.test(address)) score += 5
  if (/vmware|virtualbox|vbox|hyper-v|wsl|vethernet|bluetooth|loopback/.test(lowerName)) score -= 100

  return score
}

function pickLocalIPv4() {
  const candidates = []
  const interfaces = os.networkInterfaces()

  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const address of addresses || []) {
      if (address.family !== 'IPv4' || address.internal) continue
      if (!isPrivateIPv4(address.address)) continue

      candidates.push({
        name,
        address: address.address,
        score: scoreInterface(name, address.address),
      })
    }
  }

  candidates.sort((a, b) => b.score - a.score)
  return candidates[0] || null
}

function upsertEnvValue(content, key, value) {
  const line = `${key}=${value}`
  const pattern = new RegExp(`^${key}=.*$`, 'm')

  if (pattern.test(content)) {
    return content.replace(pattern, line)
  }

  const suffix = content.endsWith('\n') || content.length === 0 ? '' : '\n'
  return `${content}${suffix}${line}\n`
}

function canConnect(host, port, timeoutMs = 800) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port })
    let settled = false

    function finish(result) {
      if (settled) return
      settled = true
      socket.destroy()
      resolve(result)
    }

    socket.setTimeout(timeoutMs)
    socket.once('connect', () => finish(true))
    socket.once('timeout', () => finish(false))
    socket.once('error', () => finish(false))
  })
}

const candidate = pickLocalIPv4()

if (!candidate) {
  console.error('[mp-weixin-env] No available private IPv4 address was found.')
  process.exit(1)
}

const apiBaseUrl = `http://${candidate.address}:${API_PORT}`
const currentContent = fs.existsSync(ENV_FILE) ? fs.readFileSync(ENV_FILE, 'utf8') : ''
const nextContent = upsertEnvValue(currentContent, DEVELOPMENT_KEY, apiBaseUrl)

fs.writeFileSync(ENV_FILE, nextContent, 'utf8')

console.log(`[mp-weixin-env] ${DEVELOPMENT_KEY}=${apiBaseUrl}`)
console.log(`[mp-weixin-env] selected ${candidate.name} (${candidate.address})`)

const backendReachable = await canConnect(candidate.address, API_PORT)
if (!backendReachable) {
  console.error(`[mp-weixin-env] Backend is not reachable at ${apiBaseUrl}. Start backend first, then run this command again.`)
  process.exit(1)
}

console.log(`[mp-weixin-env] backend reachable at ${apiBaseUrl}`)
