package com.sellsync.api.domain.sync.enums;

/**
 * 동기화 작업 트리거 유형
 */
public enum SyncTriggerType {
    SCHEDULED("스케줄"),      // 주기적 배치 작업
    MANUAL("수동"),           // 사용자 수동 실행
    WEBHOOK("웹훅");          // 마켓 웹훅 이벤트

    private final String displayName;

    SyncTriggerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
