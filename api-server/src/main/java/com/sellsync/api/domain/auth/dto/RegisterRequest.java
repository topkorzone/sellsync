package com.sellsync.api.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 */
@Getter
@Setter
public class RegisterRequest {
    
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "유효한 이메일 형식이 아닙니다")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    private String password;
    
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, message = "이름은 2자 이상이어야 합니다")
    private String username;
    
    @NotBlank(message = "회사명은 필수입니다")
    @Size(min = 2, message = "회사명은 2자 이상이어야 합니다")
    private String companyName;
}
