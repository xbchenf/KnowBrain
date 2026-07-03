# ---- 第一阶段：Node 构建 ----
FROM node:20-alpine AS builder

WORKDIR /build
COPY package.json ./
RUN npm install --registry=https://registry.npmmirror.com

COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# ---- 第二阶段：Nginx 静态服务 ----
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
