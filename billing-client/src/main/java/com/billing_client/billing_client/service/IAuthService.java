package com.billing_client.billing_client.service;


import com.billing_client.billing_client.dto.request.LoginRequestDTO;
import com.billing_client.billing_client.dto.request.RegisterRequestDTO;
import com.billing_client.billing_client.dto.response.AuthResponseDTO;

public interface IAuthService {


    AuthResponseDTO login(LoginRequestDTO request);


    AuthResponseDTO register(RegisterRequestDTO request);
}