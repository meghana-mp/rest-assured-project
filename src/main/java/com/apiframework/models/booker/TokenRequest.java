package com.apiframework.models.booker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /auth on the Restful-Booker API.
 *
 * WHY this exists:
 *   Restful-Booker uses a session-token auth model. Clients POST credentials to /auth
 *   and receive a token string, which must then be passed as a Cookie on all write
 *   operations (PUT, PATCH, DELETE). This POJO captures those credentials.
 *
 * HOW it is used:
 *   TokenRequest body = TokenRequest.builder()
 *       .username(cfg.getProperty("booker.username", "admin"))
 *       .password(cfg.getProperty("booker.password", "password123"))
 *       .build();
 *   given().body(body).post("/auth");   // Jackson serializes → { "username":"admin","password":"..." }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequest {

    private String username;
    private String password;
}
