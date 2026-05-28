import { chromium } from 'playwright';

const FRONTEND_URL = 'http://localhost:5173';

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();

  page.on('pageerror', err => console.log('[PAGE ERROR]', err.message));

  console.log('1. Navigating to knowledge page...');
  await page.goto(`${FRONTEND_URL}/knowledge`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);

  // Check view mode - should default to 'table'
  console.log('2. Checking page structure...');

  // Check if we're in table view
  let tableView = page.locator('#tableView');
  let cardView = page.locator('#cardGrid');

  let inTableView = await tableView.isVisible().catch(() => false);
  let inCardView = await cardView.isVisible().catch(() => false);
  console.log(`   Table view visible: ${inTableView}, Card view visible: ${inCardView}`);

  // If in card view, switch to table
  if (inCardView) {
    console.log('   Switching to table view...');
    await page.locator('button:has-text("表格")').click();
    await page.waitForTimeout(500);
  }

  // Wait for table to load
  await page.waitForTimeout(1000);

  // Take screenshot of list
  await page.screenshot({ path: '/tmp/test-01-list.png' });
  console.log('   Screenshot: /tmp/test-01-list.png');

  // Check for table body rows
  const tableRows = page.locator('#tableBody tr').filter({ has: page.locator('td') });
  const rowCount = await tableRows.count();
  console.log(`3. Found ${rowCount} data rows in table`);

  if (rowCount === 0) {
    console.log('   No data rows, trying to find any clickable item...');
    const pageText = await page.locator('body').textContent();
    console.log('   Page text preview:', pageText.substring(500, 1000));
    await browser.close();
    return;
  }

  // Click the first row's "查看" action button (the eye icon button)
  console.log('4. Clicking first row to open detail panel...');
  const firstRowActionBtn = tableRows.first().locator('.action-btn').first();
  const actionBtnVisible = await firstRowActionBtn.isVisible();
  console.log(`   Action button visible: ${actionBtnVisible}`);

  if (actionBtnVisible) {
    await firstRowActionBtn.click();
  } else {
    // Fallback: click the row itself
    await tableRows.first().click();
  }
  await page.waitForTimeout(2000);

  // Take screenshot after clicking
  await page.screenshot({ path: '/tmp/test-02-preview-open.png' });
  console.log('   Screenshot: /tmp/test-02-preview-open.png');

  // Check if preview panel is visible
  const previewPanel = page.locator('#previewPanel');
  const previewVisible = await previewPanel.isVisible();
  console.log(`5. Preview panel visible: ${previewVisible}`);

  if (!previewVisible) {
    // Try clicking a sidebar mode list item
    console.log('   Preview panel not visible. Trying sidebar mode list...');
    const sidebarItems = page.locator('.sidebar-mode-item');
    const sidebarCount = await sidebarItems.count();
    console.log(`   Sidebar mode items: ${sidebarCount}`);

    if (sidebarCount > 0) {
      await sidebarItems.first().click();
      await page.waitForTimeout(1500);
      await page.screenshot({ path: '/tmp/test-02b-sidebar-click.png' });
    }

    const previewVisible2 = await previewPanel.isVisible();
    console.log(`   Preview panel visible after sidebar click: ${previewVisible2}`);

    if (!previewVisible2) {
      console.log('   Could not open preview panel. Exiting.');
      await browser.close();
      return;
    }
  }

  // Now look for the "文本块" section
  console.log('6. Looking for "文本块" section in preview panel...');
  const previewContent = await previewPanel.textContent();
  console.log(`   Preview panel content (first 300): ${(previewContent || '').substring(0, 300)}`);

  // Find the label with "文本块" text
  const allLabels = page.locator('.label');
  const labelCount = await allLabels.count();
  console.log(`   Found ${labelCount} label elements`);

  let textBlockFound = false;
  for (let i = 0; i < labelCount; i++) {
    const text = await allLabels.nth(i).textContent();
    if (text && text.includes('文本块')) {
      textBlockFound = true;
      console.log(`   ✅ Found "文本块" label at index ${i}`);

      // Get the parent .preview-meta-item
      const parentItem = allLabels.nth(i).locator('..');
      const parentHtml = await parentItem.innerHTML();
      console.log(`   Parent HTML: ${parentHtml.substring(0, 300)}`);

      // Check for "查看" button in this section
      const viewBtn = parentItem.locator('button:has-text("查看")');
      const viewBtnCount = await viewBtn.count();
      console.log(`   "查看" button count: ${viewBtnCount}`);

      if (viewBtnCount > 0) {
        const isVisible = await viewBtn.first().isVisible();
        console.log(`   ✅ "查看" button visible: ${isVisible}`);

        if (isVisible) {
          console.log('\n🎉 SUCCESS: "查看" button is visible in the "文本块" section!');
          console.log(`   Button text: "${await viewBtn.first().textContent()}"`);

          // Click the button
          console.log('\n7. Clicking "查看" button...');
          await viewBtn.first().click();
          await page.waitForTimeout(2000);
          await page.screenshot({ path: '/tmp/test-03-drawer.png' });
          console.log('   Screenshot: /tmp/test-03-drawer.png');

          // Verify drawer opened
          const drawer = page.locator('.el-drawer');
          const drawerVisible = await drawer.isVisible();
          console.log(`   Drawer visible: ${drawerVisible}`);

          if (drawerVisible) {
            const title = await page.locator('.el-drawer__title').textContent();
            const body = await page.locator('.el-drawer__body').textContent();
            console.log(`   Drawer title: "${title}"`);
            console.log(`   Drawer body (first 200): "${(body || '').substring(0, 200)}"`);

            // Check for chunk cards or empty state
            const chunkCards = page.locator('.chunk-card').count();
            const emptyState = page.locator('.el-drawer__body div:has-text("暂无切片数据")').count();
            console.log(`   Chunk cards: ${await chunkCards}, Empty state: ${await emptyState}`);

            console.log('\n🎉🎉🎉 CHUNK DRAWER WORKS! 🎉🎉🎉');
          } else {
            console.log('❌ Drawer did not appear after clicking "查看"');
          }
        }
      } else {
        console.log('❌ No "查看" button found in "文本块" section');
      }
      break;
    }
  }

  if (!textBlockFound) {
    console.log('❌ "文本块" label not found');

    // Debug: print all labels
    console.log('   All labels on page:');
    for (let i = 0; i < labelCount && i < 20; i++) {
      const text = await allLabels.nth(i).textContent();
      const visible = await allLabels.nth(i).isVisible();
      console.log(`   [${i}] "${text}" visible=${visible}`);
    }
  }

  await page.screenshot({ path: '/tmp/test-04-final.png' });
  console.log('\nFinal screenshot: /tmp/test-04-final.png');
  await browser.close();
}

main().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
