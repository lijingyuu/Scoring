// 检查 .vue 单文件组件里"模板/脚本引用了未声明标识符"的问题。
// 背景：<script setup> 下若某标识符不存在，模板会退化为 _ctx.xxx（编译不报错、运行时静默 undefined），
// 脚本作用域则直接 ReferenceError。Vite 构建不会发现，所以单独做一次静态检查。
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const SRC = join(ROOT, 'src')

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue
      walk(full, out)
    } else if (name.endsWith('.vue')) {
      out.push(full)
    }
  }
  return out
}

// 模板里合法出现、无需 setup 绑定的标识符
const ALLOWED = new Set([
  'true', 'false', 'null', 'undefined', 'Math', 'Number', 'String', 'Boolean', 'Array', 'Object',
  'JSON', 'Date', 'console', '$event', '$slots', '$attrs', '$props', '$emit', '$refs', '$nextTick',
  'window', 'document', 'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'encodeURIComponent',
  'decodeURIComponent', 'setTimeout', 'clearTimeout',
])

let problems = 0
for (const file of walk(SRC)) {
  const source = readFileSync(file, 'utf8')
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length) {
    console.log(`${relative(ROOT, file)}: 解析失败 ${errors[0].message}`)
    problems++
    continue
  }
  let script = ''
  let bindings
  try {
    const compiled = compileScript(descriptor, { id: 'check' })
    script = compiled.content
    bindings = compiled.bindings
  } catch (error) {
    console.log(`${relative(ROOT, file)}: script 编译失败 ${error.message}`)
    problems++
    continue
  }
  if (descriptor.template) {
    const { code } = compileTemplate({
      source: descriptor.template.content,
      filename: file,
      id: 'check',
      compilerOptions: { bindingMetadata: bindings },
    })
    const used = new Set()
    for (const match of code.matchAll(/_ctx\.([A-Za-z_$][\w$]*)/g)) used.add(match[1])
    const missing = [...used].filter((name) => !ALLOWED.has(name))
    // 只报告脚本里也没有声明、且不是 Vue 内置的标识符
    const unresolved = missing.filter((name) => {
      if (script.includes(`const ${name}`) || script.includes(`function ${name}`)) return false
      if (script.includes(`${name} =`) || script.includes(`${name}(`)) return false
      return true
    })
    if (unresolved.length) {
      console.log(`${relative(ROOT, file)}: 模板引用了未声明标识符 → ${unresolved.join(', ')}`)
      problems++
    }
  }
}

if (problems === 0) {
  console.log('OK: 未发现未声明标识符')
} else {
  console.log(`发现 ${problems} 个文件存在问题`)
  process.exitCode = 1
}
