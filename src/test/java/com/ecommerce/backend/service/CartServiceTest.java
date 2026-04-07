package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.CartResponseDTO;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void addToCartThrowsWhenRequestedQuantityExceedsStock() {
        User user = User.builder().id(UUID.randomUUID()).role(Role.USER).build();
        UUID productId = UUID.randomUUID();
        Cart cart = Cart.builder().user(user).items(new ArrayList<>()).build();
        Product product = Product.builder()
                .id(productId)
                .name("Phone")
                .price(BigDecimal.TEN)
                .stock(2)
                .build();

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> cartService.addToCart(user, productId, 3));
    }

    @Test
    void updateQuantityUpdatesExistingCartItemWithinStock() {
        User user = User.builder().id(UUID.randomUUID()).role(Role.USER).build();
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Phone")
                .price(BigDecimal.valueOf(100))
                .stock(5)
                .build();
        CartItem cartItem = CartItem.builder()
                .product(product)
                .quantity(1)
                .build();
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .user(user)
                .items(new ArrayList<>(List.of(cartItem)))
                .build();
        cartItem.setCart(cart);

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDTO response = cartService.updateQuantity(user, productId, 4);

        assertEquals(4, cartItem.getQuantity());
        assertEquals(BigDecimal.valueOf(400), response.getTotalPrice());
    }
}
