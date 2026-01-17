package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 계산식 평가기
 * 
 * 필드 값을 사용한 계산식을 평가합니다.
 * 예: "order.totalPaymentAmount / item.quantity"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormulaEvaluator {
    
    private final FieldValueExtractor fieldValueExtractor;
    
    // 필드 참조 패턴 (예: order.buyerName, item.quantity)
    private static final Pattern FIELD_PATTERN = Pattern.compile("(order|item|mapping|erpItem)\\.[a-zA-Z][a-zA-Z0-9]*");
    
    // 문자열 리터럴 패턴 (예: "홍길동", '주문번호')
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'");
    
    /**
     * 계산식 평가
     * 
     * @param formula 계산식 (예: "order.totalPaymentAmount / item.quantity")
     * @param order 주문 정보
     * @return 계산 결과
     */
    public Object evaluate(String formula, Order order) {
        if (formula == null || formula.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 1. 필드 참조를 실제 값으로 치환
            String expression = replaceFieldReferences(formula, order);
            
            log.debug("[계산식 평가] formula={}, expression={}", formula, expression);
            
            // 2. 수식 계산
            return evaluateExpression(expression);
            
        } catch (Exception e) {
            log.error("[계산식 평가 실패] formula={}, error={}", formula, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 필드 참조를 실제 값으로 치환
     * 예: "order.totalPaymentAmount / item.quantity" 
     *  → "50000 / 2"
     * 예: "order.marketplaceOrderId + ' ' + order.buyerName"
     *  → "'ORD123' + ' ' + '홍길동'"
     */
    private String replaceFieldReferences(String formula, Order order) {
        Matcher matcher = FIELD_PATTERN.matcher(formula);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String fieldRef = matcher.group(); // 예: "order.totalPaymentAmount"
            Object value = extractFieldValue(fieldRef, order);
            
            String replacement;
            if (value == null) {
                replacement = "''"; // 빈 문자열
            } else if (value instanceof Number) {
                replacement = value.toString();
            } else {
                // 문자열은 따옴표로 감싸기
                replacement = "'" + value.toString().replace("'", "\\'") + "'";
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 필드 값 추출
     */
    private Object extractFieldValue(String fieldRef, Order order) {
        // fieldRef 예: "order.totalPaymentAmount", "item.quantity"
        // FieldValueExtractor의 extractValueByFieldRef 메서드 사용
        return fieldValueExtractor.extractValueByFieldRef(fieldRef, order);
    }
    
    /**
     * 수식 계산
     * 
     * 숫자 계산: "50000 / 2" → 25000
     * 문자열 연결: "'ORD123' + ' ' + '홍길동'" → "ORD123 홍길동"
     */
    private Object evaluateExpression(String expression) {
        try {
            // 공백 제거 (문자열 리터럴 안의 공백은 보존)
            expression = removeSpacesOutsideStrings(expression);
            
            // 문자열이 포함되어 있으면 문자열 연결로 처리
            if (expression.contains("'") || expression.contains("\"")) {
                return evaluateStringExpression(expression);
            }
            
            // 숫자만 있으면 수치 계산
            BigDecimal result = evaluateNumericExpression(expression);
            
            // 정수인 경우 Long으로, 소수인 경우 Double로 반환
            if (result.scale() <= 0) {
                return result.longValue();
            } else {
                return result.doubleValue();
            }
            
        } catch (Exception e) {
            log.error("[수식 계산 실패] expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }
    
    /**
     * 문자열 리터럴 밖의 공백만 제거
     */
    private String removeSpacesOutsideStrings(String expression) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        char stringDelimiter = 0;
        
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if ((c == '\'' || c == '"') && (i == 0 || expression.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
                result.append(c);
            } else if (c == ' ' && !inString) {
                // 문자열 밖의 공백은 제거
                continue;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 문자열 연결 평가
     * 예: "'ORD123' + ' ' + '홍길동'" → "ORD123 홍길동"
     */
    private String evaluateStringExpression(String expression) {
        StringBuilder result = new StringBuilder();
        
        // + 연산자로 분리 (문자열 리터럴 안의 +는 제외)
        List<String> parts = splitByPlusOperator(expression);
        
        for (String part : parts) {
            part = part.trim();
            
            // 문자열 리터럴인 경우 따옴표 제거
            if ((part.startsWith("'") && part.endsWith("'")) || 
                (part.startsWith("\"") && part.endsWith("\""))) {
                String str = part.substring(1, part.length() - 1);
                // 이스케이프 처리
                str = str.replace("\\'", "'").replace("\\\"", "\"");
                result.append(str);
            } else if (isNumeric(part)) {
                // 숫자는 그대로 추가
                result.append(part);
            } else {
                log.warn("[문자열 표현식 평가 실패] 알 수 없는 부분: {}", part);
            }
        }
        
        return result.toString();
    }
    
    /**
     * + 연산자로 분리 (문자열 리터럴 안의 +는 제외)
     */
    private List<String> splitByPlusOperator(String expression) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringDelimiter = 0;
        
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if ((c == '\'' || c == '"') && (i == 0 || expression.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
                current.append(c);
            } else if (c == '+' && !inString) {
                // 문자열 밖의 +는 구분자
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts;
    }
    
    /**
     * 숫자 문자열인지 확인
     */
    private boolean isNumeric(String str) {
        try {
            new BigDecimal(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 숫자 연산 평가
     * 
     * 연산자 우선순위:
     * 1. 괄호 ()
     * 2. 곱셈(*), 나눗셈(/)
     * 3. 덧셈(+), 뺄셈(-)
     */
    private BigDecimal evaluateNumericExpression(String expr) {
        // 괄호 처리
        while (expr.contains("(")) {
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            String subExpr = expr.substring(start + 1, end);
            BigDecimal subResult = evaluateNumericExpression(subExpr);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        
        // 덧셈, 뺄셈 처리 (낮은 우선순위)
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if ((c == '+' || c == '-') && i > 0) {
                String left = expr.substring(0, i);
                String right = expr.substring(i + 1);
                
                BigDecimal leftVal = evaluateNumericExpression(left);
                BigDecimal rightVal = evaluateNumericExpression(right);
                
                if (c == '+') {
                    return leftVal.add(rightVal);
                } else {
                    return leftVal.subtract(rightVal);
                }
            }
        }
        
        // 곱셈, 나눗셈 처리 (높은 우선순위)
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == '*' || c == '/') {
                String left = expr.substring(0, i);
                String right = expr.substring(i + 1);
                
                BigDecimal leftVal = new BigDecimal(left);
                BigDecimal rightVal = new BigDecimal(right);
                
                if (c == '*') {
                    return leftVal.multiply(rightVal);
                } else {
                    if (rightVal.compareTo(BigDecimal.ZERO) == 0) {
                        log.warn("[0으로 나누기] expression={}", expr);
                        return BigDecimal.ZERO;
                    }
                    return leftVal.divide(rightVal, 10, RoundingMode.HALF_UP);
                }
            }
        }
        
        // 숫자 변환
        return new BigDecimal(expr);
    }
    
    /**
     * 계산식 검증
     * 
     * @param formula 검증할 계산식
     * @return 유효한 계산식이면 true
     */
    public boolean validate(String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 필드 참조나 문자열 리터럴이 있는지 확인
            Matcher fieldMatcher = FIELD_PATTERN.matcher(formula);
            Matcher stringMatcher = STRING_LITERAL_PATTERN.matcher(formula);
            
            if (!fieldMatcher.find() && !stringMatcher.find()) {
                // 필드 참조도 문자열 리터럴도 없으면 순수 수식인지 확인
                evaluateNumericExpression(formula.replaceAll("\\s+", ""));
                return true;
            }
            
            // 지원하는 연산자와 문자만 있는지 확인
            // 문자열 리터럴을 제거한 후 검사
            String withoutStrings = formula.replaceAll("\"[^\"]*\"|'[^']*'", "");
            String operators = withoutStrings.replaceAll("[a-zA-Z0-9.\\s]", "");
            for (char c : operators.toCharArray()) {
                if (c != '+' && c != '-' && c != '*' && c != '/' && c != '(' && c != ')') {
                    log.warn("[지원하지 않는 연산자] operator={}, formula={}", c, formula);
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("[계산식 검증 실패] formula={}, error={}", formula, e.getMessage());
            return false;
        }
    }
}
