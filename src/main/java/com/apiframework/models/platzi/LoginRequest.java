package com.apiframework.models.platzi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /auth/login on the Platzi Fake Store API.
 *
 * WHY email + password (not username)?
 *   Platzi uses an email-based identity model, unlike Restful-Booker which uses username.
 *   Keeping these as separate POJOs avoids confusion and documents each API's contract.
 *
 * HOW it is used:
 *   LoginRequest body = LoginRequest.builder()
 *       .email(cfg.getProperty("platzi.email", "john@mail.com"))
 *       .password(cfg.getProperty("platzi.password", "changeme"))
 *       .build();
 *   given().body(body).post("/auth/login");
 *   Jackson serializes → { "email": "john@mail.com", "password": "changeme" }
 *   On success, the API returns a JWT access_token and refresh_token (see LoginResponse).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    private String email;
    private String password;
}
