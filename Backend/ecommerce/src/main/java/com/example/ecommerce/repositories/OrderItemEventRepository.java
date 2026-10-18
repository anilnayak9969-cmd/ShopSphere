package com.example.ecommerce.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entites.OrderItemEvent;

public interface OrderItemEventRepository extends JpaRepository<OrderItemEvent, Long> {

    List<OrderItemEvent> findByOrderItemOrderItemIdInOrderByCreatedAtAscEventIdAsc(Collection<Long> orderItemIds);
}
