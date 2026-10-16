package com.billing_client.billing_client.controller;


import com.billing_client.billing_client.dto.request.InvoiceItemRequestDTO;
import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceItemResponseDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;
import com.billing_client.billing_client.exception.InvoiceNotFoundException;
import com.billing_client.billing_client.model.enums.InvoiceStatus;
import com.billing_client.billing_client.model.enums.InvoiceType;
import com.billing_client.billing_client.model.enums.VatRate;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * JwtAuthFilter is NOT mocked on purpose: a mocked filter never calls
 * chain.doFilter(), so requests would stop before reaching the controller.
 * The real filter just passes through when there is no Authorization header,
 * and @WithMockUser provides the authenticated user.
 */
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
    @DisplayName("Should return 400 when receiver CUIT has a wrong check digit")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenReceiverCuitCheckDigitIsWrong() throws Exception {
        validRequest.setReceiverCuit("20-12345678-9"); // valid check digit is 6

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiverCuit").value("CUIT check digit is invalid."));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("Should return 400 when point of sale is missing")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenPointOfSaleIsMissing() throws Exception {
        validRequest.setPointOfSale(null);

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pointOfSale").value("Point of sale is required."));
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
    @DisplayName("Should return 400 when an item quantity is zero")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenItemQuantityIsZero() throws Exception {
        validRequest.getItems().get(0).setQuantity(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists());
    }

    @Test
    @DisplayName("Should return 400 when an item unit price has more than 2 decimals")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenUnitPriceHasMoreThanTwoDecimals() throws Exception {
        validRequest.getItems().get(0).setUnitPrice(new BigDecimal("10.555"));

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].unitPrice']").exists());
    }

    @Test
    @DisplayName("Should return 202 when AFIP is unreachable and the invoice stays PENDING")
    @WithMockUser(roles = "ACCOUNTANT")
    void shouldReturn202_whenInvoiceStaysPending() throws Exception {
        approvedResponse.setStatus(InvoiceStatus.PENDING);
        approvedResponse.setCae(null);
        when(invoiceService.processInvoice(any())).thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should return 400 when invoiceType is not a valid enum value")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenInvoiceTypeIsUnknown() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest)
                .replace("\"INVOICE_B\"", "\"INVOICE_X\"");

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed Request"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceType")))
                .andExpect(jsonPath("$.message").value(containsString("INVOICE_A")));
    }

    @Test
    @DisplayName("Should return 400 when request body is not valid JSON")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed Request"));
    }

    @Test
    @DisplayName("Should return 400 when invoice ID is not a number")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400_whenInvoiceIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 401 when request has no token")
    @WithAnonymousUser
    void shouldReturn401_whenRequestIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(invoiceService);
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
    @DisplayName("Should return 403 when an ACCOUNTANT lists all invoices")
    @WithMockUser(roles = "ACCOUNTANT")
    void shouldReturn403_whenAccountantListsAllInvoices() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(invoiceService);
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
                .issuerCuit("30-71234567-1")
                .issuerBusinessName("Mi Empresa SRL")
                .issuerAddress("Av. Corrientes 1234, CABA")
                .receiverCuit("20-12345678-6")
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
                .build();
    }

    private InvoiceResponseDTO buildApprovedResponse() {
        return InvoiceResponseDTO.builder()
                .id(1L)
                .externalInvoiceId("test-uuid-1234")
                .invoiceType(InvoiceType.INVOICE_B)
                .issueDate(LocalDate.of(2026, 5, 19))
                .issuerCuit("30-71234567-1")
                .issuerBusinessName("Mi Empresa SRL")
                .receiverCuit("20-12345678-6")
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
