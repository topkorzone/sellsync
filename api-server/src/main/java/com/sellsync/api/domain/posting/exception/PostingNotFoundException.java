package com.sellsync.api.domain.posting.exception;

import java.util.UUID;

/**
 * 전표를 찾을 수 없는 경우 예외
 */
public class PostingNotFoundException extends RuntimeException {

    private UUID postingId;

    public PostingNotFoundException(UUID postingId) {
        super(String.format("전표를 찾을 수 없습니다: postingId=%s", postingId));
        this.postingId = postingId;
    }

    /**
     * 메시지만으로 예외 생성 (템플릿, 필드 등)
     */
    public PostingNotFoundException(String message) {
        super(message);
    }

    public UUID getPostingId() {
        return postingId;
    }
}
