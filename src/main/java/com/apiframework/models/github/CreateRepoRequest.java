package com.apiframework.models.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /user/repos — creates a new GitHub repository.
 *
 * WHY @JsonProperty("private") and @JsonProperty("auto_init")?
 *   "private" is a Java reserved keyword so the field is named privateRepo.
 *   "auto_init" follows snake_case which violates Java naming conventions, so
 *   autoInit is used. Both annotations tell Jackson to use the API's expected
 *   key names when serializing the POJO to JSON.
 *
 * HOW it is used:
 *   CreateRepoRequest body = CreateRepoRequest.builder()
 *       .name("my-repo")
 *       .description("...")
 *       .privateRepo(false)
 *       .autoInit(true)
 *       .build();
 *   given().body(body).post("/user/repos");
 *   Jackson serializes → { "name":"my-repo", "private":false, "auto_init":true, ... }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRepoRequest {

    /** Repository name — must be unique under the authenticated user's account. */
    private String name;

    private String description;

    /** true = private, false = public. */
    @JsonProperty("private")
    private Boolean privateRepo;

    /** Initialises the repo with a README so it's non-empty on creation. */
    @JsonProperty("auto_init")
    private Boolean autoInit;
}
