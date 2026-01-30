import { ImageResponse } from 'next/og';

export const runtime = 'edge';
export const size = { width: 180, height: 180 };
export const contentType = 'image/png';

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, #3B82F6 0%, #2563EB 100%)',
          borderRadius: '40px',
        }}
      >
        <svg
          width="120"
          height="120"
          viewBox="0 0 512 512"
          fill="none"
        >
          <path
            d="M160 208C160 163.817 195.817 128 240 128H288"
            stroke="white"
            strokeWidth="40"
            strokeLinecap="round"
          />
          <path
            d="M256 88L320 128L256 168"
            stroke="white"
            strokeWidth="40"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M352 304C352 348.183 316.183 384 272 384H224"
            stroke="white"
            strokeWidth="40"
            strokeLinecap="round"
          />
          <path
            d="M256 344L192 384L256 424"
            stroke="white"
            strokeWidth="40"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
    ),
    { ...size },
  );
}
