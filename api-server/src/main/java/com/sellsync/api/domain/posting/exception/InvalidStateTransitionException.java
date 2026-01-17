package com.sellsync.api.domain.posting.exception;

import com.sellsync.api.domain.posting.enums.PostingStatus;

/**
 * 허용되지 않은 상태 전이 예외 (ADR-0001)
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final PostingStatus from;
    private final PostingStatus to;

    public InvalidStateTransitionException(PostingStatus from, PostingStatus to) {
        super(String.format("금지된 상태 전이: %s -> %s", from, to));
        this.from = from;
        this.to = to;
    }

    public PostingStatus getFrom() {
        return from;
    }

    public PostingStatus getTo() {
        return to;
    }
}
