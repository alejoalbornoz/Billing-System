package com.billing_client.billing_client.dto.request;

import com.billing_client.billing_client.model.enums.VatRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemRequestDTO {

    @NotBlank(message = "Item description is required.")
    private String description;

    @NotNull(message = "Quantity is required.")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero.")
    private BigDecimal quantity;

    @NotNull(message = "Unit price is required.")
    @DecimalMin(value = "0.00", message = "Unit price cannot be negative.")
    private BigDecimal unitPrice;

    @NotNull(message = "VAT rate is required.")
    private VatRate vatRate;
}
