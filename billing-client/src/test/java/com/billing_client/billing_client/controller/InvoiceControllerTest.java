package com.billing_client.billing_client.controller;


import com.billing_client.billing_client.dto.request.InvoiceItemRequestDTO;
import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceItemResponseDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;
import com.billing_client.billing_client.exception.InvoiceNotFoundException;
import com.billing_client.billing_client.model.enums.InvoiceStatus;
import com.billing_client.billing_client.model.enums.InvoiceType;
import com.billing_client.billing_client.model.enums.VatRate;
import com.billing_client.billing_client.security.JwtAuthFilter;
import com.billing_client.billing_client.security.JwtService;
import com.billing_client.billing_client.security.SecurityConfig;
import com.billing_client.billing_client.service.InvoiceService;
import com.billing_client.billing_client.service.impl.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
@DisplayName("InvoiceController Tests")
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private ObjectMapper objectMapper;
    private InvoiceRequestDTO validRequest;
    private InvoiceResponseDTO approvedResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        validRequest = buildValidRequest();
        approvedResponse = buildApprovedResponse();
    }

    @Test
    @DisplayName("Should return 201 and approved invoice when request is valid")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn201_whenRequestIsValid() throws Exception {
        when(invoiceService.processInvoice(any())).thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cae").value("43969379396831"))
                .andExpect(jsonPath("$.externalInvoiceId").value("test-uuid-1234"));
    }

    @Test
    @DisplayName("Should return 400 when issuer CUIT format is invalid")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenIssuerCuitIsInvalid() throws Exception {
        validRequest.setIssuerCuit("bad-cuit");

        mockMvc.perform(post("/api/v1/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.issuerCuit").exists());
    }

    @Test
    @DisplayName("Should return 400 when items list is empty")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenItemsListIsEmpty() throws Exception {
        validRequest.setItems(List.of());

        mockMvc.perform(post("/api/v1/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    @DisplayName("Should return 400 when totalAmount is null")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenTotalAmountIsNull() throws Exception {
        validRequest.setTotalAmount(null);

        mockMvc.perform(post("/api/v1/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.totalAmount").exists());
    }


    @Test
    @DisplayName("Should return 200 and invoice when ID exists")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200_whenInvoiceIdExists() throws Exception {
        when(invoiceService.findById(1L)).thenReturn(approvedResponse);

        mockMvc.perform(get("/api/v1/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Should return 404 when invoice ID does not exist")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404_whenInvoiceIdDoesNotExist() throws Exception {
        when(invoiceService.findById(99L))
                .thenThrow(new InvoiceNotFoundException("Invoice not found with id: 99"));

        mockMvc.perform(get("/api/v1/invoices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invoice Not Found"));
    }

    @Test
    @DisplayName("Should return 200 and list of invoices")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200_withListOfInvoices() throws Exception {
        when(invoiceService.findAll()).thenReturn(List.of(approvedResponse));

        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("Should return 200 when externalInvoiceId exists")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200_whenExternalInvoiceIdExists() throws Exception {
        when(invoiceService.findByExternalInvoiceId("test-uuid-1234"))
                .thenReturn(approvedResponse);

        mockMvc.perform(get("/api/v1/invoices/external/test-uuid-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalInvoiceId").value("test-uuid-1234"));
    }

    private InvoiceRequestDTO buildValidRequest() {
        return InvoiceRequestDTO.builder()
                .invoiceType(InvoiceType.INVOICE_B)
                .issueDate(LocalDate.of(2026, 5, 19))
                .pointOfSale(1)
                .issuerCuit("30-71234567-0")
                .issuerBusinessName("Mi Empresa SRL")
                .issuerAddress("Av. Corrientes 1234, CABA")
                .receiverCuit("20-12345678-9")
                .receiverBusinessName("Cliente SA")
                .receiverAddress("Av. Santa Fe 5678, CABA")
                .items(List.of(
                        InvoiceItemRequestDTO.builder()
                                .description("Servicio de desarrollo")
                                .quantity(new BigDecimal("1"))
                                .unitPrice(new BigDecimal("1000.00"))
                                .vatRate(VatRate.VAT_21)
                                .build()
                ))
                .netAmount(new BigDecimal("1000.00"))
                .vatAmount(new BigDecimal("210.00"))
                .totalAmount(new BigDecimal("1210.00"))
                .build();
    }

    private InvoiceResponseDTO buildApprovedResponse() {
        return InvoiceResponseDTO.builder()
                .id(1L)
                .externalInvoiceId("test-uuid-1234")
                .invoiceType(InvoiceType.INVOICE_B)
                .issueDate(LocalDate.of(2026, 5, 19))
                .issuerCuit("30-71234567-0")
                .issuerBusinessName("Mi Empresa SRL")
                .receiverCuit("20-12345678-9")
                .receiverBusinessName("Cliente SA")
                .netAmount(new BigDecimal("1000.00"))
                .vatAmount(new BigDecimal("210.00"))
                .totalAmount(new BigDecimal("1210.00"))
                .status(InvoiceStatus.APPROVED)
                .cae("43969379396831")
                .caeExpirationDate(LocalDate.of(2026, 6, 4))
                .afipInvoiceNumber(2799044L)
                .afipMessage("Invoice authorized successfully by AFIP.")
                .items(List.of(
                        InvoiceItemResponseDTO.builder()
                                .id(1L)
                                .description("Servicio de desarrollo")
                                .quantity(new BigDecimal("1"))
                                .unitPrice(new BigDecimal("1000.00"))
                                .vatRate(VatRate.VAT_21)
                                .subtotal(new BigDecimal("1000.00"))
                                .build()
                ))
                .createdAt(LocalDateTime.of(2026, 5, 19, 12, 0))
                .build();
    }
}
