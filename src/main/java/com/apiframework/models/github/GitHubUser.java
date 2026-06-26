package com.apiframework.models.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserializes the GET /user response from the GitHub API.
 *
 * WHY @JsonProperty on snake_case fields?
 *   GitHub returns JSON keys in snake_case (avatar_url, html_url, public_repos, etc.)
 *   but Java convention uses camelCase. @JsonProperty bridges the naming mismatch
 *   without requiring a global ObjectMapper naming-strategy change that could affect
 *   other deserialization targets in the project.
 *
 * WHY @JsonIgnoreProperties(ignoreUnknown = true)?
 *   The GitHub User object has 30+ fields. Declaring all of them would bloat this class.
 *   Only the fields relevant to test assertions are declared; all others are silently
 *   ignored. This also future-proofs the POJO against GitHub adding new fields.
 *
 * HOW it is used:
 *   GitHubUser user = response.as(GitHubUser.class);
 *   assertNotNull(user.getLogin());   // verify identity
 *   assertNotNull(user.getAvatarUrl());
 *   githubLogin = user.getLogin();    // cached for DELETE /repos/{owner}/{repo}
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUser {

    private String login;
    private Long id;
    private String name;
    private String email;
    private String bio;
    private String company;
    private String location;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("type")
    private String type;

    @JsonProperty("site_admin")
    private Boolean siteAdmin;

    @JsonProperty("public_repos")
    private Integer publicRepos;

    @JsonProperty("public_gists")
    private Integer publicGists;

    @JsonProperty("followers")
    private Integer followers;

    @JsonProperty("following")
    private Integer following;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
