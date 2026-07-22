package com.billing_client.billing_client.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "Email is required.")
    @Email(message = "Email format is invalid.")
    private String email;

    @NotBlank(message = "Password is required.")
    private String password;
}