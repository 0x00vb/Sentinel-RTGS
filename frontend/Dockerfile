# Production stage
FROM node:20-alpine AS production

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy app source
COPY . .

# Build (for production)
RUN npm run build

EXPOSE 3000
CMD ["npm", "start"]

# Development stage
FROM node:20-alpine AS development

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy app source
COPY . .

EXPOSE 3000
CMD ["npm", "run", "dev"]
