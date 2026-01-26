interface PostingStatusProps {
  isCompleted: boolean;
}

export function PostingStatus({ isCompleted }: PostingStatusProps) {
  if (isCompleted) {
    return (
      <span className="inline-flex items-center justify-center w-12 py-0.5 text-xs font-medium bg-emerald-500 text-white rounded">
        완료
      </span>
    );
  }

  return <span className="text-gray-300 text-xs">-</span>;
}
