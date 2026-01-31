'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard,
  ShoppingCart,
  FileText,
  Link2,
  Truck,
  RefreshCw,
  Settings,
  Plug,
  Edit3,
  FileCode,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  HelpCircle,
  BookOpen,
  CreditCard,
  Crown,
  User
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useSidebarStore } from '@/lib/stores/sidebar-store';
import { Button } from '@/components/ui/button';
import { LogoSymbol, LogoFull } from '@/components/ui/logo';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import type { LucideIcon } from 'lucide-react';

interface NavItemType {
  label: string;
  href: string;
  icon: LucideIcon;
}

const NAV_ITEMS: NavItemType[] = [
  { label: '대시보드', href: '/dashboard', icon: LayoutDashboard },
  { label: '주문 관리', href: '/orders', icon: ShoppingCart },
  { label: '전표 관리', href: '/postings', icon: FileText },
  { label: '전표 입력', href: '/sale-forms', icon: Edit3 },
  { label: '전표 템플릿', href: '/settings/templates', icon: FileCode },
  { label: '상품 매핑', href: '/mappings', icon: Link2 },
  { label: '송장 관리', href: '/shipments', icon: Truck },
  { label: '동기화', href: '/sync', icon: RefreshCw },
];

const SETTINGS_ITEMS: NavItemType[] = [
  { label: '프로필', href: '/settings/profile', icon: User },
  { label: '연동 설정', href: '/settings/integrations', icon: Plug },
  { label: '구독 관리', href: '/settings/subscription', icon: Crown },
  { label: '결제 관리', href: '/settings/billing', icon: CreditCard },
];

const HELP_ITEMS: NavItemType[] = [
  { label: '사용자 가이드', href: '/help/guide', icon: BookOpen },
  { label: '자주 묻는 질문', href: '/help/faq', icon: HelpCircle },
];

// 네비게이션 아이템 컴포넌트 (재사용)
function NavItem({
  item,
  isActive,
  isCollapsed,
  indent,
  onClick
}: {
  item: NavItemType;
  isActive: boolean;
  isCollapsed: boolean;
  indent?: boolean;
  onClick?: () => void;
}) {
  const content = (
    <Link
      href={item.href}
      onClick={onClick}
      className={cn(
        'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200',
        isCollapsed && 'justify-center px-2',
        indent && !isCollapsed && 'pl-10',
        isActive
          ? 'bg-gray-900 text-white shadow-sm'
          : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
      )}
    >
      <item.icon className="h-5 w-5 flex-shrink-0" />
      {!isCollapsed && <span>{item.label}</span>}
    </Link>
  );

  // 접힌 상태일 때 툴팁 표시
  if (isCollapsed) {
    return (
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>
          {content}
        </TooltipTrigger>
        <TooltipContent side="right" className="font-medium">
          {item.label}
        </TooltipContent>
      </Tooltip>
    );
  }

  return content;
}

