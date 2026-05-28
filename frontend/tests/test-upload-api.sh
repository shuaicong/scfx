#!/bin/bash
# 后端 upload API 端到端测试
# 创建一个 .docx 文件，通过 curl 上传到后端，验证 contentHtml 是否正确存储

set -e

BASE_URL="${1:-http://localhost:8081}"
PASS=0
FAIL=0

green() { echo -e "\033[32m$1\033[0m"; }
red() { echo -e "\033[31m$1\033[0m"; }

check() {
  if [ $? -eq 0 ] && [ -n "$2" ] && echo "$2" | grep -q "$3"; then
    green "  ✅ $1"
    PASS=$((PASS + 1))
  else
    red "  ❌ $1"
    echo "     expected: $3"
    echo "     got: $2"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== 后端 upload API 端到端测试 ===
"

# 1. 后端是否可用
echo "1. 检查后端状态..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/actuator/health" 2>&1 || echo "000")
if [ "$HEALTH" = "200" ] || [ "$HEALTH" = "503" ]; then
  green "  ✅ 后端可访问 (HTTP $HEALTH)"
  PASS=$((PASS + 1))
else
  red "  ❌ 后端不可访问 (HTTP $HEALTH)"
  echo "    BASE_URL=$BASE_URL"
  FAIL=$((FAIL + 1))
fi

# 2. 创建测试 .docx（用 Python）
echo ""
echo "2. 创建测试 .docx..."
TEST_DIR=$(mktemp -d)
DOCX_FILE="$TEST_DIR/test.docx"

python3 -c "
import struct, zlib, os

# Minimal .docx with formatting: h1, bold, italic, paragraph
doc_xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">
  <w:body>
    <w:p>
      <w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>
      <w:r><w:t>测试一级标题</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:rPr><w:b/></w:rPr><w:t>这是加粗文字</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:rPr><w:i/></w:rPr><w:t>这是斜体文字</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>普通段落文本</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>加粗+斜体</w:t></w:r>
    </w:p>
  </w:body>
</w:document>'''

content_types_xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">
  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>
  <Default Extension=\"xml\" ContentType=\"application/xml\"/>
  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>
</Types>'''

rels_xml = '''<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">
  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>
</Relationships>'''

import zipfile
with zipfile.ZipFile('$DOCX_FILE', 'w') as z:
    z.writestr('[Content_Types].xml', content_types_xml)
    z.writestr('_rels/.rels', rels_xml)
    z.writestr('word/document.xml', doc_xml)
"

if [ -f "$DOCX_FILE" ]; then
  SIZE=$(stat -f%z "$DOCX_FILE" 2>/dev/null || stat --format=%s "$DOCX_FILE" 2>/dev/null || echo "?")
  green "  ✅ 测试文档已创建 ($SIZE bytes)"
  PASS=$((PASS + 1))
else
  red "  ❌ 测试文档创建失败"
  FAIL=$((FAIL + 1))
fi

# 3. 上传文件（不含 contentHtml）— 验证后端接收正常
echo ""
echo "3. 上传文件（前端自动生成 contentHtml）..."
UPLOAD_RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/upload" \
  -F "file=@$DOCX_FILE" \
  -F "categoryId=1" \
  -F "title=测试文档-$(date +%s)" \
  -F "idempotentKey=test-$(date +%s)-$$" \
  -w "\nHTTP_CODE:%{http_code}" 2>&1)

HTTP_CODE=$(echo "$UPLOAD_RESP" | grep "HTTP_CODE:" | cut -d: -f2)
BODY=$(echo "$UPLOAD_RESP" | grep -v "HTTP_CODE:")

if [ "$HTTP_CODE" = "200" ]; then
  green "  ✅ 上传成功 (HTTP 200)"
  PASS=$((PASS + 1))
else
  red "  ❌ 上传失败 (HTTP $HTTP_CODE)"
  echo "     $BODY"
  FAIL=$((FAIL + 1))
fi

# 4. 上传文件（含 contentHtml）— 模拟前端 mammoth 解析后
echo ""
echo "4. 上传文件（模拟前端携带 mammoth 解析的 contentHtml）..."
CONTENT_HTML='<h1>测试标题</h1><p><strong>加粗内容</strong></p><p><em>斜体内容</em></p><p>普通段落</p>'
UPLOAD2_RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/upload" \
  -F "file=@$DOCX_FILE" \
  -F "categoryId=1" \
  -F "title=测试文档-HTML-$(date +%s)" \
  -F "contentHtml=$CONTENT_HTML" \
  -F "idempotentKey=test-$(date +%s)-$$" \
  -w "\nHTTP_CODE:%{http_code}" 2>&1)

HTTP_CODE2=$(echo "$UPLOAD2_RESP" | grep "HTTP_CODE:" | cut -d: -f2)
BODY2=$(echo "$UPLOAD2_RESP" | grep -v "HTTP_CODE:")

if [ "$HTTP_CODE2" = "200" ]; then
  green "  ✅ 上传成功 (HTTP 200)"
  PASS=$((PASS + 1))

  # 5. 验证响应中包含 contentHtml
  echo ""
  echo "5. 验证响应中 contentHtml..."
  if echo "$BODY2" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('data') and data['data'].get('contentHtml'):
    html = data['data']['contentHtml']
    print(f'contentHtml 存在, 长度: {len(html)}')
    print(f'内容: {html}')
    assert '<h1>' in html, '缺少 <h1>'
    assert '<strong>' in html or '<b>' in html, '缺少加粗'
    assert '<p>' in html, '缺少段落'
    sys.exit(0)
else:
    print('contentHtml 为空或缺失')
    sys.exit(1)
" 2>&1; then
    green "  ✅ contentHtml 正确返回并包含格式标签"
    PASS=$((PASS + 1))

    # 6. 通过 GET 验证持久化
    echo ""
    echo "6. 通过 GET /knowledge/{id} 验证持久化..."
    KB_ID=$(echo "$BODY2" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
    if [ -n "$KB_ID" ] && [ "$KB_ID" != "0" ]; then
      GET_RESP=$(curl -s "$BASE_URL/api/knowledge/$KB_ID" 2>&1)
      if echo "$GET_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('data') and data['data'].get('contentHtml'):
    html = data['data']['contentHtml']
    print(f'GET 验证 contentHtml 存在, 长度: {len(html)}')
    print(f'内容: {html}')
    assert html == '$CONTENT_HTML', 'contentHtml 内容不匹配'
    sys.exit(0)
else:
    print('GET 返回中 contentHtml 为空')
    sys.exit(1)
" 2>&1; then
        green "  ✅ contentHtml 持久化验证通过"
        PASS=$((PASS + 1))
      else
        red "  ❌ contentHtml 持久化验证失败"
        echo "     $GET_RESP"
        FAIL=$((FAIL + 1))
      fi
    fi
  else
    red "  ❌ contentHtml 格式验证失败"
    FAIL=$((FAIL + 1))
  fi
else
  echo "    (跳过 contentHtml 验证 — 上传失败)"
  red "  ❌ 上传含 contentHtml 的请求失败 (HTTP $HTTP_CODE2)"
  echo "     $BODY2"
  FAIL=$((FAIL + 1))
fi

# 清理
rm -rf "$TEST_DIR"

echo ""
echo "=== 结果: $PASS passed, $FAIL failed ==="
[ $FAIL -eq 0 ] && echo "🎉 所有测试通过！" || echo "❌ $FAIL 个测试失败"

exit $FAIL
