'use client';

import { useState } from 'react';
import { Search, HelpCircle, ExternalLink, Mail } from 'lucide-react';
import { PageHeader } from '@/components/layout';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

interface FAQ {
  id: string;
  category: string;
  question: string;
  answer: string;
  link?: string;
  linkText?: string;
}

const faqs: FAQ[] = [
  {
    id: '1',
    category: 'API 연동',
    question: '네이버 커머스 API 키는 어디서 확인하나요?',
    answer: '네이버 커머스 API 센터(apicenter.commerce.naver.com)에서 통합매니저 계정으로 로그인하면 확인할 수 있습니다. 또한 스마트스토어센터 > 스토어관리 > 판매자 정보에서도 확인 가능합니다.',
    link: 'https://apicenter.commerce.naver.com',
    linkText: '네이버 커머스 API 센터 바로가기',
  },
  {
    id: '2',
    category: 'API 연동',
    question: '쿠팡 API 키의 유효기간이 만료되면 어떻게 하나요?',
    answer: '쿠팡 Wing에서 새로운 API 키를 재발급받은 후, SellSync의 스토어 관리에서 [API 인증] 버튼을 클릭하여 새 키를 입력하세요. 유효기간 만료 2주 전과 1주 전에 이메일 안내가 발송됩니다.',
    link: 'https://wing.coupang.com',
    linkText: '쿠팡 Wing 바로가기',
  },
  {
    id: '3',
    category: 'API 연동',
    question: 'Ecount ERP API 인증키는 어떻게 발급받나요?',
    answer: 'Ecount ERP 로그인 후, Self-Customizing > 정보관리 > API 인증키관리로 이동하여 API 인증키를 발급받을 수 있습니다. 발급된 키는 안전하게 보관하세요.',
  },
  {
    id: '4',
    category: 'ERP 연동',
    question: 'ERP에서 거래처코드, 창고코드가 어디 있나요?',
    answer: 'Ecount ERP > 기초정보관리 > 거래처등록에서 거래처코드를, 기초정보관리 > 창고등록에서 창고코드를 확인할 수 있습니다.',
  },
  {
    id: '5',
    category: '스토어 관리',
    question: '스토어 상태가 "비활성"으로 표시됩니다',
    answer: 'API 인증 정보가 올바른지 확인하세요. 네이버 커머스 API의 경우 인증 기한이 만료되어 휴면 처리되었을 수 있습니다. 커머스 API 센터에서 인증을 다시 진행하세요.',
  },
  {
    id: '6',
    category: '스토어 관리',
    question: '여러 개의 스토어를 동시에 연동할 수 있나요?',
    answer: '네, 가능합니다. 네이버 스마트스토어, 쿠팡 등 여러 마켓플레이스를 동시에 연동하여 사용할 수 있습니다.',
  },
  {
    id: '7',
    category: '주문 동기화',
    question: '주문은 얼마나 자주 동기화되나요?',
    answer: 'SellSync는 30분마다 자동으로 새 주문을 수집합니다. 수동으로 즉시 동기화하려면 "동기화" 메뉴에서 [전체 주문 동기화] 버튼을 클릭하세요.',
  },
  {
    id: '8',
    category: '전표 생성',
    question: '주문은 수집되었는데 전표가 생성되지 않습니다',
    answer: '해당 주문에 포함된 상품 중 매핑되지 않은 상품이 있을 수 있습니다. 상품 매핑 메뉴에서 미매핑 상품을 확인하고 매핑을 완료해주세요. 매핑이 완료되면 자동으로 전표가 생성됩니다.',
  },
  {
    id: '9',
    category: '전표 생성',
    question: '전표 생성에 실패했습니다. 어떻게 해야 하나요?',
    answer: '전표 목록에서 실패한 전표를 확인하고, 오류 메시지를 참고하여 문제를 해결한 후 재시도 버튼을 클릭하세요. 계속 실패하는 경우 고객센터로 문의해주세요.',
  },
  {
    id: '10',
    category: '상품 매핑',
    question: '상품 매핑이 무엇인가요?',
    answer: '쇼핑몰 상품과 ERP 품목을 연결하는 과정입니다. 매핑이 완료되어야 해당 상품이 포함된 주문의 전표를 생성할 수 있습니다.',
  },
  {
    id: '11',
    category: '상품 매핑',
    question: 'AI 추천 매핑이 정확하지 않습니다',
    answer: 'AI 추천은 상품명을 기반으로 제안됩니다. 정확하지 않은 경우 직접 검색하여 올바른 ERP 품목을 선택하세요. 한 번 매핑하면 이후 동일 상품은 자동으로 매핑됩니다.',
  },
  {
    id: '12',
    category: '동기화',
    question: '동기화가 실패했다는 메시지가 나옵니다',
    answer: 'ERP 또는 마켓플레이스 API 연결 상태를 확인하세요. API 키가 만료되었거나 일시적인 서버 오류일 수 있습니다. 잠시 후 다시 시도하거나 API 인증 정보를 확인해주세요.',
  },
  {
    id: '13',
    category: '동기화',
    question: 'ERP 품목 동기화는 언제 해야 하나요?',
    answer: '처음 시스템을 설정할 때 한 번, 그리고 ERP에 새로운 품목을 추가한 경우 동기화를 다시 실행하세요. 정기적으로 동기화하면 최신 품목 정보를 유지할 수 있습니다.',
  },
  {
    id: '14',
    category: '송장 처리',
    question: '송장 번호는 자동으로 반영되나요?',
    answer: '네, ERP에서 발행된 송장 번호를 자동으로 수집하여 각 마켓플레이스에 전송합니다. 전송 실패 시 알림을 받으실 수 있습니다.',
  },
  {
    id: '15',
    category: '기타',
    question: '사용 중 문제가 발생했어요',
    answer: '고객센터(support@sell-sync.biz)로 문의해주세요. 오류 메시지 스크린샷과 함께 상황을 설명해주시면 빠르게 도와드리겠습니다.',
  },
];

