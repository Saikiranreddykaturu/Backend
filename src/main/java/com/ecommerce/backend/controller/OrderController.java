package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.OrderResponseDTO;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.service.OrderService;
import com.ecommerce.backend.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    public OrderController(OrderService orderService,UserService userService){
        this.orderService=orderService;
        this.userService=userService;
    }

    @PostMapping("/place")
    public OrderResponseDTO placeOrder(Authentication authentication) {

        String email = authentication.getName();
        User user = userService.findByEmail(email);

        return orderService.placeOrder(user);
    }
    @GetMapping
    public List<OrderResponseDTO> getOrders(Authentication authentication) {

        String email = authentication.getName();
        User user = userService.findByEmail(email);

        return orderService.getOrders(user);
    }
}
