package com.ecommerce.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkUploadResponseDTO {

    private int totalRows;
    private int successCount;
    private int failedCount;
    private List<String> errors;
}