export default function FAQPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  const categories = Array.from(new Set(faqs.map((faq) => faq.category)));

  const filteredFaqs = faqs.filter((faq) => {
    const matchesSearch =
      faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.answer.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = !selectedCategory || faq.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="자주 묻는 질문"
        description="SellSync 사용 중 궁금한 점을 빠르게 찾아보세요"
      />

      {/* 검색 및 필터 */}
      <Card>
        <CardContent className="pt-6 space-y-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <Input
              placeholder="질문을 검색하세요..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <Badge
              variant={selectedCategory === null ? 'default' : 'outline'}
              className="cursor-pointer"
              onClick={() => setSelectedCategory(null)}
            >
              전체
            </Badge>
            {categories.map((category) => (
              <Badge
                key={category}
                variant={selectedCategory === category ? 'default' : 'outline'}
                className="cursor-pointer"
                onClick={() => setSelectedCategory(category)}
              >
                {category}
              </Badge>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* FAQ 목록 */}
      <div className="space-y-4">
        {filteredFaqs.length === 0 ? (
          <Card>
            <CardContent className="py-12 text-center">
              <HelpCircle className="h-12 w-12 text-gray-300 mx-auto mb-4" />
              <p className="text-gray-500">검색 결과가 없습니다</p>
            </CardContent>
          </Card>
        ) : (
          filteredFaqs.map((faq) => (
            <Card key={faq.id} className="hover:shadow-md transition-shadow">
              <CardContent className="pt-6">
                <div className="space-y-3">
                  <div className="flex items-start gap-3">
                    <HelpCircle className="h-5 w-5 text-blue-600 mt-0.5 flex-shrink-0" />
                    <div className="flex-1 space-y-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-semibold text-gray-900">{faq.question}</h3>
                        <Badge variant="outline" className="text-xs">
                          {faq.category}
                        </Badge>
                      </div>
                      <p className="text-sm text-gray-600 leading-relaxed">{faq.answer}</p>
                      {faq.link && (
                        <a
                          href={faq.link}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-700"
                        >
                          {faq.linkText || '자세히 보기'}
                          <ExternalLink className="h-3 w-3" />
                        </a>
                      )}
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* 하단 문의 */}
      <Card className="border-blue-200 bg-blue-50">
        <CardContent className="py-6">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <Mail className="h-8 w-8 text-blue-600" />
              <div>
                <h3 className="font-semibold text-gray-900">원하는 답변을 찾지 못하셨나요?</h3>
                <p className="text-sm text-gray-600">언제든지 문의해주세요. 빠르게 도와드리겠습니다.</p>
              </div>
            </div>
            <a href="mailto:support@sell-sync.biz">
              <Button>
                <Mail className="h-4 w-4 mr-2" />
                문의하기
              </Button>
            </a>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
