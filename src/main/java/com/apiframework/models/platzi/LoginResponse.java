package com.apiframework.models.platzi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserializes the JWT token pair returned by POST /auth/login and POST /auth/refresh-token.
 *
 * WHY @JsonProperty on both fields?
 *   The Platzi API returns snake_case keys: "access_token" and "refresh_token".
 *   Java convention requires camelCase, so @JsonProperty maps the JSON key to the
 *   Java field name. Without this, Jackson would fail to populate the fields and
 *   both would remain null after deserialization.
 *
 * HOW it is used:
 *   LoginResponse lr = response.as(LoginResponse.class);
 *   accessToken  = lr.getAccessToken();   // stored for Bearer header in subsequent requests
 *   refreshToken = lr.getRefreshToken();  // stored for TC_PF_005 token renewal test
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;
}
