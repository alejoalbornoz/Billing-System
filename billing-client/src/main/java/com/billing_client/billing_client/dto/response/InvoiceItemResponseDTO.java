package com.billing_client.billing_client.dto.response;

import com.billing_client.billing_client.model.enums.VatRate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItemResponseDTO {

    private Long id;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private VatRate vatRate;
    private BigDecimal subtotal;
}
