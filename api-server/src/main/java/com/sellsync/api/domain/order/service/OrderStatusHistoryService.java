package com.sellsync.api.domain.order.service;

import com.sellsync.api.domain.order.dto.OrderStatusHistoryDto;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderStatusHistory;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.order.repository.OrderStatusHistoryRepository;
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
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository historyRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 상태 변경 이력 조회
     */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryDto> getHistoryByOrderId(UUID orderId) {
        log.info("[주문 상태 이력 조회] orderId={}", orderId);
        
        // 주문 존재 확인
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
        }
        
        List<OrderStatusHistory> histories = historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        return histories.stream()
                .map(OrderStatusHistoryDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 주문 상태 변경 이력 기록 (시스템)
     */
    @Transactional
    public void recordStatusChange(
            UUID orderId,
            UUID tenantId,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String note
    ) {
        log.info("[주문 상태 변경 이력 기록 (시스템)] orderId={}, from={}, to={}", orderId, fromStatus, toStatus);
        
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .tenantId(tenantId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBySystem(true)
                .note(note)
                .build();
        
        historyRepository.save(history);
    }

    /**
     * 주문 상태 변경 이력 기록 (사용자)
     */
    @Transactional
    public void recordStatusChange(
            UUID orderId,
            UUID tenantId,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            UUID userId,
            String userName,
            String note
    ) {
        log.info("[주문 상태 변경 이력 기록 (사용자)] orderId={}, from={}, to={}, userId={}", 
                orderId, fromStatus, toStatus, userId);
        
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .tenantId(tenantId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBySystem(false)
                .changedByUserId(userId)
                .changedByUserName(userName)
                .note(note)
                .build();
        
        historyRepository.save(history);
    }

    /**
     * 최초 주문 생성 시 이력 기록
     */
    @Transactional
    public void recordInitialStatus(UUID orderId, UUID tenantId, OrderStatus initialStatus) {
        log.info("[최초 주문 상태 이력 기록] orderId={}, status={}", orderId, initialStatus);
        
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .tenantId(tenantId)
                .fromStatus(null)
                .toStatus(initialStatus)
                .changedBySystem(true)
                .note("주문 수집")
                .build();
        
        historyRepository.save(history);
    }
}
