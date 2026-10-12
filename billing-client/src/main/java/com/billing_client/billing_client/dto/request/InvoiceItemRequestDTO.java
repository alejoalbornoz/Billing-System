package com.billing_client.billing_client.dto.request;

import com.billing_client.billing_client.model.enums.VatRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    // @Digits matches the DB columns (precision/scale), so nothing is silently rounded on save

    @NotNull(message = "Quantity is required.")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero.")
    @Digits(integer = 8, fraction = 2, message = "Quantity can have up to 8 integer digits and 2 decimals.")
    private BigDecimal quantity;

    @NotNull(message = "Unit price is required.")
    @DecimalMin(value = "0.00", message = "Unit price cannot be negative.")
    @Digits(integer = 13, fraction = 2, message = "Unit price can have up to 13 integer digits and 2 decimals.")
    private BigDecimal unitPrice;

    @NotNull(message = "VAT rate is required.")
    private VatRate vatRate;
}
