# 构建阶段：安装依赖并执行 vite build
FROM node:20-alpine AS builder
WORKDIR /app

# 先复制依赖清单，命中 Docker 层缓存以跳过重复安装
# 容器不继承宿主机的 ~/.npmrc，用 npmmirror 保证国内构建环境可稳定拉包（海外同样可用）
COPY package.json pnpm-lock.yaml ./
RUN npm config set registry https://registry.npmmirror.com \
    && npm install -g pnpm \
    && pnpm config set registry https://registry.npmmirror.com \
    && pnpm install

COPY . .
RUN pnpm build

# 运行阶段：用 Nginx 托管静态产物并反向代理 API
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]