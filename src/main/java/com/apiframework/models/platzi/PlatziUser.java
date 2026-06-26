package com.apiframework.models.platzi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserializes the authenticated user profile returned by GET /auth/profile.
 *
 * WHY no @JsonProperty annotations?
 *   The Platzi profile endpoint returns fields whose JSON keys already match Java
 *   camelCase conventions (id, name, email, role, avatar). Jackson's default field
 *   mapping handles these without explicit annotations.
 *
 * HOW it is used:
 *   PlatziUser user = response.as(PlatziUser.class);
 *   assertNotNull(user.getId());     // verify the token resolved to a real account
 *   assertNotNull(user.getEmail());  // verify identity fields are populated
 *   The "role" field (e.g. "customer", "admin") can be used in future role-based
 *   authorization tests.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatziUser {

    private Integer id;
    private String name;
    private String email;
    private String role;
    private String avatar;
}
