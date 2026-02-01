'use client';

import { useState, useEffect, useCallback } from 'react';
import Image from 'next/image';
import { toast } from 'sonner';
import {
  ExternalLink,
  Copy,
  CheckCircle2,
  AlertTriangle,
  Info,
  ChevronDown,
} from 'lucide-react';
import { cn } from '@/lib/utils';

// ─── Types ────────────────────────────────────────────
type MarketplaceTab = 'smartstore' | 'coupang' | 'ecount';

type AlertVariant = 'info' | 'warning' | 'success';

// ─── Shared UI Components ─────────────────────────────

function StepCard({
  step,
  title,
  subtitle,
  color,
  children,
  borderColor,
}: {
  step: string;
  title: string;
  subtitle: string;
  color: string;
  children: React.ReactNode;
  borderColor?: string;
}) {
  return (
    <div
      className={cn(
        'bg-white rounded-2xl p-6 sm:p-8 mb-6 shadow-sm',
        borderColor && `border-l-4 ${borderColor}`
      )}
    >
      <div className="flex items-center gap-4 mb-6">
        <div
          className="w-12 h-12 rounded-xl flex items-center justify-center font-bold text-xl text-white flex-shrink-0"
          style={{ background: color }}
        >
          {step}
        </div>
        <div>
          <div className="text-xl font-bold text-gray-900">{title}</div>
          <div className="text-sm text-gray-500 mt-1">{subtitle}</div>
        </div>
      </div>
      {children}
    </div>
  );
}

function Instruction({
  number,
  children,
  isLast,
}: {
  number: number;
  children: React.ReactNode;
  isLast?: boolean;
}) {
  return (
    <div
      className={cn(
        'flex gap-4 py-5',
        !isLast && 'border-b border-gray-100'
      )}
    >
      <div className="w-7 h-7 rounded-full bg-gray-100 flex items-center justify-center font-semibold text-sm text-gray-700 flex-shrink-0">
        {number}
      </div>
      <div className="flex-1 min-w-0">{children}</div>
    </div>
  );
}

function InstructionText({ children }: { children: React.ReactNode }) {
  return (
    <div className="text-[15px] text-gray-700 mb-3 leading-relaxed">
      {children}
    </div>
  );
}

function Screenshot({
  src,
  alt,
  caption,
}: {
  src: string;
  alt: string;
  caption: string;
}) {
  return (
    <div className="my-4 rounded-xl overflow-hidden border border-gray-200 bg-gray-100">
      <Image
        src={src}
        alt={alt}
        width={800}
        height={500}
        className="w-full h-auto block"
        sizes="(max-width: 900px) 100vw, 800px"
      />
      <div className="px-4 py-3 bg-gray-50 text-[13px] text-gray-500 border-t border-gray-200">
        {caption}
      </div>
    </div>
  );
}

function LinkButton({
  href,
  children,
}: {
  href: string;
  children: React.ReactNode;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-2 px-4 py-2.5 bg-gray-100 rounded-lg text-blue-600 text-sm font-medium hover:bg-gray-200 transition-colors mb-3"
    >
      <ExternalLink className="w-4 h-4" />
      {children}
    </a>
  );
}

function AlertBox({
  variant,
  title,
  children,
}: {
  variant: AlertVariant;
  title: string;
  children: React.ReactNode;
}) {
  const styles = {
    info: 'bg-blue-50 border-blue-600 text-blue-800',
    warning: 'bg-amber-50 border-amber-500 text-amber-900',
    success: 'bg-emerald-50 border-emerald-500 text-emerald-800',
  };

  const icons = {
    info: <Info className="w-4 h-4 flex-shrink-0 mt-0.5" />,
    warning: <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />,
    success: <CheckCircle2 className="w-4 h-4 flex-shrink-0 mt-0.5" />,
  };

  return (
    <div
      className={cn(
        'p-4 rounded-xl text-sm mt-4 border-l-4',
        styles[variant]
      )}
    >
      <div className="flex items-start gap-2">
        {icons[variant]}
        <div>
          <strong className="block mb-1">{title}</strong>
          {children}
        </div>
      </div>
    </div>
  );
}

