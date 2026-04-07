package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.OrderResponseDTO;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Order;
import com.ecommerce.backend.entity.OrderStatus;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrderThrowsWhenCartIsEmpty() {
        User user = User.builder().id(UUID.randomUUID()).role(Role.USER).build();
        Cart cart = Cart.builder().user(user).items(new ArrayList<>()).build();

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        assertThrows(BadRequestException.class, () -> orderService.placeOrder(user));
    }

    @Test
    void placeOrderUpdatesStockClearsCartAndReturnsPlacedOrder() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .role(Role.USER)
                .build();
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Phone")
                .price(BigDecimal.valueOf(250))
                .stock(5)
                .createdAt(Instant.now())
                .build();
        CartItem cartItem = CartItem.builder()
                .product(product)
                .quantity(2)
                .build();
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .user(user)
                .items(new ArrayList<>(List.of(cartItem)))
                .build();
        cartItem.setCart(cart);

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.PLACED);
            return order;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDTO response = orderService.placeOrder(user);

        assertEquals(3, product.getStock());
        assertTrue(cart.getItems().isEmpty());
        assertEquals("PLACED", response.getStatus());
        assertEquals(BigDecimal.valueOf(500), response.getTotalAmount());

        ArgumentCaptor<List<Product>> productCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(productCaptor.capture());
        assertEquals(1, productCaptor.getValue().size());
        assertEquals(product.getId(), productCaptor.getValue().get(0).getId());
    }
}
