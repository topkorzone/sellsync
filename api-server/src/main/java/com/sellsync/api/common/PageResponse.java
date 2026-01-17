package com.sellsync.api.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 페이징 응답 DTO
 * 
 * <p>목록 조회 API의 표준 응답 형식
 */
@Data
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