function ValueBox({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      toast.success('복사되었습니다!');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('복사에 실패했습니다');
    }
  }, [value]);

  return (
    <div className="inline-flex items-center gap-2 px-3.5 py-2 bg-gray-900 rounded-md font-mono text-sm text-white my-2">
      {value}
      <button
        type="button"
        onClick={handleCopy}
        className="text-gray-300 hover:text-white hover:bg-white/10 p-1 rounded transition-colors"
      >
        {copied ? (
          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
        ) : (
          <Copy className="w-4 h-4" />
        )}
      </button>
    </div>
  );
}

function InputPreview({
  title,
  fields,
}: {
  title: string;
  fields: { label: string; sample: string }[];
}) {
  return (
    <div className="bg-gray-50 border-2 border-dashed border-gray-200 rounded-xl p-5 mt-4">
      <div className="text-[13px] text-gray-500 mb-3 font-medium">{title}</div>
      <div className="space-y-3">
        {fields.map((field) => (
          <div key={field.label} className="flex flex-col gap-1.5">
            <span className="text-[13px] font-semibold text-gray-700">
              {field.label}
            </span>
            <div className="px-3.5 py-2.5 bg-white border border-gray-200 rounded-md text-sm text-gray-500">
              {field.sample}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ChecklistItem({
  id,
  label,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-start gap-3 py-3">
      <input
        type="checkbox"
        id={id}
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="w-5 h-5 mt-0.5 cursor-pointer accent-emerald-500 rounded"
      />
      <label
        htmlFor={id}
        className="text-[15px] text-gray-700 cursor-pointer select-none"
      >
        {label}
      </label>
    </div>
  );
}

function Checklist({
  items,
}: {
  items: { id: string; label: string }[];
}) {
  const [checkedState, setCheckedState] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const saved: Record<string, boolean> = {};
    items.forEach((item) => {
      const val = localStorage.getItem(`guide-${item.id}`);
      if (val === 'true') saved[item.id] = true;
    });
    setCheckedState(saved);
  }, [items]);

  const handleChange = (id: string, checked: boolean) => {
    setCheckedState((prev) => ({ ...prev, [id]: checked }));
    localStorage.setItem(`guide-${id}`, String(checked));
  };

  return (
    <div className="mt-5 pt-5 border-t border-gray-100">
      {items.map((item) => (
        <ChecklistItem
          key={item.id}
          id={item.id}
          label={item.label}
          checked={checkedState[item.id] || false}
          onChange={(checked) => handleChange(item.id, checked)}
        />
      ))}
    </div>
  );
}

// ─── SmartStore Guide ─────────────────────────────────

const NAVER_COLOR = '#03c75a';

const NAVER_CHECKLIST = [
  { id: 'naver-check-1', label: '커머스 API 계정 가입 완료' },
  { id: 'naver-check-2', label: '애플리케이션 등록 완료' },
  { id: 'naver-check-3', label: 'IP 주소 (54.180.135.117) 추가 완료' },
  { id: 'naver-check-4', label: 'SellSync에 키 입력 완료' },
];

function SmartStoreGuide() {
  return (
    <>
      {/* Step 1: 계정 생성 */}
      <StepCard
        step="1"
        title="커머스 API 계정 만들기"
        subtitle="최초 1회만 진행하면 됩니다"
        color={NAVER_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            먼저 <strong>통합매니저 권한</strong>이 있는 계정인지 확인하세요.
            <br />
            스마트스토어센터 &rarr; 판매자정보 &rarr; 매니저관리에서 확인할 수
            있어요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-02-manager.png"
            alt="통합매니저 권한 확인"
            caption='매니저관리에서 "통합 매니저" 권한 확인'
          />
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            아래 링크를 클릭해서 <strong>네이버 커머스 API 센터</strong>에
            접속하세요.
          </InstructionText>
          <LinkButton href="https://apicenter.commerce.naver.com/ko/basic/join/step1">
            커머스 API 센터 바로가기
          </LinkButton>
          <Screenshot
            src="/images/guide/naver-03-apicenter.png"
            alt="커머스 API 센터"
            caption='초록색 버튼 "내 스마트스토어 전용 커머스API가 필요한 스토어" 클릭'
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            로그인 화면이 나오면{' '}
            <strong>통합매니저 권한이 있는 계정</strong>으로 로그인하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-04-login-form.png"
            alt="로그인 화면"
            caption="네이버 커머스 ID로 로그인"
          />
        </Instruction>

        <Instruction number={4} isLast>
          <InstructionText>
            계정 정보를 입력하고 <strong>가입하기</strong> 버튼을 클릭하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-06-account-form.png"
            alt="계정 생성"
            caption="① 계정명 입력 → ② 이메일 인증 → ③ 가입하기 클릭"
          />
          <AlertBox variant="success" title="이미 가입되어 있다면?">
            자동으로 다음 단계로 넘어가니 걱정하지 마세요!
          </AlertBox>
          <Screenshot
            src="/images/guide/naver-07-already-joined.png"
            alt="이미 가입된 경우"
            caption="이미 가입된 경우 이런 팝업이 뜹니다. 확인 클릭!"
          />
        </Instruction>
      </StepCard>

      {/* Step 2: 애플리케이션 등록 */}
      <StepCard
        step="2"
        title="애플리케이션 등록하기"
        subtitle="연동에 필요한 키를 발급받아요"
        color={NAVER_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            아래 링크로 접속한 뒤 <strong>&quot;등록하기&quot;</strong> 버튼을
            클릭하세요.
          </InstructionText>
          <LinkButton href="https://apicenter.commerce.naver.com/ko/member/home">
            애플리케이션 관리 바로가기
          </LinkButton>
          <Screenshot
            src="/images/guide/naver-08-app-register.png"
            alt="애플리케이션 등록"
            caption='초록색 "등록하기" 버튼 클릭'
          />
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            오른쪽 위에서 <strong>연동할 스토어를 선택</strong>하고,
            애플리케이션 이름과 설명을 입력하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-09-store-select.png"
            alt="스토어 선택"
            caption="① 스토어 선택 → ② 이름/설명 입력"
          />
          <InputPreview
            title="입력 예시"
            fields={[
              { label: '애플리케이션 이름', sample: 'SellSync' },
              { label: '설명', sample: '주문 연동용' },
            ]}
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            아래로 스크롤해서 <strong>API 그룹을 모두 추가</strong>하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-10-api-group.png"
            alt="API 그룹 추가"
            caption='각 항목의 "추가" 버튼을 클릭해서 모두 추가'
          />
          <Screenshot
            src="/images/guide/naver-11-api-added.png"
            alt="API 그룹 추가 완료"
            caption='추가되면 "삭제" 버튼으로 바뀝니다'
          />
          <AlertBox variant="warning" title="중요!">
            반드시 모든 API 그룹을 추가해야 주문 수집이 정상적으로 됩니다.
          </AlertBox>
        </Instruction>

        <Instruction number={4}>
          <InstructionText>
            맨 아래 <strong>등록</strong> 버튼을 클릭하면 완료!
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-12-register-btn.png"
            alt="등록 버튼"
            caption='초록색 "등록" 버튼 클릭'
          />
        </Instruction>

        <Instruction number={5}>
          <InstructionText>
            등록이 완료되면 <strong>애플리케이션 ID</strong>와{' '}
            <strong>시크릿 코드</strong>가 발급됩니다.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-14-app-keys.png"
            alt="애플리케이션 ID/시크릿"
            caption="애플리케이션 ID와 시크릿 코드 확인"
          />
        </Instruction>

        <Instruction number={6} isLast>
          <InstructionText>
            <strong>수정</strong> 버튼을 클릭해서{' '}
            <strong>API 호출 IP</strong>를 추가하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/naver-15-ip-add.png"
            alt="IP 추가"
            caption="API 호출 IP에 SellSync 서버 IP 추가"
          />
          <InputPreview
            title="추가할 IP 주소"
            fields={[{ label: 'IP 주소', sample: '' }]}
          />
          <div className="mt-2">
            <ValueBox value="54.180.135.117" />
          </div>
          <AlertBox variant="warning" title="IP 주소 필수!">
            IP 주소를 입력하지 않으면 SellSync에서 주문을 가져올 수 없어요.
          </AlertBox>
        </Instruction>
      </StepCard>

      {/* Step 3: SellSync 입력 */}
      <StepCard
        step="3"
        title="SellSync에 입력하기"
        subtitle="발급받은 키를 붙여넣기 하세요"
        color={NAVER_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            SellSync에서 <strong>스토어 연동</strong> 메뉴로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            발급받은 정보를 아래와 같이 입력하세요:
          </InstructionText>
          <InputPreview
            title="SellSync 입력란"
            fields={[
              {
                label: 'Client ID (클라이언트 ID)',
                sample: '애플리케이션 ID를 붙여넣기',
              },
              {
                label: 'Client Secret (클라이언트 시크릿)',
                sample: '시크릿 코드를 붙여넣기',
              },
            ]}
          />
        </Instruction>

        <Instruction number={3} isLast>
          <InstructionText>
            <strong>저장</strong> 버튼을 클릭하면 연동 완료!
          </InstructionText>
        </Instruction>

        <Checklist items={NAVER_CHECKLIST} />
      </StepCard>
    </>
  );
}

// ─── Coupang Guide ────────────────────────────────────

const COUPANG_COLOR = '#e52528';

const COUPANG_CHECKLIST = [
  { id: 'coupang-check-1', label: '쿠팡윙에서 API 발급 완료' },
  {
    id: 'coupang-check-2',
    label: '연동 정보에서 IP 주소 (54.180.135.117) 추가 완료',
  },
  { id: 'coupang-check-3', label: 'SellSync에 키 입력 완료' },
];

function CoupangGuide() {
  return (
    <>
      {/* Step 1: API 발급 */}
      <StepCard
        step="1"
        title="쿠팡윙에서 API 발급받기"
        subtitle="쿠팡 판매자센터에서 진행합니다"
        color={COUPANG_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            <strong>쿠팡윙(판매자센터)</strong>에 로그인 후, 우측 상단{' '}
            <strong>내 아이디</strong>를 클릭하세요.
            <br />
            <strong>판매자정보</strong> 또는 <strong>추가판매정보</strong>를
            클릭하세요.
          </InstructionText>
          <LinkButton href="https://wing.coupang.com">
            쿠팡윙 바로가기
          </LinkButton>
          <Screenshot
            src="/images/guide/coupang-01-menu.png"
            alt="쿠팡윙 메뉴"
            caption="① 우측 상단 아이디 클릭 → ② 판매자정보 또는 추가판매정보 클릭"
          />
          <AlertBox variant="info" title="메뉴 위치가 다를 수 있어요">
            판매자 유형에 따라 &quot;판매자정보&quot; 또는
            &quot;추가판매정보&quot; 중 하나에 있어요.
          </AlertBox>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            페이지 하단으로 스크롤해서{' '}
            <strong>&quot;API Key 발급 받기&quot;</strong> 버튼을 클릭하세요.
            <br />
            팝업창에서 <strong>OPEN API</strong>를 선택하고 확인을 클릭하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-02-api-select.png"
            alt="API Key 발급"
            caption="① API Key 발급 받기 클릭 → ② OPEN API 선택 → ③ 확인 클릭"
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            약관에 동의하고{' '}
            <strong>&quot;약관 동의 및 Key 발급받기&quot;</strong>를 클릭하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-03-terms.png"
            alt="약관 동의"
            caption='약관에 체크 후 "약관 동의 및 Key 발급받기" 클릭'
          />
        </Instruction>

        <Instruction number={4}>
          <InstructionText>
            업체 입력 방식에서{' '}
            <strong>&quot;자체개발(직접입력)&quot;</strong>을 선택하고 아래와
            같이 입력하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-05-self-dev.png"
            alt="자체개발 입력"
            caption="자체개발(직접입력) 선택 후 업체명, URL, IP 주소 입력"
          />
          <InputPreview
            title="입력 내용"
            fields={[
              { label: '업체명', sample: 'SellSync' },
              { label: 'URL', sample: 'https://sell-sync.biz' },
            ]}
          />
          <div className="mt-2">
            <span className="text-[13px] font-semibold text-gray-700 block mb-1">
              IP 주소
            </span>
            <ValueBox value="54.180.135.117" />
          </div>
          <AlertBox variant="warning" title="IP 주소 필수!">
            IP 주소를 입력하지 않으면 SellSync에서 주문을 가져올 수 없어요.
          </AlertBox>
        </Instruction>

        <Instruction number={5}>
          <InstructionText>
            <strong>확인</strong> 버튼을 클릭하면 발급 완료!
            <br />
            <strong>업체코드</strong>, <strong>Access Key</strong>,{' '}
            <strong>Secret Key</strong>가 화면에 표시됩니다.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-06-complete.png"
            alt="발급 완료"
            caption="업체코드, Access Key, Secret Key 확인"
          />
          <AlertBox variant="info" title="참고사항">
            최초 발급 시 실제 사용까지 최대 24시간이 걸릴 수 있어요.
          </AlertBox>
        </Instruction>

        <Instruction number={6}>
          <InstructionText>
            <strong>이미 발급받은 경우</strong>, 연동 정보에서{' '}
            <strong>수정</strong> 버튼을 클릭해서 IP를 추가할 수 있어요.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-08-ip-complete.png"
            alt="연동 정보 수정"
            caption='연동 정보 영역의 "수정" 버튼 클릭'
          />
        </Instruction>

        <Instruction number={7} isLast>
          <InstructionText>
            팝업창에서 <strong>IP 주소</strong> 입력란에 아래 IP를 추가하고{' '}
            <strong>확인</strong>을 클릭하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/coupang-07-ip-edit.png"
            alt="IP 주소 수정"
            caption="IP 주소란에 SellSync 서버 IP 추가"
          />
          <div className="mt-2">
            <span className="text-[13px] font-semibold text-gray-700 block mb-1">
              추가할 IP 주소
            </span>
            <ValueBox value="54.180.135.117" />
          </div>
          <AlertBox variant="warning" title="IP가 여러 개인 경우">
            쉼표(,)로 구분해서 입력하세요. 예: 54.180.135.117, 211.48.92.8
          </AlertBox>
        </Instruction>
      </StepCard>

      {/* Step 2: SellSync 입력 */}
      <StepCard
        step="2"
        title="SellSync에 입력하기"
        subtitle="발급받은 키를 붙여넣기 하세요"
        color={COUPANG_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            SellSync에서 <strong>스토어 연동</strong> 메뉴로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            쿠팡윙에서 발급받은 정보를 아래와 같이 입력하세요:
          </InstructionText>
          <InputPreview
            title="SellSync 입력란"
            fields={[
              {
                label: '업체코드 (Vendor ID)',
                sample: '업체코드를 붙여넣기',
              },
              { label: 'Access Key', sample: 'Access Key를 붙여넣기' },
              { label: 'Secret Key', sample: 'Secret Key를 붙여넣기' },
            ]}
          />
        </Instruction>

        <Instruction number={3} isLast>
          <InstructionText>
            <strong>저장</strong> 버튼을 클릭하면 연동 완료!
          </InstructionText>
        </Instruction>

        <AlertBox variant="warning" title="API 유효기간 안내">
          쿠팡 API는 180일마다 갱신이 필요해요. 만료 14일 전부터 재발급이
          가능하며, SellSync에서 알림을 보내드릴게요.
        </AlertBox>

        <Checklist items={COUPANG_CHECKLIST} />
      </StepCard>
    </>
  );
}

// ─── Ecount ERP Guide ─────────────────────────────────

const ECOUNT_COLOR = '#6366f1';

const ECOUNT_CHECKLIST = [
  { id: 'ecount-check-1', label: '이카운트 ERP에 관리자로 로그인' },
  { id: 'ecount-check-2', label: '회사코드 (6자리) 메모' },
  { id: 'ecount-check-3', label: '테스트 인증키 발급 완료' },
  {
    id: 'ecount-check-4',
    label: 'SellSync에서 연결 테스트 (검증) 완료',
  },
  { id: 'ecount-check-5', label: '실인증키 발급 완료' },
  { id: 'ecount-check-6', label: 'SellSync에 실인증키 입력 완료' },
];

function EcountGuide() {
  return (
    <>
      {/* 사전 안내 */}
      <StepCard
        step="!"
        title="이카운트 API 연동 흐름"
        subtitle="테스트 → 검증 → 실인증키 순서로 진행합니다"
        color={ECOUNT_COLOR}
        borderColor="border-indigo-500"
      >
        <div className="flex items-center gap-2 flex-wrap my-5">
          <span className="bg-amber-100 text-amber-900 px-4 py-2 rounded-lg text-sm font-semibold">
            ① 테스트 인증키 발급
          </span>
          <span className="text-gray-400">&rarr;</span>
          <span className="bg-blue-100 text-blue-900 px-4 py-2 rounded-lg text-sm font-semibold">
            ② API 검증
          </span>
          <span className="text-gray-400">&rarr;</span>
          <span className="bg-emerald-100 text-emerald-900 px-4 py-2 rounded-lg text-sm font-semibold">
            ③ 실인증키 발급
          </span>
        </div>
        <AlertBox variant="warning" title="중요!">
          테스트 인증키로 API를 검증해야만 실인증키를 발급받을 수 있습니다.
          검증하지 않은 API는 실인증키로도 사용할 수 없어요.
        </AlertBox>
      </StepCard>

      {/* Step 1: 테스트 인증키 발급 */}
      <StepCard
        step="1"
        title="테스트 인증키 발급받기"
        subtitle="먼저 테스트용 키를 발급받아요 (유효기간 2주)"
        color="#f59e0b"
      >
        <Instruction number={1}>
          <InstructionText>
            <strong>이카운트 ERP</strong>에 마스터 아이디 또는 관리자 권한이
            있는 아이디로 로그인하세요.
          </InstructionText>
          <LinkButton href="https://login.ecount.com">
            이카운트 ERP 로그인
          </LinkButton>
          <InputPreview
            title="로그인 시 확인할 정보 (메모해 두세요!)"
            fields={[
              {
                label: '회사코드 (6자리 숫자)',
                sample: '예: 123456',
              },
              { label: '사용자 ID', sample: '예: ADMIN' },
            ]}
          />
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            아래 경로로 이동하세요:
            <br />
            <strong>
              Self-Customizing &rarr; 정보관리 &rarr; API인증키발급
            </strong>
          </InstructionText>
          <Screenshot
            src="/images/guide/ecount-01-main.png"
            alt="API인증키발급 메뉴"
            caption="Self-Customizing > 정보관리 > API인증키발급 화면"
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            하단의 <strong>API인증현황</strong> 버튼을 클릭하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={4}>
          <InstructionText>
            팝업창 하단의 <strong>키발급</strong> 버튼을 클릭하고,{' '}
            <strong>테스트 인증키</strong>를 선택하세요.
          </InstructionText>
          <AlertBox variant="info" title="영상 가이드">
            <a
              href="https://www.youtube.com/watch?v=fe-d8OkxOYE"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-700 hover:underline"
            >
              이카운트 공식 유튜브 - 테스트 인증키 발급 방법 (53초)
            </a>
          </AlertBox>
        </Instruction>

        <Instruction number={5} isLast>
          <InstructionText>
            발급이 완료되면 <strong>테스트 인증키</strong>와{' '}
            <strong>발급 ID</strong>가 표시됩니다.
            <br />이 정보를 복사해서 메모해 두세요.
          </InstructionText>
          <AlertBox variant="warning" title="테스트 인증키 유효기간: 2주">
            2주 안에 검증을 완료해야 합니다. 최대 3개까지 발급 가능해요.
          </AlertBox>
        </Instruction>
      </StepCard>

      {/* Step 2: SellSync에서 검증 */}
      <StepCard
        step="2"
        title="SellSync에서 API 검증하기"
        subtitle="테스트 키로 연결해서 API를 검증해요"
        color="#3b82f6"
      >
        <Instruction number={1}>
          <InstructionText>
            SellSync에서 <strong>ERP 연동</strong> 메뉴로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            발급받은 <strong>테스트 인증키</strong> 정보를 입력하세요:
          </InstructionText>
          <InputPreview
            title="SellSync 입력란 (테스트 모드)"
            fields={[
              {
                label: '회사코드 (COM_CODE)',
                sample: '로그인 시 입력한 6자리 숫자',
              },
              {
                label: '사용자 ID (USER_ID)',
                sample: '테스트 인증키 발급 시 선택한 ID',
              },
              {
                label: 'API 인증키 (테스트)',
                sample: '발급받은 테스트 인증키',
              },
            ]}
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            <strong>연결 테스트</strong> 버튼을 클릭하세요.
            <br />
            SellSync가 이카운트 API를 호출하면서 자동으로 검증이 진행됩니다.
          </InstructionText>
          <AlertBox variant="success" title="검증되는 API">
            품목 조회, 거래처 조회, 판매전표 생성 등 SellSync에서 사용하는
            API가 자동으로 검증됩니다.
          </AlertBox>
        </Instruction>

        <Instruction number={4} isLast>
          <InstructionText>
            연결 테스트가 성공하면 <strong>검증 완료!</strong>
            <br />
            이제 이카운트에서 실인증키를 발급받을 수 있어요.
          </InstructionText>
        </Instruction>
      </StepCard>

      {/* Step 3: 실인증키 발급 */}
      <StepCard
        step="3"
        title="실인증키 발급받기"
        subtitle="검증 완료 후 실제 운영용 키를 발급받아요"
        color="#10b981"
      >
        <Instruction number={1}>
          <InstructionText>
            다시 이카운트 ERP로 돌아가서
            <br />
            <strong>
              Self-Customizing &rarr; 정보관리 &rarr; API인증키발급
            </strong>
            으로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            하단의 <strong>키발급</strong> 버튼을 클릭하세요.
          </InstructionText>
          <Screenshot
            src="/images/guide/ecount-02-popup.png"
            alt="키발급 팝업"
            caption="키발급 클릭 → 사용할 ID 선택 → 저장(F8)"
          />
        </Instruction>

        <Instruction number={3}>
          <InstructionText>
            팝업에서 <strong>사용할 ID</strong>를 선택하고{' '}
            <strong>저장(F8)</strong>을 클릭하세요.
            <br />
            <span className="text-gray-500 text-sm">
              ※ 인증키와 허용된 ID가 일치해야 API가 작동합니다
            </span>
          </InstructionText>
        </Instruction>

        <Instruction number={4} isLast>
          <InstructionText>
            발급이 완료되면 <strong>실인증키</strong>와{' '}
            <strong>유효기간</strong>이 표시됩니다.
          </InstructionText>
          <InputPreview
            title="발급 완료 시 확인할 정보"
            fields={[
              {
                label: 'API 인증키 (실인증키)',
                sample: '예: a1b2c3d4-e5f6-7890-abcd-ef1234567890',
              },
              { label: '유효기간', sample: '발급일로부터 1년' },
            ]}
          />
        </Instruction>
      </StepCard>

      {/* Step 4: SellSync에 실인증키 입력 */}
      <StepCard
        step="4"
        title="SellSync에 실인증키 입력하기"
        subtitle="테스트 키를 실인증키로 교체해요"
        color={ECOUNT_COLOR}
      >
        <Instruction number={1}>
          <InstructionText>
            SellSync에서 <strong>ERP 연동</strong> 메뉴로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2}>
          <InstructionText>
            기존 테스트 인증키를 <strong>실인증키</strong>로 교체하세요:
          </InstructionText>
          <InputPreview
            title="SellSync 입력란 (실운영 모드)"
            fields={[
              { label: '회사코드 (COM_CODE)', sample: '그대로 유지' },
              {
                label: '사용자 ID (USER_ID)',
                sample: '실인증키 발급 시 선택한 ID',
              },
              {
                label: 'API 인증키 (실인증키)',
                sample: '새로 발급받은 실인증키로 교체',
              },
            ]}
          />
        </Instruction>

        <Instruction number={3} isLast>
          <InstructionText>
            <strong>저장</strong> 버튼을 클릭하면 연동 완료!
          </InstructionText>
        </Instruction>

        <Checklist items={ECOUNT_CHECKLIST} />
      </StepCard>

      {/* 유효기간 연장 안내 */}
      <StepCard
        step="&#x23F0;"
        title="인증키 유효기간 연장하기"
        subtitle="1년마다 연장이 필요합니다"
        color="#f59e0b"
        borderColor="border-amber-500"
      >
        <Instruction number={1}>
          <InstructionText>
            유효기간 만료 전에{' '}
            <strong>
              Self-Customizing &rarr; 정보관리 &rarr; API인증키발급
            </strong>
            으로 이동하세요.
          </InstructionText>
        </Instruction>

        <Instruction number={2} isLast>
          <InstructionText>
            하단의 <strong>유효기간 연장</strong> 버튼을 클릭하면 1년
            연장됩니다.
          </InstructionText>
          <AlertBox variant="warning" title="주의!">
            인증 기간 만료 후 3개월이 지나면 인증키가 영구 삭제되어 신규
            발급이 필요합니다.
          </AlertBox>
        </Instruction>
      </StepCard>
    </>
  );
}

// ─── Tab Icons ────────────────────────────────────────

function SmartStoreIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={className}>
      <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
    </svg>
  );
}

function CoupangIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12z" />
    </svg>
  );
}

function EcountIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z" />
    </svg>
  );
}

// ─── Main Component ───────────────────────────────────

const TABS: {
  key: MarketplaceTab;
  label: string;
  icon: typeof SmartStoreIcon;
  activeColor: string;
  activeBorder: string;
}[] = [
  {
    key: 'smartstore',
    label: '스마트스토어',
    icon: SmartStoreIcon,
    activeColor: 'bg-[#03c75a] text-white border-[#03c75a]',
    activeBorder: 'border-[#03c75a]',
  },
  {
    key: 'coupang',
    label: '쿠팡',
    icon: CoupangIcon,
    activeColor: 'bg-[#e52528] text-white border-[#e52528]',
    activeBorder: 'border-[#e52528]',
  },
  {
    key: 'ecount',
    label: '이카운트 ERP',
    icon: EcountIcon,
    activeColor: 'bg-[#6366f1] text-white border-[#6366f1]',
    activeBorder: 'border-[#6366f1]',
  },
];

export default function IntegrationGuide() {
  const [activeTab, setActiveTab] = useState<MarketplaceTab>('smartstore');

  return (
    <div>
      {/* Tab Navigation */}
      <div className="flex flex-col sm:flex-row gap-3 mb-8">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={cn(
                'flex-1 flex items-center justify-center gap-2.5 px-6 py-4 rounded-xl text-base font-semibold border-2 transition-all duration-200',
                isActive
                  ? tab.activeColor
                  : 'bg-white text-gray-500 border-gray-200 hover:border-gray-300'
              )}
            >
              <Icon className="w-6 h-6" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      {activeTab === 'smartstore' && <SmartStoreGuide />}
      {activeTab === 'coupang' && <CoupangGuide />}
      {activeTab === 'ecount' && <EcountGuide />}

      {/* Footer */}
      <div className="text-center py-10 text-gray-500 text-sm">
        <p>
          연동이 어려우시면{' '}
          <a
            href="https://pf.kakao.com/_sellsync"
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-600 hover:underline"
          >
            카카오톡으로 문의
          </a>
          해 주세요.
        </p>
        <p className="mt-2">화면공유로 함께 진행해 드립니다 (무료)</p>
      </div>
    </div>
  );
}
