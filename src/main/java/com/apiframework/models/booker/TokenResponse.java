package com.apiframework.models.booker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserializes the POST /auth response from the Restful-Booker API.
 *
 * WHY this exists:
 *   The /auth endpoint returns { "token": "abc123..." }. Mapping it to a typed POJO
 *   rather than using jsonPath().getString("token") makes the extraction refactor-safe
 *   and documents the expected response contract in code.
 *
 * HOW it is used:
 *   TokenResponse tr = response.as(TokenResponse.class);
 *   authToken = tr.getToken();   // stored for use in authenticatedSpec(authToken)
 *   @JsonIgnoreProperties(ignoreUnknown = true) silently drops any extra fields
 *   the API may add in the future without breaking deserialization.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {

    /** Session token used as a cookie value for authenticated operations. */
    private String token;
}
