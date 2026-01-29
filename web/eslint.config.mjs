import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // 커스텀 규칙
  {
    rules: {
      // any 타입을 warning으로 완화 (에러가 아닌 경고로 표시)
      "@typescript-eslint/no-explicit-any": "warn",
      // 사용하지 않는 변수를 warning으로 완화
      "@typescript-eslint/no-unused-vars": ["warn", { 
        "argsIgnorePattern": "^_",
        "varsIgnorePattern": "^_"
      }],
      // 빈 객체 타입 허용
      "@typescript-eslint/no-empty-object-type": "warn",
      // require 사용 허용 (JS 파일에서)
      "@typescript-eslint/no-require-imports": "warn",
      // React Hook Form 관련 경고 완화
      "react-hooks/incompatible-library": "warn",
      // useEffect 내 setState 경고 완화
      "react-hooks/set-state-in-effect": "warn",
    }
  }
]);

export default eslintConfig;
