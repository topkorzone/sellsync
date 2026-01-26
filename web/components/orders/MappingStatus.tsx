interface MappingStatusProps {
  status: 'MAPPED' | 'PARTIAL' | 'UNMAPPED' | null;
}

export function MappingStatus({ status }: MappingStatusProps) {
  if (status === 'MAPPED') {
    return (
      <span className="text-xs text-gray-400">
        완료
      </span>
    );
  }

  if (status === 'PARTIAL') {
    return (
      <span className="text-xs text-orange-600 font-medium">
        부분
      </span>
    );
  }

  if (status === 'UNMAPPED') {
    return (
      <span className="text-xs text-orange-600 font-medium">
        매핑필요
      </span>
    );
  }

  return <span className="text-gray-300 text-xs">-</span>;
}
