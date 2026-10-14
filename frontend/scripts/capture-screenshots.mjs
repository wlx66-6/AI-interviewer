/**
 * 用真实浏览器给 README 拍「效果展示」截图。
 *
 * 和 e2e 的区别：e2e 用例会用 page.route 把接口 mock 掉，拍出来的界面没有真实数据；
 * 这里刻意不 mock，直接连一个跑起来的实例（默认 http://localhost:8000），
 * 所以需要先 docker compose up 起全栈，并造好演示数据。
 *
 * 用法：
 *   pnpm run screenshots                       # 默认连 http://localhost:8000
 *   CAPTURE_BASE_URL=http://localhost:5173 pnpm run screenshots
 *
 * 产物写到仓库根目录的 screenshots/（该目录会被提交，README 直接引用）。
 */
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const BASE_URL = process.env.CAPTURE_BASE_URL ?? 'http://localhost:8000';
const OUT_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../../screenshots');

/**
 * 详情页需要具体记录 ID。默认值对应仓库里演示数据那一套，
 * 换一套数据时用环境变量覆盖，例如 CAPTURE_RESUME_ID=7。
 */
const RESUME_ID = process.env.CAPTURE_RESUME_ID ?? '3';
const INTERVIEW_SESSION_ID = process.env.CAPTURE_INTERVIEW_SESSION_ID ?? '886a6c19707a4e73';
const KNOWLEDGE_BASE_ID = process.env.CAPTURE_KB_ID ?? '1';

const PAGES = [
  { name: '01-resume-list', path: '/history' },
  { name: '02-resume-detail', path: `/history/${RESUME_ID}` },
  { name: '03-interview-hub', path: '/interview-hub' },
  { name: '04-interview-report', path: `/interviews/${INTERVIEW_SESSION_ID}` },
  { name: '05-knowledge-base', path: '/knowledgebase' },
  {
    name: '06-knowledge-base-questions',
    path: `/knowledgebase-interview/${KNOWLEDGE_BASE_ID}/questions`,
  },
  {
    name: '07-knowledge-base-chat',
    path: '/knowledgebase/chat',
    // 问答助手默认是空态（提示「请先选择知识库」），列表里点一条历史会话才有内容可看。
    // 点的是列表项的正文，事件冒泡到父级 onClick，和真人操作一致。
    action: async (page) => {
      await page
        .getByText(/\d+ 条消息/)
        .first()
        .click();
    },
  },
  { name: '08-token-usage', path: '/token-usage' },
  { name: '09-rag-evaluation', path: '/rag-eval' },
];

/** 页面上出现这些文字，基本可以认为数据已经渲染完了。 */
const READY_TEXT = [
  [/resume-list|resume-detail/, /简历|Java/],
  [/interview-report/, /分|得分|报告/],
  [/knowledge-base$/, /Java后端面试核心笔记/],
  [/knowledge-base-questions/, /题|Redis|JVM/],
  [/knowledge-base-chat/, /Redis|B\+ 树|缓存/],
  [/token-usage/, /Token|模型/],
  [/rag-evaluation/, /Hit@|MRR|chunk|评估/],
];

/**
 * 优先用 Playwright 自带的 chromium；没装（或下载不下来）时退回系统浏览器。
 * Windows 11 自带 Edge，用它省掉几百 MB 的浏览器下载。
 * 也可用 CAPTURE_CHANNEL=chrome|msedge|chromium 显式指定。
 */
async function launchBrowser() {
  const channel = process.env.CAPTURE_CHANNEL;
  if (channel) {
    return chromium.launch({ channel });
  }
  try {
    return await chromium.launch();
  } catch {
    process.stdout.write('未找到 Playwright 自带浏览器，回退到系统 Edge\n');
    return chromium.launch({ channel: 'msedge' });
  }
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });
  const browser = await launchBrowser();
  // 视口尺寸而不是整页截图：内容长的页面（报告、评测）整页截出来是 1 万多像素高、
  // 七八 MB，塞进 README 会严重拖慢加载。1600x1100 的首屏足够说明界面。
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1100 },
    deviceScaleFactor: 1,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  });
  const page = await context.newPage();

  for (const { name, path, action } of PAGES) {
    const url = BASE_URL + path;
    process.stdout.write(`→ ${name}  ${url}\n`);
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60_000 });

    const hint = READY_TEXT.find(([pattern]) => pattern.test(name))?.[1];
    if (hint) {
      try {
        await page.getByText(hint).first().waitFor({ timeout: 20_000 });
      } catch {
        // 文案可能变了，不因为等待超时就放弃这张图，交给下面的固定停顿兜底
        process.stdout.write('    (未等到预期文案，仍继续截图)\n');
      }
    }
    // 少数页面需要先做一步交互（例如点开会话）才不是空态
    if (action) {
      await action(page);
    }

    // 等 recharts 的入场动画和懒加载的图片收尾
    await page.waitForTimeout(2000);

    await page.screenshot({ path: `${OUT_DIR}/${name}.png` });
  }

  await browser.close();
  process.stdout.write(`\n完成，截图已写入 ${OUT_DIR}\n`);
}

main().catch((error) => {
  console.error('截图失败：', error);
  process.exit(1);
});
