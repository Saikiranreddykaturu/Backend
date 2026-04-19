package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUser(User user);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findWithItemsAndProductsByUser(User user);
}
