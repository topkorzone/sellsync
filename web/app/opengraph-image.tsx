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
          background: 'linear-gradient(135deg, #111827 0%, #1f2937 50%, #111827 100%)',
          fontFamily: 'sans-serif',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            marginBottom: '32px',
          }}
        >
          <div
            style={{
              width: '64px',
              height: '64px',
              borderRadius: '16px',
              background: '#ffffff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '36px',
              fontWeight: 'bold',
              color: '#111827',
            }}
          >
            S
          </div>
          <span
            style={{
              fontSize: '48px',
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
