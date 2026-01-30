import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import Script from 'next/script';
import './globals.css';

const inter = Inter({ subsets: ['latin'] });

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 5,
};

export const metadata: Metadata = {
  metadataBase: new URL('https://sell-sync.biz'),
  title: {
    default: 'SellSync - 오픈마켓 ERP 자동화 솔루션',
    template: '%s | SellSync',
  },
  description:
    '스마트스토어, 쿠팡 주문 수집부터 이카운트 ERP 전표 생성, 송장 자동 반영까지. 오픈마켓 판매 관리를 자동화하세요.',
  keywords: [
    '오픈마켓 ERP',
    '스마트스토어 자동화',
    '쿠팡 주문관리',
    '이카운트 연동',
    'ERP 자동화',
    '전표 자동 생성',
    '송장 자동 반영',
    '쇼핑몰 통합관리',
    '셀싱크',
    'SellSync',
  ],
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-video-preview': -1,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },
  openGraph: {
    type: 'website',
    locale: 'ko_KR',
    siteName: 'SellSync',
    title: 'SellSync - 오픈마켓 ERP 자동화 솔루션',
    description:
      '스마트스토어, 쿠팡 주문 수집부터 이카운트 ERP 전표 생성, 송장 자동 반영까지. 오픈마켓 판매 관리를 자동화하세요.',
    url: 'https://sell-sync.biz',
    images: [
      {
        url: '/opengraph-image',
        width: 1200,
        height: 630,
        alt: 'SellSync - 오픈마켓 ERP 자동화 솔루션',
      },
    ],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'SellSync - 오픈마켓 ERP 자동화 솔루션',
    description:
      '스마트스토어, 쿠팡 주문 수집부터 이카운트 ERP 전표 생성, 송장 자동 반영까지.',
    images: ['/opengraph-image'],
  },
  alternates: {
    canonical: 'https://sell-sync.biz',
  },
  // verification: {
  //   google: 'Google Search Console 인증 코드',
  //   other: { 'naver-site-verification': '네이버 웹마스터 인증 코드' },
  // },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body className={inter.className} suppressHydrationWarning>
        {children}
        <Script
          src="https://js.tosspayments.com/v2/standard"
          strategy="afterInteractive"
        />
      </body>
    </html>
  );
}
