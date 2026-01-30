import Link from 'next/link';
import { ArrowRight, Zap, RefreshCw, Shield, BarChart3 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '오픈마켓 주문관리 자동화 - 스마트스토어, 쿠팡 ERP 연동',
  description:
    '스마트스토어, 쿠팡 주문 수집부터 이카운트 ERP 전표 자동 생성, 정산 자동화, 송장 자동 반영까지. SellSync로 오픈마켓 판매 관리를 자동화하세요. 14일 무료 체험.',
  keywords: [
    '오픈마켓 자동화',
    '스마트스토어 ERP',
    '쿠팡 ERP 연동',
    '이카운트 자동 전표',
    '주문 자동 수집',
    '송장 자동 반영',
    '쇼핑몰 주문관리',
    '오픈마켓 통합관리',
  ],
  alternates: {
    canonical: 'https://sell-sync.biz',
  },
  openGraph: {
    title: 'SellSync - 오픈마켓 주문관리 자동화 솔루션',
    description:
      '스마트스토어, 쿠팡 주문 수집부터 ERP 전표 생성, 송장 반영까지 모든 과정을 자동화합니다.',
    url: 'https://sell-sync.biz',
  },
};

const FEATURES = [
  {
    icon: Zap,
    title: '자동 주문 수집',
    description:
      '스마트스토어, 쿠팡 주문을 30분 주기로 자동 수집합니다. 수작업 없이 모든 오픈마켓 주문을 한곳에서 관리하세요.',
  },
  {
    icon: RefreshCw,
    title: 'ERP 자동 연동',
    description:
      '이카운트 ERP에 매출전표를 자동으로 생성합니다. 주문 데이터가 실시간으로 ERP에 반영됩니다.',
  },
  {
    icon: Shield,
    title: '정산 자동화',
    description:
      '마켓별 수수료를 자동 계산하고 정산 전표를 생성합니다. 복잡한 수수료 정산을 자동으로 처리합니다.',
  },
  {
    icon: BarChart3,
    title: '송장 자동 반영',
    description:
      '송장번호를 업로드하면 스마트스토어, 쿠팡에 자동으로 반영됩니다. 배송 처리 시간을 절약하세요.',
  },
];

const PRICING = [
  { range: '0 ~ 1,000건', price: '49,000원' },
  { range: '1,001 ~ 5,000건', price: '99,000원' },
  { range: '5,001 ~ 15,000건', price: '199,000원' },
  { range: '15,001 ~ 30,000건', price: '349,000원' },
];

