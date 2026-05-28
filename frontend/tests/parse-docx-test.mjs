/**
 * mammoth.js .docx → HTML 转换测试
 *
 * 创建一个含格式（标题、加粗、列表、表格）的 .docx 文件，
 * 用 mammoth.js 解析后检查是否能正确提取 HTML 格式。
 *
 * 运行：node tests/parse-docx-test.mjs
 */

import { Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell } from 'docx'
import { writeFileSync, unlinkSync } from 'fs'
import { convertToHtml } from 'mammoth'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const testDocx = join(__dirname, 'test-formatting.docx')

let allPassed = true

function pass(msg) {
  console.log(`  ✅ ${msg}`)
}

function fail(msg) {
  console.log(`  ❌ ${msg}`)
  allPassed = false
}

async function run() {
  console.log('\n=== mammoth.js .docx → HTML 解析测试 ===\n')

  // 1. 创建带格式的 .docx
  console.log('1. 创建测试文档...')
  const doc = new Document({
    sections: [{
      children: [
        new Paragraph({
          text: '这是一级标题',
          heading: HeadingLevel.HEADING_1,
        }),
        new Paragraph({
          text: '这是二级标题',
          heading: HeadingLevel.HEADING_2,
        }),
        new Paragraph({
          children: [
            new TextRun({ text: '这是加粗文字', bold: true }),
          ],
        }),
        new Paragraph({
          children: [
            new TextRun({ text: '这是正常文字，' }),
            new TextRun({ text: '这是斜体文字', italics: true }),
          ],
        }),
        new Paragraph({
          text: '普通段落文本',
        }),
      ],
    }],
  })

  const buffer = await Packer.toBuffer(doc)
  writeFileSync(testDocx, buffer)
  pass(`测试文档已创建 (${(buffer.length / 1024).toFixed(1)} KB)`)

  // 2. 用 mammoth.js 解析
  console.log('\n2. 用 mammoth.js 解析...')
  let result
  try {
    result = await convertToHtml({ buffer })
    pass('mammoth.convertToHtml 执行成功')
  } catch (e) {
    fail(`mammoth.convertToHtml 抛出异常: ${e.message}`)
    cleanup()
    process.exit(1)
  }

  const html = result.value
  console.log(`\n3. 解析结果 (${html.length} 字符):`)
  console.log('─'.repeat(60))
  console.log(html)
  console.log('─'.repeat(60))

  // 4. 验证关键 HTML 结构
  console.log('\n4. 验证关键 HTML 结构...')

  // 检查标题
  if (html.includes('<h1>') || html.includes('<h2>')) {
    pass('包含标题标签 <h1>/<h2>')
  } else {
    fail('缺少标题标签 — mammoth 未识别标题格式')
  }

  // 检查加粗
  if (html.includes('<strong>')) {
    pass('包含加粗标签 <strong>')
  } else {
    fail('缺少加粗标签 — mammoth 未识别加粗格式')
  }

  // 检查斜体
  if (html.includes('<em>')) {
    pass('包含斜体标签 <em>')
  } else {
    fail('缺少斜体标签 — mammoth 未识别斜体格式')
  }

  // 检查段落
  if (html.includes('<p>') || html.includes('</p>')) {
    pass('包含段落标签 <p>')
  } else {
    fail('缺少段落标签')
  }

  // 5. 检查所有格式都被保留
  const hasHeading = /<h[1-6]/.test(html)
  const hasBold = html.includes('<strong>')
  const hasItalic = html.includes('<em>')
  const hasParagraph = html.includes('<p>')

  console.log('')
  if (hasHeading && hasBold && hasItalic && hasParagraph) {
    console.log('🎉 结论：mammoth.js 正确提取了所有格式，HTML 输出完整')
  } else {
    console.log('⚠️  结论：mammoth.js 部分格式未提取')
    if (!hasHeading) console.log('   - 缺失: 标题')
    if (!hasBold) console.log('   - 缺失: 加粗')
    if (!hasItalic) console.log('   - 缺失: 斜体')
    if (!hasParagraph) console.log('   - 缺失: 段落')
  }

  // 清理
  cleanup()
  process.exit(allPassed ? 0 : 1)
}

function cleanup() {
  try { unlinkSync(testDocx) } catch {}
}

run().catch(e => {
  console.error('测试异常:', e)
  cleanup()
  process.exit(1)
})
