'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { useAuthStore } from '@/lib/stores/auth-store';
import { User, Building2, Mail, Shield } from 'lucide-react';

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '최고 관리자',
  TENANT_ADMIN: '테넌트 관리자',
  OPERATOR: '운영자',
  VIEWER: '뷰어',
};

export default function ProfileSettingsPage() {
  const { user } = useAuthStore();

  if (!user) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-muted-foreground">로딩 중...</div>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">프로필 설정</h1>
        <p className="text-muted-foreground">계정 정보를 확인합니다.</p>
      </div>

      {/* 사용자 정보 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">사용자 정보</CardTitle>
          <CardDescription>로그인 계정의 기본 정보입니다.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4 p-4 border rounded-lg">
            <div className="h-14 w-14 rounded-full bg-gray-900 flex items-center justify-center flex-shrink-0">
              <span className="text-white font-bold text-xl">
                {user.username?.charAt(0).toUpperCase() || 'U'}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="font-semibold text-lg">{user.username}</div>
              <div className="text-sm text-muted-foreground">{user.email}</div>
            </div>
            <Badge variant="secondary">
              {ROLE_LABELS[user.role] || user.role}
            </Badge>
          </div>

          <Separator />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex items-start gap-3 p-3 rounded-lg bg-gray-50">
              <User className="h-5 w-5 text-muted-foreground mt-0.5" />
              <div>
                <div className="text-sm font-medium text-muted-foreground">사용자 이름</div>
                <div className="font-medium">{user.username}</div>
              </div>
            </div>
            <div className="flex items-start gap-3 p-3 rounded-lg bg-gray-50">
              <Mail className="h-5 w-5 text-muted-foreground mt-0.5" />
              <div>
                <div className="text-sm font-medium text-muted-foreground">이메일</div>
                <div className="font-medium">{user.email}</div>
              </div>
            </div>
            <div className="flex items-start gap-3 p-3 rounded-lg bg-gray-50">
              <Shield className="h-5 w-5 text-muted-foreground mt-0.5" />
              <div>
                <div className="text-sm font-medium text-muted-foreground">역할</div>
                <div className="font-medium">{ROLE_LABELS[user.role] || user.role}</div>
              </div>
            </div>
            <div className="flex items-start gap-3 p-3 rounded-lg bg-gray-50">
              <Building2 className="h-5 w-5 text-muted-foreground mt-0.5" />
              <div>
                <div className="text-sm font-medium text-muted-foreground">테넌트</div>
                <div className="font-medium">{user.tenantName}</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
