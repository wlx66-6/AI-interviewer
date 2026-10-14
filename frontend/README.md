# AI Interviewer · 前端

AI 模拟面试与简历分析平台的 Web 前端。

## 技术栈

- React 18 + TypeScript + Vite
- TailwindCSS 4
- 图表：recharts / react-big-calendar
- 图标：lucide-react（通用）/ react-icons（技术栈品牌图标）
- 语音面试：WebSocket + 浏览器端 VAD（onnxruntime-web）

## 开发

```bash
pnpm install
pnpm run dev        # 启动开发服务器（默认 5173）
```

后端默认运行在 `http://localhost:8080`，开发时通过 Vite 代理转发 `/api` 与 `/ws`。

## 构建与检查

```bash
pnpm run build        # tsc 类型检查 + vite build
pnpm run lint         # ESLint
pnpm run format       # Prettier 格式化
pnpm run format:check # 仅校验格式
```

## 测试

```bash
pnpm run test:unit    # node:test 单元测试
pnpm run test:e2e     # Playwright 端到端测试（需先启动前后端）
```

## 目录结构

```
src/
├── api/          # 后端接口封装，复用 request.ts 的 Axios 实例
├── components/   # 可复用 UI 组件
├── constants/    # 路由等常量
├── hooks/        # 自定义 Hook
├── pages/        # 页面级组件
├── types/        # 共享类型定义
└── utils/        # 工具函数
```