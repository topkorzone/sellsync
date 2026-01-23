package com.sellsync.api.domain.onboarding.dto;

import com.sellsync.api.domain.tenant.enums.OnboardingStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class OnboardingProgressResponse {
    private OnboardingStatus onboardingStatus;
    private int currentStep;
    private int totalSteps;
    private Map<String, Boolean> steps;
    private BusinessInfoDto businessInfo;
}
