package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.OrderItemDTO;
import com.ecommerce.backend.dto.OrderResponseDTO;
import com.ecommerce.backend.entity.*;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(CartRepository cartRepository,
                        ProductRepository productRepository,
                        OrderRepository orderRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponseDTO placeOrder(User user) {
        logger.info("Placing order for user {}", user.getEmail());

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Order order = Order.builder()
                .user(user)
                .createdAt(Instant.now())
                .status(OrderStatus.PLACED)
                .build();

        List<Product> updatedProducts = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {

            Product product = cartItem.getProduct();

            // 🔥 Stock check
            if (product.getStock() < cartItem.getQuantity()) {
                logger.warn("Insufficient stock for product {} while ordering for user {}", product.getName(), user.getEmail());
                throw new BadRequestException("Not enough stock for " + product.getName());
            }

            // 🔥 Reduce stock
            product.setStock(product.getStock() - cartItem.getQuantity());
            updatedProducts.add(product);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(product.getPrice())
                    .build();

            order.getItems().add(orderItem);

        }

        // Save order

        productRepository.saveAll(updatedProducts);
        Order saved = orderRepository.save(order);
        cart.getItems().clear();
        cartRepository.save(cart);
        logger.info("Placed order {} for user {}", saved.getId(), user.getEmail());

        return mapOrder(saved);
    }
    private OrderItemDTO mapOrderItem(OrderItem item) {
        return OrderItemDTO.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .build();
    }

    private OrderResponseDTO mapOrder(Order order) {

        List<OrderItemDTO> items = order.getItems()
                .stream()
                .map(this::mapOrderItem)
                .toList();

        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponseDTO.builder()
                .orderId(order.getId())
                .createdAt(order.getCreatedAt())
                .status(order.getStatus().name())
                .items(items)
                .totalAmount(total)
                .build();
    }
    public List<OrderResponseDTO> getOrders(User user) {
        logger.debug("Fetching orders for user {}", user.getEmail());

        return orderRepository.findByUser(user)
                .stream()
                .map(this::mapOrder)
                .toList();
    }
}
