'use client';

import Link from 'next/link';
import {
  BookOpen,
  ChevronRight,
  Settings,
  ShoppingBag,
  RefreshCw,
  Link2,
  ExternalLink,
} from 'lucide-react';
import { PageHeader } from '@/components/layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';

export default function GuidePage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="사용자 가이드"
        description="SellSync 시작부터 운영까지 단계별로 안내합니다"
      />

      <Tabs defaultValue="overview" className="space-y-6">
        <TabsList>
          <TabsTrigger value="overview">개요</TabsTrigger>
          <TabsTrigger value="setup">연동 설정</TabsTrigger>
          <TabsTrigger value="sync">주문 동기화</TabsTrigger>
          <TabsTrigger value="mapping">상품 매핑</TabsTrigger>
        </TabsList>

        {/* 개요 */}
        <TabsContent value="overview" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BookOpen className="h-5 w-5" />
                SellSync 소개
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-gray-600 leading-relaxed">
                SellSync는 네이버 스마트스토어, 쿠팡 등 온라인 쇼핑몰의 주문을 Ecount ERP로 자동 연동해주는
                서비스입니다. 복잡한 API 설정 없이 로그인 정보만으로 간편하게 연동할 수 있습니다.
              </p>

              <div className="space-y-3 pt-4">
                <h4 className="font-semibold text-gray-900">주요 기능</h4>
                <ul className="space-y-2">
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <span className="text-gray-600">쇼핑몰 주문 자동 수집 (30분 주기)</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <span className="text-gray-600">ERP 판매전표 자동 생성</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <span className="text-gray-600">운송장 번호 자동 업로드</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <span className="text-gray-600">쇼핑몰 상품과 ERP 품목 간편 매핑</span>
                  </li>
                </ul>
              </div>
            </CardContent>
          </Card>

          {/* 빠른 시작 단계 */}
          <Card>
            <CardHeader>
              <CardTitle>빠른 시작 가이드</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {[
                  {
                    step: 1,
                    icon: Settings,
                    title: 'ERP 연동 설정',
                    description: 'Ecount ERP API 인증키를 등록합니다',
                    link: '#setup-erp',
                  },
                  {
                    step: 2,
                    icon: ShoppingBag,
                    title: '쇼핑몰 연동',
                    description: '네이버 스마트스토어, 쿠팡 등을 연동합니다',
                    link: '#setup-store',
                  },
                  {
                    step: 3,
                    icon: RefreshCw,
                    title: 'ERP 품목 동기화',
                    description: '상품 매핑을 위해 ERP 품목을 가져옵니다',
                    link: '#sync-items',
                  },
                  {
                    step: 4,
                    icon: Link2,
                    title: '상품 매핑',
                    description: '쇼핑몰 상품과 ERP 품목을 연결합니다',
                    link: '#mapping',
                  },
                  {
                    step: 5,
                    icon: RefreshCw,
                    title: '주문 동기화',
                    description: '첫 주문을 수집하고 전표를 생성합니다',
                    link: '#sync-orders',
                  },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <a
                      key={item.step}
                      href={item.link}
                      className="flex items-start gap-4 p-4 rounded-lg border hover:bg-gray-50 transition-colors group"
                    >
                      <div className="flex items-center justify-center w-10 h-10 rounded-full bg-blue-100 text-blue-600 font-bold flex-shrink-0">
                        {item.step}
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <Icon className="h-4 w-4 text-gray-600" />
                          <h4 className="font-semibold text-gray-900">{item.title}</h4>
                        </div>
                        <p className="text-sm text-gray-600">{item.description}</p>
                      </div>
                      <ChevronRight className="h-5 w-5 text-gray-400 group-hover:text-blue-600 mt-2" />
                    </a>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* 연동 설정 */}
        <TabsContent value="setup" className="space-y-6">
          <Card id="setup-erp">
            <CardHeader>
              <CardTitle>1. Ecount ERP 연동</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <h4 className="font-semibold text-yellow-900 mb-2">사전 준비: API 인증키 발급</h4>
                <p className="text-sm text-yellow-800">
                  Ecount ERP에서 API 인증키를 먼저 발급받아야 합니다.
                </p>
              </div>

              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">발급 절차</h4>
                <ol className="space-y-2 list-decimal list-inside text-gray-600">
                  <li>Ecount ERP 로그인</li>
                  <li>Self-Customizing → 정보관리 → API 인증키관리 이동</li>
                  <li>API 인증키 발급 (발급된 키는 안전하게 보관)</li>
                </ol>
              </div>

              <Separator />

              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">SellSync에서 ERP 연동</h4>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-2 text-left font-semibold">항목</th>
                        <th className="px-4 py-2 text-left font-semibold">설명</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      <tr>
                        <td className="px-4 py-2 font-mono text-xs">COM_CODE</td>
                        <td className="px-4 py-2 text-gray-600">
                          Ecount 회사코드 (로그인 시 사용하는 회사코드)
                        </td>
                      </tr>
                      <tr>
                        <td className="px-4 py-2 font-mono text-xs">USER_ID</td>
                        <td className="px-4 py-2 text-gray-600">Ecount 사용자 ID</td>
                      </tr>
                      <tr>
                        <td className="px-4 py-2 font-mono text-xs">API_CERT_KEY</td>
                        <td className="px-4 py-2 text-gray-600">위에서 발급받은 API 인증키</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <Link href="/settings/integrations?tab=erp">
                <Badge className="cursor-pointer">
                  ERP 연동 설정하러 가기 →
                </Badge>
              </Link>
            </CardContent>
          </Card>

          <Card id="setup-store">
            <CardHeader>
              <CardTitle>2. 네이버 스마트스토어 연동</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">Step 1: 네이버 커머스 API 키 발급</h4>
                <ol className="space-y-2 list-decimal list-inside text-gray-600 text-sm">
                  <li>
                    네이버 커머스 API 센터 접속:{' '}
                    <a
                      href="https://apicenter.commerce.naver.com"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:underline inline-flex items-center gap-1"
                    >
                      apicenter.commerce.naver.com
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  </li>
                  <li>스마트스토어 통합매니저 계정으로 로그인</li>
                  <li>우측 상단 [계정생성] 클릭</li>
                  <li>개발업체 계정명, 장애대응 연락처 입력 후 약관 동의</li>
                  <li>[애플리케이션 등록] → [등록하기] 클릭</li>
                  <li>
                    애플리케이션 정보 입력:
                    <ul className="ml-6 mt-1 space-y-1">
                      <li>• 애플리케이션 이름: 스토어명 또는 SellSync 연동</li>
                      <li>• API 호출 IP: SellSync 서버 IP 입력 (문의 필요)</li>
                      <li>• API 그룹: 주문/판매자 API 선택</li>
                    </ul>
                  </li>
                  <li>등록 완료 후 Client ID와 Client Secret 확인</li>
                </ol>
              </div>

              <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <p className="text-sm text-red-800">
                  <strong>주의:</strong> 네이버 커머스 API는 통합매니저 권한으로만 발급 가능합니다. 또한
                  주기적인 인증이 필요하며, 미인증 시 휴면 처리됩니다.
                </p>
              </div>

              <Separator />

              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">Step 2: SellSync에서 스토어 연동</h4>
                <p className="text-sm text-gray-600">
                  설정 → 연동 설정 → 스토어 목록에서 [+ 스토어 추가] 클릭 후 네이버 스마트스토어 선택
                </p>
              </div>

              <Link href="/settings/integrations?tab=stores">
                <Badge className="cursor-pointer">
                  스토어 연동 설정하러 가기 →
                </Badge>
              </Link>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>3. 쿠팡 연동</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">Step 1: 쿠팡 Wing에서 API 키 발급</h4>
                <ol className="space-y-2 list-decimal list-inside text-gray-600 text-sm">
                  <li>
                    쿠팡 Wing 접속:{' '}
                    <a
                      href="https://wing.coupang.com"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:underline inline-flex items-center gap-1"
                    >
                      wing.coupang.com
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  </li>
                  <li>판매자 계정으로 로그인</li>
                  <li>우측 상단 [본인 아이디] 클릭 → [판매자정보] 또는 [추가판매정보] 메뉴 선택</li>
                  <li>하단의 [OPEN API 키 발급] 섹션에서 [API Key 발급 받기] 클릭</li>
                  <li>키의 사용 목적 팝업에서 [OPEN API] 선택 후 확인</li>
                  <li>연동업체 선택 또는 자체개발 선택 (자체개발 시 IP 주소 입력 필요)</li>
                  <li>발급 완료 후 업체코드(Vendor ID), Access Key, Secret Key 확인</li>
                </ol>
              </div>

              <div className="bg-red-50 border border-red-200 rounded-lg p-4 space-y-2">
                <p className="text-sm text-red-800">
                  <strong>주의사항:</strong>
                </p>
                <ul className="text-sm text-red-800 space-y-1 ml-4">
                  <li>• 사업자 인증을 완료해야 API 키 발급이 가능합니다</li>
                  <li>• OPEN API 키의 유효기간은 180일이며, 만료 전 재발급이 필요합니다</li>
                  <li>• 발급된 키는 외부 유출에 주의하세요</li>
                </ul>
              </div>

              <Link href="/settings/integrations?tab=stores">
                <Badge className="cursor-pointer">
                  스토어 연동 설정하러 가기 →
                </Badge>
              </Link>
            </CardContent>
          </Card>
        </TabsContent>

        {/* 주문 동기화 */}
        <TabsContent value="sync" className="space-y-6">
          <Card id="sync-items">
            <CardHeader>
              <CardTitle>1. ERP 품목 동기화</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-gray-600">
                상품 매핑을 위해 먼저 ERP의 품목 정보를 SellSync로 가져와야 합니다.
              </p>
              <ul className="space-y-2 text-sm text-gray-600">
                <li className="flex items-start gap-2">
                  <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                  동기화 메뉴에서 [ERP 품목 동기화] 버튼 클릭
                </li>
                <li className="flex items-start gap-2">
                  <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                  ERP에 등록된 모든 품목이 SellSync로 동기화됩니다
                </li>
                <li className="flex items-start gap-2">
                  <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                  새 품목을 ERP에 추가한 경우 다시 동기화 필요
                </li>
              </ul>
              <Link href="/sync">
                <Badge className="cursor-pointer">
                  동기화 페이지로 이동 →
                </Badge>
              </Link>
            </CardContent>
          </Card>

          <Card id="sync-orders">
            <CardHeader>
              <CardTitle>2. 주문 수집 및 전표 생성</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-gray-600">
                SellSync는 30분마다 자동으로 새 주문을 수집하고 ERP 전표를 생성합니다.
              </p>
              
              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">자동 동기화</h4>
                <ul className="space-y-2 text-sm text-gray-600">
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    30분 주기로 자동 실행
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    연동된 모든 스토어의 신규 주문 수집
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    매핑 완료된 상품에 대해 자동으로 판매전표 생성
                  </li>
                </ul>
              </div>

              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">수동 동기화</h4>
                <ul className="space-y-2 text-sm text-gray-600">
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    동기화 메뉴에서 [전체 주문 동기화] 버튼 클릭
                  </li>
                  <li className="flex items-start gap-2">
                    <ChevronRight className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    또는 개별 스토어별로 동기화 실행 가능
                  </li>
                </ul>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* 상품 매핑 */}
        <TabsContent value="mapping" className="space-y-6">
          <Card id="mapping">
            <CardHeader>
              <CardTitle>상품 매핑</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-gray-600">
                쇼핑몰 상품과 ERP 품목을 연결하는 과정입니다. 매핑이 완료되어야 전표가 생성됩니다.
              </p>

              <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">매핑 절차</h4>
                <ol className="space-y-2 list-decimal list-inside text-gray-600">
                  <li>상품 매핑 메뉴에서 미매핑 상품 확인</li>
                  <li>상품 선택 후 해당하는 ERP 품목 검색</li>
                  <li>매핑 저장</li>
                </ol>
              </div>

              <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <p className="text-sm text-red-800">
                  <strong>중요:</strong> 매핑되지 않은 상품이 포함된 주문은 전표가 생성되지 않습니다. 
                  상품 매핑 화면에서 미매핑 상품을 확인하고 처리해주세요.
                </p>
              </div>

              <Link href="/mappings">
                <Badge className="cursor-pointer">
                  상품 매핑 페이지로 이동 →
                </Badge>
              </Link>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
