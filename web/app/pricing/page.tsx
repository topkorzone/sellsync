import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { PricingPlans } from '@/components/pricing/PricingPlans';
import { ArrowLeft } from 'lucide-react';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '요금제 안내 - 오픈마켓 ERP 자동화 가격',
  description:
    'SellSync 요금제를 확인하세요. 월 주문 건수에 따라 49,000원부터 시작. 주문 수집, ERP 전표 생성, 정산 자동화, 송장 반영 모두 포함. 14일 무료 체험.',
  keywords: [
    'SellSync 요금제',
    '오픈마켓 ERP 가격',
    '이커머스 자동화 비용',
    'ERP 연동 요금',
    '스마트스토어 자동화 가격',
  ],
  alternates: {
    canonical: 'https://sell-sync.biz/pricing',
  },
  openGraph: {
    title: 'SellSync 요금제 - 오픈마켓 ERP 자동화',
    description:
      '월 주문 건수에 따라 합리적인 가격으로 오픈마켓 판매 관리를 자동화하세요. 14일 무료 체험.',
    url: 'https://sell-sync.biz/pricing',
  },
};

const FAQ_ITEMS = [
  {
    question: '무료 체험은 어떻게 시작하나요?',
    answer:
      '회원가입 후 자동으로 14일 무료 체험이 시작됩니다. 체험 기간 동안 전표 50건을 생성할 수 있습니다.',
  },
  {
    question: '플랜 변경은 언제든 가능한가요?',
    answer:
      '네, 언제든 상위 플랜으로 업그레이드할 수 있습니다. 업그레이드는 즉시 적용됩니다.',
  },
  {
    question: '해지하면 어떻게 되나요?',
    answer:
      '현재 구독 기간이 종료될 때까지는 서비스를 계속 이용할 수 있으며, 이후 자동결제가 중단됩니다.',
  },
];

export default function PricingPage() {
  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: FAQ_ITEMS.map((item) => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: {
        '@type': 'Answer',
        text: item.answer,
      },
    })),
  };

  return (
    <main className="min-h-screen bg-gray-50">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(faqJsonLd),
        }}
      />

      {/* Header */}
      <header className="border-b bg-white">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link href="/">
              <Button variant="ghost" size="sm">
                <ArrowLeft className="h-4 w-4 mr-1" />
                돌아가기
              </Button>
            </Link>
          </div>
          <Link href="/login">
            <Button variant="outline">로그인</Button>
          </Link>
        </div>
      </header>

      {/* Content */}
      <div className="max-w-6xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold tracking-tight">요금제 안내</h1>
          <p className="mt-3 text-lg text-muted-foreground">
            비즈니스 규모에 맞는 플랜을 선택하세요
          </p>
        </div>

        <PricingPlans />

        {/* FAQ */}
        <section className="mt-16 max-w-2xl mx-auto">
          <h2 className="text-xl font-semibold text-center mb-6">
            자주 묻는 질문
          </h2>
          <div className="space-y-4">
            {FAQ_ITEMS.map((item, index) => (
              <div key={index} className="bg-white rounded-lg p-4 border">
                <h3 className="font-medium">{item.question}</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  {item.answer}
                </p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