// 설정 그룹 컴포넌트
function SettingsGroup({
  isCollapsed,
  onNavClick,
}: {
  isCollapsed: boolean;
  onNavClick?: () => void;
}) {
  const pathname = usePathname();
  const isSettingsActive = SETTINGS_ITEMS.some((item) => pathname.startsWith(item.href));
  const [isOpen, setIsOpen] = useState(isSettingsActive);

  // 접힌 사이드바: 설정 아이콘만 표시 → 클릭 시 /settings/profile 이동
  if (isCollapsed) {
    return (
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>
          <Link
            href="/settings/profile"
            onClick={onNavClick}
            className={cn(
              'flex items-center justify-center px-2 py-2.5 rounded-lg text-sm font-medium transition-all duration-200',
              isSettingsActive
                ? 'bg-gray-900 text-white shadow-sm'
                : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
            )}
          >
            <Settings className="h-5 w-5 flex-shrink-0" />
          </Link>
        </TooltipTrigger>
        <TooltipContent side="right" className="font-medium">
          설정
        </TooltipContent>
      </Tooltip>
    );
  }

  // 펼쳐진 사이드바: 접기/펼치기 가능한 그룹
  return (
    <div>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 w-full',
          isSettingsActive && !isOpen
            ? 'bg-gray-900 text-white shadow-sm'
            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
        )}
      >
        <Settings className="h-5 w-5 flex-shrink-0" />
        <span>설정</span>
        <ChevronDown
          className={cn(
            'h-4 w-4 ml-auto transition-transform duration-200',
            isOpen && 'rotate-180'
          )}
        />
      </button>
      {isOpen && (
        <div className="mt-1 space-y-1">
          {SETTINGS_ITEMS.map((item) => {
            const isActive = pathname.startsWith(item.href);
            return (
              <NavItem
                key={item.href}
                item={item}
                isActive={isActive}
                isCollapsed={false}
                indent
                onClick={onNavClick}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

// 사이드바 컨텐츠 (데스크톱/모바일 공용)
function SidebarContent({
  isCollapsed = false,
  onNavClick
}: {
  isCollapsed?: boolean;
  onNavClick?: () => void;
}) {
  const pathname = usePathname();

  return (
    <div className="flex flex-col h-full">
      {/* Logo Section */}
      <div className={cn(
        "mb-8 pb-6 border-b border-gray-100",
        isCollapsed && "mb-4 pb-4"
      )}>
        <div className={cn(
          "flex items-center gap-3 mb-2",
          isCollapsed && "justify-center"
        )}>
          {isCollapsed ? (
            <LogoSymbol size={36} />
          ) : (
            <LogoFull height={36} />
          )}
        </div>
        {!isCollapsed && (
          <p className="text-xs text-gray-500 ml-12">판매 통합 관리 시스템</p>
        )}
      </div>

      {/* Navigation */}
      <nav className="space-y-1 flex-1">
        <TooltipProvider>
          {NAV_ITEMS.map((item) => {
            const isActive = pathname.startsWith(item.href);
            return (
              <NavItem
                key={item.href}
                item={item}
                isActive={isActive}
                isCollapsed={isCollapsed}
                onClick={onNavClick}
              />
            );
          })}
          <SettingsGroup isCollapsed={isCollapsed} onNavClick={onNavClick} />
        </TooltipProvider>
      </nav>

      {/* 도움말 섹션 */}
      {!isCollapsed && (
        <div className="pt-4 mt-4 border-t border-gray-100">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 px-3">
            도움말
          </p>
          <nav className="space-y-1">
            <TooltipProvider>
              {HELP_ITEMS.map((item) => {
                const isActive = pathname.startsWith(item.href);
                return (
                  <NavItem
                    key={item.href}
                    item={item}
                    isActive={isActive}
                    isCollapsed={false}
                    onClick={onNavClick}
                  />
                );
              })}
            </TooltipProvider>
          </nav>
        </div>
      )}
    </div>
  );
}

// 데스크톱 사이드바
export function Sidebar() {
  const { isCollapsed, toggleCollapse } = useSidebarStore();

  return (
    <aside
      className={cn(
        "hidden lg:flex flex-col bg-white border-r border-gray-100 min-h-screen p-6 relative transition-all duration-300 ease-in-out",
        isCollapsed ? "w-20 p-4" : "w-64"
      )}
    >
      <SidebarContent isCollapsed={isCollapsed} />

      {/* 접기/펼치기 토글 버튼 */}
      <Button
        variant="ghost"
        size="icon"
        onClick={toggleCollapse}
        className={cn(
          "absolute -right-3 top-20 h-6 w-6 rounded-full border bg-white shadow-md hover:bg-gray-50 transition-all duration-200",
          "flex items-center justify-center"
        )}
        aria-label={isCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
      >
        {isCollapsed ? (
          <ChevronRight className="h-3.5 w-3.5" />
        ) : (
          <ChevronLeft className="h-3.5 w-3.5" />
        )}
      </Button>
    </aside>
  );
}

// 모바일 사이드바 (Sheet/Drawer)
export function MobileSidebar() {
  const { isMobileOpen, closeMobile } = useSidebarStore();

  return (
    <Sheet open={isMobileOpen} onOpenChange={closeMobile}>
      <SheetContent side="left" className="w-64 p-6">
        <SheetHeader className="sr-only">
          <SheetTitle>메뉴</SheetTitle>
        </SheetHeader>
        <SidebarContent onNavClick={closeMobile} />
      </SheetContent>
    </Sheet>
  );
}
