import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    // Use BACKEND_URL env var if set, otherwise default to localhost
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
    
    return [
      {
        source: '/api/:path*',
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
