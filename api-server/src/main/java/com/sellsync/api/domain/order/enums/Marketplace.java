package com.sellsync.api.domain.order.enums;

/**
 * 마켓플레이스 구분 (TRD v2)
 */
public enum Marketplace {
    NAVER_SMARTSTORE("네이버 스마트스토어"),
    COUPANG("쿠팡"),
    GMARKET("G마켓"),
    AUCTION("옥션"),
    ELEVENTH_STREET("11번가"),
    INTERPARK("인터파크"),
    WMP("위메프"),
    TMON("티몬"),
    ETC("기타");

    private final String displayName;

    Marketplace(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
