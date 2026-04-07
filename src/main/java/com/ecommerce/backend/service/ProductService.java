package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.*;
import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.exception.ResourceNotFoundException;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponseDTO createProduct(ProductRequestDTO request) {

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .createdAt(Instant.now())
                .build();

        Product saved = productRepository.save(product);

        return mapToDTO(saved);
    }


    public BulkUploadResponseDTO uploadProducts(MultipartFile file) {

        if (!file.getOriginalFilename().endsWith(".xlsx")) {
            throw new RuntimeException("Invalid file format. Only .xlsx allowed");
        }

        List<Product> validProducts = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                totalRows++;
                Row row = sheet.getRow(i);

                try {
                    String name = getStringCell(row.getCell(0));
                    String description = getStringCell(row.getCell(1));
                    Double price = getNumericCell(row.getCell(2));
                    Integer stock = getNumericCell(row.getCell(3)).intValue();

                    // 🔥 Validation
                    if (name == null || name.isBlank()) {
                        throw new RuntimeException("Name is empty");
                    }

                    if (price == null || price <= 0) {
                        throw new RuntimeException("Invalid price");
                    }

                    if (stock < 0) {
                        throw new RuntimeException("Stock cannot be negative");
                    }

                    Product product = Product.builder()
                            .name(name)
                            .description(description)
                            .price(BigDecimal.valueOf(price))
                            .stock(stock)
                            .createdAt(Instant.now())
                            .build();

                    validProducts.add(product);

                } catch (Exception e) {
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }

            // 🔥 Batch insert
            productRepository.saveAll(validProducts);

            return BulkUploadResponseDTO.builder()
                    .totalRows(totalRows)
                    .successCount(validProducts.size())
                    .failedCount(errors.size())
                    .errors(errors)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to process file");
        }
    }
    private String getStringCell(Cell cell) {
        if (cell == null) return null;
        return cell.getCellType() == CellType.STRING
                ? cell.getStringCellValue()
                : String.valueOf(cell.getNumericCellValue());
    }

    private Double getNumericCell(Cell cell) {
        if (cell == null) return null;
        return cell.getNumericCellValue();
    }

    public Page<ProductResponseDTO> getProducts(
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return productRepository.findAll(pageable)
                .map(this::mapToDTO);
    }

    public ProductResponseDTO getProductById(UUID id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return mapToDTO(product);
    }

    public void deleteProductById(UUID id){
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found");
        }
        productRepository.deleteById(id);

    }
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());

        Product updated = productRepository.save(product);

        return mapToDTO(updated);
    }
    public ProductResponseDTO partialUpdate(UUID id, ProductUpdateDTO request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getName() != null) {
            product.setName(request.getName());
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }

        Product updated = productRepository.save(product);

        return mapToDTO(updated);
    }

    private ProductResponseDTO mapToDTO(Product product) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .createdAt(product.getCreatedAt())
                .build();
    }
}