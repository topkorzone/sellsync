'use client';

import { useState } from 'react';
import { X, HelpCircle, Book, MessageCircle, ExternalLink } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import Link from 'next/link';

interface HelpItem {
  id: string;
  category: string;
  question: string;
  answer: string;
  link?: string;
}

const helpItems: HelpItem[] = [
  {
    id: '1',
    category: 'API 연동',
    question: '네이버 커머스 API 키는 어디서 확인하나요?',
    answer: '네이버 커머스 API 센터(apicenter.commerce.naver.com)에서 통합매니저 계정으로 로그인하면 확인할 수 있습니다.',
    link: 'https://apicenter.commerce.naver.com',
  },
  {
    id: '2',
    category: 'API 연동',
    question: '쿠팡 API 키의 유효기간이 만료되면 어떻게 하나요?',
    answer: '쿠팡 Wing에서 새로운 API 키를 재발급받은 후, SellSync의 스토어 관리에서 [API 인증] 버튼을 클릭하여 새 키를 입력하세요.',
  },
  {
    id: '3',
    category: 'ERP 연동',
    question: 'ERP에서 거래처코드, 창고코드가 어디 있나요?',
    answer: 'Ecount ERP > 기초정보관리 > 거래처등록에서 거래처코드를, 기초정보관리 > 창고등록에서 창고코드를 확인할 수 있습니다.',
  },
  {
    id: '4',
    category: '스토어 관리',
    question: '스토어 상태가 "비활성"으로 표시됩니다',
    answer: 'API 인증 정보가 올바른지 확인하세요. 네이버 커머스 API의 경우 인증 기한이 만료되어 휴면 처리되었을 수 있습니다.',
  },
  {
    id: '5',
    category: '전표 생성',
    question: '주문은 수집되었는데 전표가 생성되지 않습니다',
    answer: '해당 주문에 포함된 상품 중 매핑되지 않은 상품이 있을 수 있습니다. 상품 매핑 메뉴에서 미매핑 상품을 확인하고 매핑을 완료해주세요.',
  },
  {
    id: '6',
    category: '동기화',
    question: '동기화가 실패했다는 메시지가 나옵니다',
    answer: 'ERP 또는 마켓플레이스 API 연결 상태를 확인하세요. API 키가 만료되었거나 일시적인 서버 오류일 수 있습니다.',
  },
];

interface HelpPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function HelpPanel({ open, onOpenChange }: HelpPanelProps) {
  const [searchQuery, setSearchQuery] = useState('');

  const filteredItems = helpItems.filter(
    (item) =>
      item.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.answer.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const categories = Array.from(new Set(helpItems.map((item) => item.category)));

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-lg">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <HelpCircle className="h-5 w-5" />
            도움말
          </SheetTitle>
        </SheetHeader>

        <div className="mt-6 space-y-4">
          {/* 검색 */}
          <div>
            <Input
              placeholder="질문을 검색하세요..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full"
            />
          </div>

          {/* 빠른 링크 */}
          <div className="grid grid-cols-2 gap-2">
            <Link href="/help/guide">
              <Button variant="outline" className="w-full justify-start" size="sm">
                <Book className="h-4 w-4 mr-2" />
                사용자 가이드
              </Button>
            </Link>
            <a href="mailto:support@sell-sync.biz">
              <Button variant="outline" className="w-full justify-start" size="sm">
                <MessageCircle className="h-4 w-4 mr-2" />
                문의하기
              </Button>
            </a>
          </div>

          <Separator />

          {/* FAQ 목록 */}
          <ScrollArea className="h-[calc(100vh-280px)]">
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-gray-900">자주 묻는 질문</h3>
              
              {categories.map((category) => {
                const categoryItems = filteredItems.filter(
                  (item) => item.category === category
                );
                
                if (categoryItems.length === 0) return null;

                return (
                  <div key={category} className="space-y-2">
                    <h4 className="text-xs font-medium text-gray-500 uppercase">
                      {category}
                    </h4>
                    {categoryItems.map((item) => (
                      <div
                        key={item.id}
                        className="p-3 rounded-lg bg-gray-50 hover:bg-gray-100 transition-colors"
                      >
                        <div className="flex items-start gap-2">
                          <HelpCircle className="h-4 w-4 text-blue-600 mt-0.5 flex-shrink-0" />
                          <div className="space-y-1 flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-900">
                              {item.question}
                            </p>
                            <p className="text-xs text-gray-600">{item.answer}</p>
                            {item.link && (
                              <a
                                href={item.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 mt-1"
                              >
                                자세히 보기
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })}

              {filteredItems.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                  <p className="text-sm">검색 결과가 없습니다</p>
                </div>
              )}
            </div>
          </ScrollArea>

          {/* 하단 문의 */}
          <div className="pt-4 border-t">
            <p className="text-xs text-gray-600 mb-2">
              원하는 답변을 찾지 못하셨나요?
            </p>
            <a href="mailto:support@sell-sync.biz">
              <Button variant="outline" className="w-full" size="sm">
                <MessageCircle className="h-4 w-4 mr-2" />
                support@sell-sync.biz로 문의하기
              </Button>
            </a>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