export default function LandingPage() {
  const organizationJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'SellSync',
    url: 'https://sell-sync.biz',
    logo: 'https://sell-sync.biz/opengraph-image',
    description:
      '오픈마켓 판매자를 위한 ERP 자동화 솔루션. 스마트스토어, 쿠팡 주문관리를 자동화합니다.',
  };

  const softwareJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'SellSync',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    url: 'https://sell-sync.biz',
    description:
      '스마트스토어, 쿠팡 주문 수집부터 이카운트 ERP 전표 생성, 송장 자동 반영까지 오픈마켓 판매 관리를 자동화하는 SaaS 솔루션',
    offers: {
      '@type': 'AggregateOffer',
      priceCurrency: 'KRW',
      lowPrice: '49000',
      highPrice: '349000',
      offerCount: 4,
    },
  };

  return (
    <main className="min-h-screen bg-white">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(organizationJsonLd),
        }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(softwareJsonLd),
        }}
      />

      {/* Header */}
      <header className="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-md border-b border-gray-100">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-gray-900 flex items-center justify-center">
                <span className="text-white font-bold">S</span>
              </div>
              <span className="text-xl font-bold text-gray-900">SellSync</span>
            </div>
            <nav className="flex items-center gap-3">
              <Link href="/pricing">
                <Button variant="ghost" size="sm">
                  요금제
                </Button>
              </Link>
              <Link href="/login">
                <Button variant="ghost" size="sm">
                  로그인
                </Button>
              </Link>
              <Link href="/register">
                <Button size="sm" className="bg-gray-900 hover:bg-gray-800">
                  무료로 시작하기
                </Button>
              </Link>
            </nav>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="pt-32 pb-20 px-4 sm:px-6 lg:px-8">
        <div className="max-w-4xl mx-auto text-center">
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-900 leading-tight">
            오픈마켓 판매,
            <br />
            <span className="text-blue-600">자동화</span>로 더 쉽게
          </h1>
          <p className="mt-6 text-lg sm:text-xl text-gray-600 max-w-2xl mx-auto">
            스마트스토어, 쿠팡 주문 수집부터 ERP 전표 생성, 송장 반영까지
            <br />
            모든 과정을 자동화하여 운영 비용을 절감하세요.
          </p>
          <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link href="/register">
              <Button
                size="lg"
                className="bg-gray-900 hover:bg-gray-800 text-base px-8 h-12"
              >
                무료로 시작하기
                <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
            </Link>
            <Link href="#features">
              <Button variant="outline" size="lg" className="text-base px-8 h-12">
                기능 살펴보기
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-20 bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900">
              반복 작업은 SellSync에 맡기세요
            </h2>
            <p className="mt-4 text-lg text-gray-600">
              오픈마켓 주문 관리의 모든 과정을 자동화합니다
            </p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
            {FEATURES.map((feature, index) => (
              <div
                key={index}
                className="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 hover:shadow-md transition-shadow"
              >
                <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center mb-4">
                  <feature.icon className="h-6 w-6 text-blue-600" />
                </div>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">
                  {feature.title}
                </h3>
                <p className="text-gray-600 text-sm">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it Works Section */}
      <section className="py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900">
              간단한 3단계로 시작하세요
            </h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[
              {
                step: '01',
                title: '스토어 연동',
                desc: '스마트스토어, 쿠팡 API 키를 등록하세요.',
              },
              {
                step: '02',
                title: 'ERP 연동',
                desc: '이카운트 인증 정보를 입력하세요.',
              },
              {
                step: '03',
                title: '자동화 시작',
                desc: '나머지는 SellSync가 알아서 처리합니다.',
              },
            ].map((item, index) => (
              <div key={index} className="text-center">
                <div className="w-16 h-16 rounded-full bg-gray-900 text-white text-2xl font-bold flex items-center justify-center mx-auto mb-4">
                  {item.step}
                </div>
                <h3 className="text-xl font-semibold text-gray-900 mb-2">
                  {item.title}
                </h3>
                <p className="text-gray-600">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Pricing Section */}
      <section id="pricing" className="py-20 bg-gray-50">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900">
              합리적인 요금제
            </h2>
            <p className="mt-4 text-lg text-gray-600">
              월 주문 건수에 따라 선택하세요
            </p>
          </div>
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
            <div className="divide-y divide-gray-100">
              {PRICING.map((plan, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-6 hover:bg-gray-50 transition-colors"
                >
                  <span className="text-gray-900 font-medium">{plan.range}</span>
                  <span className="text-2xl font-bold text-gray-900">
                    {plan.price}
                    <span className="text-sm font-normal text-gray-500">/월</span>
                  </span>
                </div>
              ))}
            </div>
          </div>
          <div className="mt-8 text-center">
            <p className="text-gray-600 mb-6">
              모든 요금제에 포함: 주문 수집 / ERP 전표 생성 / 정산 자동화 / 송장
              반영
            </p>
            <Link href="/register">
              <Button size="lg" className="bg-gray-900 hover:bg-gray-800">
                14일 무료 체험 시작하기
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-6">
            지금 바로 시작하세요
          </h2>
          <p className="text-lg text-gray-600 mb-8">
            14일 무료 체험으로 SellSync의 모든 기능을 경험해보세요.
            <br />
            신용카드 없이 바로 시작할 수 있습니다.
          </p>
          <Link href="/register">
            <Button
              size="lg"
              className="bg-blue-600 hover:bg-blue-700 text-base px-10 h-12"
            >
              무료로 시작하기
              <ArrowRight className="ml-2 h-5 w-5" />
            </Button>
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-12 border-t border-gray-100">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded bg-gray-900 flex items-center justify-center">
                <span className="text-white font-bold text-xs">S</span>
              </div>
              <span className="text-sm text-gray-600">
                © 2026 SellSync. All rights reserved.
              </span>
            </div>
            <nav className="flex items-center gap-6 text-sm text-gray-600">
              <Link
                href="/pricing"
                className="hover:text-gray-900 transition-colors"
              >
                요금제
              </Link>
              <Link
                href="/register"
                className="hover:text-gray-900 transition-colors"
              >
                회원가입
              </Link>
              <Link
                href="/login"
                className="hover:text-gray-900 transition-colors"
              >
                로그인
              </Link>
              <a href="#" className="hover:text-gray-900 transition-colors">
                이용약관
              </a>
              <a href="#" className="hover:text-gray-900 transition-colors">
                개인정보처리방침
              </a>
            </nav>
          </div>
        </div>
      </footer>
    </main>
  );
}
