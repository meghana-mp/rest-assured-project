package com.apiframework.models.platzi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /auth/refresh-token on the Platzi Fake Store API.
 *
 * WHY refresh tokens matter:
 *   JWT access tokens are short-lived by design. When one expires, the client sends the
 *   longer-lived refresh token to /auth/refresh-token and receives a new access token
 *   without requiring the user to re-enter credentials. TC_PF_005 demonstrates this flow.
 *
 * HOW it is used:
 *   RefreshTokenRequest body = RefreshTokenRequest.builder()
 *       .refreshToken(refreshToken)   // stored from TC_PF_001 login
 *       .build();
 *   given().body(body).post("/auth/refresh-token");
 *   Jackson serializes → { "refreshToken": "eyJ..." }
 *   The response contains a new access_token validated as a 3-segment JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    private String refreshToken;
}
