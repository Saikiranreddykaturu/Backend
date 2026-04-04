package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.*;
import com.ecommerce.backend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ProductResponseDTO createProduct(@Valid @RequestBody ProductRequestDTO request) {
        return productService.createProduct(request);
    }

    @GetMapping
    public List<ProductResponseDTO> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ProductResponseDTO getProduct(@PathVariable UUID id) {
        return productService.getProductById(id);
    }
}