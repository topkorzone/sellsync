package com.sellsync.api.domain.posting.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.posting.entity.Posting;
import com.sellsync.api.domain.posting.exception.ErpApiException;
import com.sellsync.infra.erp.ecount.auth.EcountSessionService;
import com.sellsync.infra.erp.ecount.dto.EcountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * 이카운트 ERP API 클라이언트
 * 
 * 실제 구현 시:
 * - 이카운트 Open API (https://openapi.ecounterp.com) 연동
 * - OAuth 2.0 인증
 * - 전표 전송 API: POST /api/sales/postDocument
 * 
 * 현재: Mock 구현 (개발/테스트용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EcountApiClient implements ErpApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final EcountSessionService sessionService;

    @Override
    public String getErpCode() {
        return "ECOUNT";
    }

    @Override
    public String postDocument(Posting posting, String credentials) {
        log.info("[이카운트 전표 전송] postingId={}, type={}", 
            posting.getPostingId(), posting.getPostingType());

        try {
            // 1. requestPayload 검증
            String requestPayload = posting.getRequestPayload();
            if (requestPayload == null || requestPayload.trim().isEmpty()) {
                throw new ErpApiException("ECOUNT", "INVALID_PAYLOAD", 
                    "전표 데이터(requestPayload)가 없습니다", null, false);
            }

            // 2. 페이로드 로깅 (JSON 문자열 그대로 사용)
            log.info("[이카운트 전표 데이터] postingId={}, payloadSize={}", 
                posting.getPostingId(), requestPayload.length());
            log.debug("[이카운트 전표 JSON] {}", requestPayload);

            // 3. 실제 API 호출 (JSON 문자열 그대로 전달)
            String erpDocNo = callEcountApi(posting.getTenantId(), credentials, requestPayload);

            log.info("[이카운트 전표 전송 완료] postingId={}, erpDocNo={}", 
                posting.getPostingId(), erpDocNo);

            return erpDocNo;

        } catch (ErpApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[이카운트 전표 전송 실패] postingId={}, error={}", 
                posting.getPostingId(), e.getMessage(), e);
            throw new ErpApiException("ECOUNT", "API_ERROR", 
                "이카운트 API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 실제 이카운트 API 호출
     */
    private String callEcountApi(UUID tenantId, String credentialsJson, String payloadJson) {
        try {
            // 1. credentials 파싱
            EcountCredentials creds = objectMapper.readValue(credentialsJson, EcountCredentials.class);
            
            // 2. 세션 ID 획득
            String sessionId = sessionService.getSessionId(tenantId, creds);
            
            // 3. 이카운트 API URL (SESSION_ID를 쿼리 파라미터로 전달)
            String url = String.format("https://oapi%s.ecount.com/OAPI/V2/Sale/SaveSale?SESSION_ID=%s", 
                    creds.getZone(), sessionId);
            
            log.info("[이카운트 API 호출] url={}, sessionId={}", 
                    url.replace(sessionId, "***SESSION***"), sessionId.substring(0, 10) + "...");
            
            // 4. 요청 헤더 생성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 5. 요청 바디 생성 (JSON 문자열을 그대로 사용)
            // payloadJson은 이미 {"SaleList": [...]} 형식의 JSON 문자열
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
            
            log.debug("[이카운트 API 요청 바디] {}", payloadJson);
            
            // 6. API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            log.info("[이카운트 API 응답] status={}, body={}", 
                response.getStatusCode(), response.getBody());
            
            // 7. 응답 파싱
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (isSuccess(root)) {
                String docNo = extractDocumentNo(root);
                log.info("[이카운트 전표 등록 성공] docNo={}", docNo);
                return docNo;
            } else {
                String errorMsg = extractErrorMessage(root);
                log.error("[이카운트 전표 등록 실패] error={}", errorMsg);
                
                // 세션 만료 시 재시도
                if (isSessionExpired(root)) {
                    sessionService.invalidateSession(tenantId);
                    log.info("[세션 만료 - 재시도]");
                    return callEcountApi(tenantId, credentialsJson, payloadJson);
                }
                
                throw new ErpApiException("ECOUNT", extractErrorCode(root), errorMsg);
            }
            
        } catch (ErpApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[이카운트 API 호출 실패]", e);
            throw new ErpApiException("ECOUNT", "API_ERROR", 
                "이카운트 API 호출 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * API 응답 성공 여부 확인
     * 
     * 이카운트 API 응답 형식:
     * - 성공: {"Status": "200", "Data": {"SuccessCnt": 1, "FailCnt": 0, ...}}
     * - 실패: {"Status": "500", "Errors": [...]}
     */
    private boolean isSuccess(JsonNode root) {
        // 1. Status 확인 (문자열 "200"이 성공)
        JsonNode status = root.path("Status");
        if (!status.isMissingNode()) {
            String statusStr = status.asText();
            if (!"200".equals(statusStr)) {
                return false;
            }
        }
        
        // 2. Data.SuccessCnt 확인 (최소 1건 이상 성공해야 함)
        JsonNode data = root.path("Data");
        if (!data.isMissingNode()) {
            int successCnt = data.path("SuccessCnt").asInt(0);
            int failCnt = data.path("FailCnt").asInt(0);
            
            // 성공 건수가 1 이상이고, 실패 건수가 0이면 성공
            return successCnt > 0 && failCnt == 0;
        }
        
        return false;
    }
    
    /**
     * 전표 번호 추출
     * 
     * 이카운트 API 응답 형식:
     * {"Data": {"SlipNos": ["20260116-13"]}}
     */
    private String extractDocumentNo(JsonNode root) {
        JsonNode data = root.path("Data");
        if (!data.isMissingNode()) {
            // SlipNos 배열에서 첫 번째 전표번호 추출
            JsonNode slipNos = data.path("SlipNos");
            if (slipNos.isArray() && slipNos.size() > 0) {
                return slipNos.get(0).asText();
            }
        }
        
        log.warn("[전표번호 추출 실패] 응답에서 SlipNos를 찾을 수 없습니다");
        return "UNKNOWN";
    }
    
    /**
     * 오류 메시지 추출
     * 
     * 이카운트 API 오류 형식:
     * {"Errors": [{"Code": "EXP00001", "Message": "데이터 입력에 오류가 있습니다"}]}
     * 또는
     * {"Error": {"Code": 0, "Message": "오류 메시지"}}
     */
    private String extractErrorMessage(JsonNode root) {
        // 1. Errors 배열 확인
        JsonNode errors = root.path("Errors");
        if (errors.isArray() && errors.size() > 0) {
            JsonNode firstError = errors.get(0);
            String message = firstError.path("Message").asText();
            String code = firstError.path("Code").asText();
            return String.format("[%s] %s", code, message);
        }
        
        // 2. Error 객체 확인
        JsonNode error = root.path("Error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("Message").asText();
            if (!message.isEmpty()) {
                return message;
            }
        }
        
        // 3. Data.ResultDetails 확인 (상세 오류)
        JsonNode data = root.path("Data");
        if (!data.isMissingNode()) {
            JsonNode resultDetails = data.path("ResultDetails");
            if (resultDetails.isArray() && resultDetails.size() > 0) {
                JsonNode firstDetail = resultDetails.get(0);
                if (!firstDetail.path("IsSuccess").asBoolean(true)) {
                    return firstDetail.path("TotalError").asText("상세 오류 정보 없음");
                }
            }
        }
        
        return "알 수 없는 오류";
    }
    
    /**
     * 오류 코드 추출
     */
    private String extractErrorCode(JsonNode root) {
        // Status를 문자열로 반환 (이카운트는 "200", "500" 등 문자열 사용)
        JsonNode status = root.path("Status");
        if (!status.isMissingNode()) {
            return status.asText();
        }
        return "UNKNOWN";
    }
    
    /**
     * 세션 만료 여부 확인
     */
    private boolean isSessionExpired(JsonNode root) {
        String errorCode = extractErrorCode(root);
        return "401".equals(errorCode) || "SESSION_EXPIRED".equals(errorCode);
    }

    @Override
    public String getDocument(String erpDocumentNo, String credentials) {
        log.info("[Mock] 이카운트 전표 조회: erpDocNo={}", erpDocumentNo);

        // Mock: 전표 상태 정보
        return String.format("{\"erpDocNo\":\"%s\",\"status\":\"POSTED\",\"postedAt\":\"2026-01-12T10:00:00\"}", 
            erpDocumentNo);
    }

    @Override
    public boolean testConnection(String credentials) {
        log.info("[Mock] 이카운트 인증 테스트");
        // Mock: 항상 성공
        return true;
    }

    @Override
    public Integer getRemainingQuota() {
        // Mock: 이카운트는 rate limit 1000/hour
        return 980;
    }
}
