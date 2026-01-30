interface LogoSymbolProps {
  size?: number;
  className?: string;
}

export function LogoSymbol({ size = 32, className }: LogoSymbolProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      className={className}
    >
      <rect width="32" height="32" rx="8" fill="url(#logo-gradient)" />
      <path
        d="M10 13C10 10.239 12.239 8 15 8H18"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M16 5.5L19 8L16 10.5"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M22 19C22 21.761 19.761 24 17 24H14"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M16 21.5L13 24L16 26.5"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <defs>
        <linearGradient
          id="logo-gradient"
          x1="0"
          y1="0"
          x2="32"
          y2="32"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#2563EB" />
        </linearGradient>
      </defs>
    </svg>
  );
}

interface LogoFullProps {
  height?: number;
  className?: string;
}

export function LogoFull({ height = 32, className }: LogoFullProps) {
  const scale = height / 48;
  const width = 200 * scale;

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 200 48"
      fill="none"
      className={className}
    >
      <rect x="2" y="8" width="32" height="32" rx="8" fill="url(#logo-full-gradient)" />
      <path
        d="M12 20C12 16.686 14.686 14 18 14H22"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M20 11L23 14L20 17"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M24 28C24 31.314 21.314 34 18 34H14"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M16 31L13 34L16 37"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <text
        x="44"
        y="33"
        fontFamily="Inter, sans-serif"
        fontSize="24"
        fontWeight="600"
      >
        <tspan fill="#1E293B">Sell</tspan>
        <tspan fill="url(#logo-full-gradient)">Sync</tspan>
      </text>
      <defs>
        <linearGradient
          id="logo-full-gradient"
          x1="0%"
          y1="0%"
          x2="100%"
          y2="100%"
        >
          <stop offset="0%" stopColor="#3B82F6" />
          <stop offset="100%" stopColor="#2563EB" />
        </linearGradient>
      </defs>
    </svg>
  );
}
