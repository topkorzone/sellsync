'use client';

import { useRouter } from 'next/navigation';
import { LogOut, User, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuthStore } from '@/lib/stores/auth-store';
import { useSidebarStore } from '@/lib/stores/sidebar-store';
import { authApi } from '@/lib/api';

export function Header() {
  const router = useRouter();
  const { user, clearAuth } = useAuthStore();
  const { openMobile } = useSidebarStore();

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 로그아웃 에러는 무시하고 진행
    }
    clearAuth();
    router.push('/login');
  };

  return (
    <header className="h-16 border-b border-gray-100 bg-white/80 backdrop-blur-sm px-4 lg:px-6 flex items-center justify-between sticky top-0 z-10">
      <div className="flex items-center gap-3">
        {/* 모바일 햄버거 메뉴 버튼 */}
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden"
          onClick={openMobile}
          aria-label="메뉴 열기"
        >
          <Menu className="h-5 w-5" />
        </Button>
        
        <div>
          <h2 className="text-base font-semibold text-gray-900">{user?.tenantName}</h2>
          <p className="text-xs text-gray-500 hidden sm:block">테넌트 대시보드</p>
        </div>
      </div>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button 
            variant="ghost" 
            className="h-10 w-10 rounded-full transition-all duration-200 hover:ring-2 hover:ring-gray-200 hover:ring-offset-2"
          >
            <Avatar className="h-9 w-9">
              <AvatarFallback className="bg-gray-900 text-white text-sm font-medium">
                {user?.username?.charAt(0).toUpperCase() || 'U'}
              </AvatarFallback>
            </Avatar>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56 shadow-lg">
          <DropdownMenuLabel className="py-3">
            <p className="text-sm font-medium text-gray-900">{user?.username}</p>
            <p className="text-xs text-gray-500 mt-1">{user?.email}</p>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem 
            onClick={() => router.push('/settings/profile')} 
            className="cursor-pointer py-2.5 transition-colors duration-200"
          >
            <User className="mr-2 h-4 w-4" />
            프로필 설정
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem 
            onClick={handleLogout} 
            className="cursor-pointer text-red-600 py-2.5 transition-colors duration-200 focus:text-red-700 focus:bg-red-50"
          >
            <LogOut className="mr-2 h-4 w-4" />
            로그아웃
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
