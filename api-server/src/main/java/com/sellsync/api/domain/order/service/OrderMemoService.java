package com.sellsync.api.domain.order.service;

import com.sellsync.api.domain.order.dto.CreateOrderMemoRequest;
import com.sellsync.api.domain.order.dto.OrderMemoDto;
import com.sellsync.api.domain.order.dto.UpdateOrderMemoRequest;
import com.sellsync.api.domain.order.entity.OrderMemo;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderMemoRepository;
import com.sellsync.api.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMemoService {

    private final OrderMemoRepository orderMemoRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 메모 목록 조회
     */
    @Transactional(readOnly = true)
    public List<OrderMemoDto> getMemosByOrderId(UUID orderId) {
        log.info("[주문 메모 조회] orderId={}", orderId);
        
        // 주문 존재 확인
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
        }
        
        List<OrderMemo> memos = orderMemoRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        return memos.stream()
                .map(OrderMemoDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 주문 메모 생성
     */
    @Transactional
    public OrderMemoDto createMemo(
            UUID orderId, 
            UUID tenantId, 
            UUID userId, 
            String userName,
            CreateOrderMemoRequest request
    ) {
        log.info("[주문 메모 생성] orderId={}, userId={}, userName={}", orderId, userId, userName);
        
        // 주문 존재 확인
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
        }
        
        OrderMemo memo = OrderMemo.builder()
                .orderId(orderId)
                .tenantId(tenantId)
                .userId(userId)
                .userName(userName)
                .content(request.getContent())
                .build();
        
        OrderMemo saved = orderMemoRepository.save(memo);
        log.info("[주문 메모 생성 완료] memoId={}", saved.getMemoId());
        
        return OrderMemoDto.from(saved);
    }

    /**
     * 주문 메모 수정
     */
    @Transactional
    public OrderMemoDto updateMemo(
            UUID memoId, 
            UUID userId,
            UpdateOrderMemoRequest request
    ) {
        log.info("[주문 메모 수정] memoId={}, userId={}", memoId, userId);
        
        OrderMemo memo = orderMemoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다: " + memoId));
        
        // 본인 작성 메모만 수정 가능
        if (!memo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인이 작성한 메모만 수정할 수 있습니다");
        }
        
        memo.setContent(request.getContent());
        OrderMemo updated = orderMemoRepository.save(memo);
        
        log.info("[주문 메모 수정 완료] memoId={}", memoId);
        return OrderMemoDto.from(updated);
    }

    /**
     * 주문 메모 삭제
     */
    @Transactional
    public void deleteMemo(UUID memoId, UUID userId) {
        log.info("[주문 메모 삭제] memoId={}, userId={}", memoId, userId);
        
        OrderMemo memo = orderMemoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다: " + memoId));
        
        // 본인 작성 메모만 삭제 가능
        if (!memo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인이 작성한 메모만 삭제할 수 있습니다");
        }
        
        orderMemoRepository.delete(memo);
        log.info("[주문 메모 삭제 완료] memoId={}", memoId);
    }
}
