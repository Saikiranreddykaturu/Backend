package com.ecommerce.backend.controller;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
    public Page<ProductResponseDTO> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return productService.getProducts(page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public ProductResponseDTO getProduct(@PathVariable UUID id) {
        return productService.getProductById(id);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable UUID id){
        return ResponseEntity.ok("Product deleted Successfully") ;
    }
    @PutMapping("/{id}")
    public ProductResponseDTO updateProduct(@PathVariable UUID id,
                                            @Valid @RequestBody ProductRequestDTO request) {
        return productService.updateProduct(id, request);
    }
    @PatchMapping("/{id}")
    public ProductResponseDTO partialUpdate(@PathVariable UUID id,
                                            @RequestBody ProductUpdateDTO request) {
        return productService.partialUpdate(id, request);
    }

}