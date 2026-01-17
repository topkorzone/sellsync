package com.sellsync.api.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * 현재 인증된 사용자 정보를 주입하는 어노테이션
 * 
 * <p>컨트롤러 메서드 파라미터에 사용하여 현재 사용자 정보를 가져옵니다.
 * 
 * <pre>
 * {@code
 * @GetMapping("/profile")
 * public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal user) {
 *     // user 객체 사용
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? null : #this")
public @interface CurrentUser {
}
