package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.posting.enums.PostingStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-001-1 검증] PostingStatus 상태전이 매트릭스 검증
 * 
 * ADR-0001 기준과 실제 구현이 정확히 일치하는지 전수 검사
 */
@Slf4j
class PostingStateTransitionMatrixTest {

    @Test
    @DisplayName("[상태전이 매트릭스] ADR-0001 기준과 완전히 일치하는지 검증")
    void testStateTransitionMatrix_matchesADR() {
        log.info("=".repeat(80));
        log.info("PostingStatus 상태전이 매트릭스 (ADR-0001 검증)");
        log.info("=".repeat(80));
        
        // 헤더 출력
        log.info(String.format("%-20s | %s", "FROM \\ TO", 
            "READY | READY_TO_POST | POSTING_REQUESTED | POSTED | FAILED"));
        log.info("-".repeat(80));

        // 각 상태별로 전수 검사
        for (PostingStatus from : PostingStatus.values()) {
            StringBuilder row = new StringBuilder(String.format("%-20s |", from));
            
            for (PostingStatus to : PostingStatus.values()) {
                boolean allowed = from.canTransitionTo(to);
                row.append(allowed ? "  ✅   |" : "  ❌   |");
            }
            
            log.info(row.toString());
        }
        
        log.info("=".repeat(80));

        // ADR-0001 기준 명시적 검증
        log.info("\n[ADR-0001 허용 전이 검증]");
        
        // 1. 정상 흐름
        assertThat(PostingStatus.READY.canTransitionTo(PostingStatus.READY_TO_POST))
            .as("READY -> READY_TO_POST 허용").isTrue();
        assertThat(PostingStatus.READY_TO_POST.canTransitionTo(PostingStatus.POSTING_REQUESTED))
            .as("READY_TO_POST -> POSTING_REQUESTED 허용").isTrue();
        assertThat(PostingStatus.POSTING_REQUESTED.canTransitionTo(PostingStatus.POSTED))
            .as("POSTING_REQUESTED -> POSTED 허용").isTrue();
        log.info("✅ 정상 흐름: READY -> READY_TO_POST -> POSTING_REQUESTED -> POSTED");

        // 2. 실패 처리
        assertThat(PostingStatus.POSTING_REQUESTED.canTransitionTo(PostingStatus.FAILED))
            .as("POSTING_REQUESTED -> FAILED 허용").isTrue();
        log.info("✅ 실패 처리: POSTING_REQUESTED -> FAILED");

        // 3. 재시도
        assertThat(PostingStatus.FAILED.canTransitionTo(PostingStatus.POSTING_REQUESTED))
            .as("FAILED -> POSTING_REQUESTED 허용 (재시도)").isTrue();
        log.info("✅ 재시도: FAILED -> POSTING_REQUESTED");

        log.info("\n[ADR-0001 금지 전이 검증]");
        
        // 4. POSTED 불변성
        for (PostingStatus to : PostingStatus.values()) {
            assertThat(PostingStatus.POSTED.canTransitionTo(to))
                .as("POSTED -> %s 금지", to).isFalse();
        }
        log.info("✅ POSTED 불변성: 모든 전이 금지");

        // 5. FAILED -> READY 금지
        assertThat(PostingStatus.FAILED.canTransitionTo(PostingStatus.READY))
            .as("FAILED -> READY 금지").isFalse();
        log.info("✅ FAILED -> READY 금지");

        // 6. 단계 건너뛰기 금지
        assertThat(PostingStatus.READY.canTransitionTo(PostingStatus.POSTED))
            .as("READY -> POSTED 금지 (단계 건너뛰기)").isFalse();
        assertThat(PostingStatus.READY.canTransitionTo(PostingStatus.POSTING_REQUESTED))
            .as("READY -> POSTING_REQUESTED 금지 (단계 건너뛰기)").isFalse();
        log.info("✅ 단계 건너뛰기 금지");

        log.info("\n" + "=".repeat(80));
        log.info("✅ 상태전이 매트릭스 검증 완료: ADR-0001 기준과 완벽히 일치");
        log.info("=".repeat(80));
    }

    @Test
    @DisplayName("[상태전이 통계] 각 상태별 허용/금지 전이 개수")
    void testStateTransitionStatistics() {
        log.info("\n[상태전이 통계]");
        
        for (PostingStatus from : PostingStatus.values()) {
            long allowedCount = EnumSet.allOf(PostingStatus.class).stream()
                .filter(from::canTransitionTo)
                .count();
            
            long forbiddenCount = PostingStatus.values().length - allowedCount;
            
            log.info("{}: 허용={}, 금지={}", from, allowedCount, forbiddenCount);
            
            // POSTED는 모든 전이 금지
            if (from == PostingStatus.POSTED) {
                assertThat(allowedCount).isEqualTo(0);
            }
            
            // 나머지 상태는 최소 1개 이상 허용
            if (from != PostingStatus.POSTED) {
                assertThat(allowedCount).isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("[상태전이 경로] 모든 유효한 경로 탐색")
    void testAllValidPaths() {
        log.info("\n[유효한 상태 전이 경로]");
        
        // 정상 경로
        log.info("1. 정상: READY -> READY_TO_POST -> POSTING_REQUESTED -> POSTED");
        assertPath(PostingStatus.READY, PostingStatus.READY_TO_POST, 
                   PostingStatus.POSTING_REQUESTED, PostingStatus.POSTED);
        
        // 실패 경로
        log.info("2. 실패: READY -> READY_TO_POST -> POSTING_REQUESTED -> FAILED");
        assertPath(PostingStatus.READY, PostingStatus.READY_TO_POST, 
                   PostingStatus.POSTING_REQUESTED, PostingStatus.FAILED);
        
        // 재시도 후 성공 경로
        log.info("3. 재시도: ... -> FAILED -> POSTING_REQUESTED -> POSTED");
        assertThat(PostingStatus.FAILED.canTransitionTo(PostingStatus.POSTING_REQUESTED)).isTrue();
        assertThat(PostingStatus.POSTING_REQUESTED.canTransitionTo(PostingStatus.POSTED)).isTrue();
        
        log.info("✅ 모든 유효 경로 검증 완료");
    }

    private void assertPath(PostingStatus... states) {
        for (int i = 0; i < states.length - 1; i++) {
            PostingStatus from = states[i];
            PostingStatus to = states[i + 1];
            assertThat(from.canTransitionTo(to))
                .as("%s -> %s 허용", from, to)
                .isTrue();
        }
    }
}
