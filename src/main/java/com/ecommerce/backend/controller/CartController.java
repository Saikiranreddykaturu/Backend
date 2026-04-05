package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.CartResponseDTO;
import com.ecommerce.backend.dto.UpdateQuantityDTO;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.service.CartService;
import com.ecommerce.backend.service.UserService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
public class CartController {


    private  final CartService cartService;

    private final UserService userService;
    public CartController(CartService cartService,UserService userService){
       this.cartService=cartService;
       this.userService=userService;
    }

    @PostMapping("/add")
    public CartResponseDTO addToCart(@RequestParam UUID productId,
                                     @RequestParam int quantity,
                                     Authentication authentication) {

        String email = authentication.getName();

        // fetch user using email
        User user = userService.findByEmail(email);

        return cartService.addToCart(user, productId, quantity);
    }
    @GetMapping
    public CartResponseDTO getCart(Authentication authentication) {

        String email = authentication.getName();
        User user = userService.findByEmail(email);

        return cartService.getCart(user);
    }
    @DeleteMapping("/remove")
    public CartResponseDTO removeItem(@RequestParam UUID productId,
                                      Authentication authentication) {

        String email = authentication.getName();
        User user = userService.findByEmail(email);

        return cartService.removeItem(user, productId);
    }
    @PutMapping("/update")
    public CartResponseDTO updateQuantity(@RequestBody UpdateQuantityDTO request,
                                          Authentication authentication) {

        String email = authentication.getName();
        User user = userService.findByEmail(email);

        return cartService.updateQuantity(
                user,
                request.getProductId(),
                request.getQuantity()
        );
    }
}