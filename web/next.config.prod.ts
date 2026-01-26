import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone', // Docker 배포를 위한 standalone 모드
  
  // 프로덕션 최적화
  reactStrictMode: true,
  
  // 이미지 최적화
  images: {
    domains: [
      'shopping-phinf.pstatic.net', // 네이버 스마트스토어
      'image.coupangcdn.com', // 쿠팡
    ],
    formats: ['image/avif', 'image/webp'],
  },
  
  // 환경 변수
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
    NEXT_PUBLIC_APP_NAME: 'SellSync',
    NEXT_PUBLIC_APP_VERSION: '1.0.0',
  },
  
  // 헤더 설정
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
        ],
      },
    ];
  },
};

export default nextConfig;
