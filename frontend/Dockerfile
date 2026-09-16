# ==============================================================================
# ResolveAI Frontend — Multi-Stage Production Dockerfile (Vite / React / Nginx)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Stage
# ------------------------------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /app

# Copy dependency manifests
COPY package.json package-lock.json ./

# Clean install dependencies
RUN npm ci

# Copy source code and config files
COPY . .

# Build production assets (/app/dist)
# VITE_API_BASE_URL defaults to /api so browser requests route cleanly through Nginx reverse proxy
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Production Nginx Server
# ------------------------------------------------------------------------------
FROM nginx:alpine AS runner

# Install curl for reliable container health check
RUN apk add --no-cache curl

# Remove default nginx static assets and configuration
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/resolveai.conf

# Copy compiled SPA static assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

# Health check against /healthz
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD curl -f http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
