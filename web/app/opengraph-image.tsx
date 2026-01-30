import { ImageResponse } from 'next/og';

export const runtime = 'edge';

export const alt = 'SellSync - 오픈마켓 ERP 자동화 솔루션';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

export default async function Image() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
          fontFamily: 'sans-serif',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '20px',
            marginBottom: '40px',
          }}
        >
          {/* Logo symbol - gradient rounded square with sync arrows */}
          <svg
            width="72"
            height="72"
            viewBox="0 0 32 32"
            fill="none"
          >
            <rect width="32" height="32" rx="8" fill="url(#og-grad)" />
            <path
              d="M10 13C10 10.239 12.239 8 15 8H18"
              stroke="white"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
            <path
              d="M16 5.5L19 8L16 10.5"
              stroke="white"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d="M22 19C22 21.761 19.761 24 17 24H14"
              stroke="white"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
            <path
              d="M16 21.5L13 24L16 26.5"
              stroke="white"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <defs>
              <linearGradient
                id="og-grad"
                x1="0"
                y1="0"
                x2="32"
                y2="32"
                gradientUnits="userSpaceOnUse"
              >
                <stop stopColor="#3B82F6" />
                <stop offset="1" stopColor="#2563EB" />
              </linearGradient>
            </defs>
          </svg>
          <span
            style={{
              fontSize: '56px',
              fontWeight: 'bold',
              color: '#ffffff',
            }}
          >
            SellSync
          </span>
        </div>
        <div
          style={{
            fontSize: '32px',
            color: '#d1d5db',
            textAlign: 'center',
            lineHeight: 1.5,
          }}
        >
          오픈마켓 ERP 자동화 솔루션
        </div>
        <div
          style={{
            fontSize: '20px',
            color: '#9ca3af',
            textAlign: 'center',
            marginTop: '16px',
            lineHeight: 1.6,
          }}
        >
          스마트스토어 · 쿠팡 주문 수집 → ERP 전표 생성 → 송장 자동 반영
        </div>
      </div>
    ),
    { ...size },
  );
}
