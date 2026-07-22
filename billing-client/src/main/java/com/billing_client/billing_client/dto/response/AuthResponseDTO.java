package com.billing_client.billing_client.dto.response;


import com.billing_client.billing_client.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

    private String token;
    private String type;
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private UserRole role;
}