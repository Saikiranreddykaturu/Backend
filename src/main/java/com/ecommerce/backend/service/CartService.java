package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.CartItemDTO;
import com.ecommerce.backend.dto.CartResponseDTO;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.BadRequestException;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service
public class CartService{
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,ProductRepository productRepository){
        this.cartRepository=cartRepository;
        this.productRepository=productRepository;

    }
    public CartResponseDTO getCart(User user) {

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        return mapCart(cart);
    }
    public CartResponseDTO addToCart(User user, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than 0");
        }

    Cart cart = cartRepository.findByUser(user)
            .orElseGet(() -> cartRepository.save(
                    Cart.builder().user(user).build()
            ));

    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

    // Check if already exists
    Optional<CartItem> existingItem = cart.getItems()
            .stream()
            .filter(item -> item.getProduct().getId().equals(productId))
            .findFirst();

    if (existingItem.isPresent()) {
        CartItem item = existingItem.get();
        int updatedQuantity = item.getQuantity() + quantity;
        if (updatedQuantity > product.getStock()) {
            throw new BadRequestException("Requested quantity exceeds available stock");
        }
        item.setQuantity(updatedQuantity);
    } else {
        if (quantity > product.getStock()) {
            throw new BadRequestException("Requested quantity exceeds available stock");
        }
        CartItem newItem = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .build();

        cart.getItems().add(newItem);
    }

        Cart savedCart = cartRepository.save(cart);
        return mapCart(savedCart);
    }
    public CartResponseDTO removeItem(User user, UUID productId) {

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getItems().removeIf(item ->
                item.getProduct().getId().equals(productId)
        );

        Cart updated = cartRepository.save(cart);

        return mapCart(updated);
    }

    public CartResponseDTO updateQuantity(User user, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than 0");
        }

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        if (quantity > item.getProduct().getStock()) {
            throw new BadRequestException("Requested quantity exceeds available stock");
        }
        item.setQuantity(quantity);

        Cart updated = cartRepository.save(cart);

        return mapCart(updated);
    }
    private CartItemDTO mapCartItem(CartItem item) {

        BigDecimal price = item.getProduct().getPrice();
        Integer quantity = item.getQuantity();

        return CartItemDTO.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .price(price)
                .quantity(quantity)
                .totalPrice(price.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }
    private CartResponseDTO mapCart(Cart cart) {

        List<CartItemDTO> items = cart.getItems()
                .stream()
                .map(this::mapCartItem)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(CartItemDTO::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponseDTO.builder()
                .cartId(cart.getId())
                .userId(cart.getUser().getId())
                .items(items)
                .totalPrice(totalPrice)
                .build();
    }
}
