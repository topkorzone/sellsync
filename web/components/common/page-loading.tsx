export function PageLoading() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
      <div className="flex flex-col items-center gap-4 animate-in fade-in duration-500">
        <div className="relative">
          <div className="animate-spin rounded-full h-12 w-12 border-b-3 border-gray-900" />
          <div className="absolute inset-0 rounded-full border-2 border-gray-200" />
        </div>
        <p className="text-sm font-medium text-gray-600">로딩 중...</p>
      </div>
    </div>
  );
}
