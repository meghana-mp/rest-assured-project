package com.apiframework.models.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a GitHub repository — used for both list deserialization and single-repo responses.
 *
 * WHY dual-use (list + single)?
 *   GET /user/repos returns a JSON array; each element maps to a GitHubRepo.
 *   POST /user/repos and GET /repos/{owner}/{repo} return a single JSON object.
 *   The same POJO serves both because the JSON structure is identical.
 *
 * HOW list deserialization works:
 *   List<GitHubRepo> repos = response.jsonPath().getList(".", GitHubRepo.class);
 *   The root path "." tells Jackson the top-level element IS the array.
 *
 * HOW single deserialization works:
 *   GitHubRepo repo = response.as(GitHubRepo.class);
 *
 * WHY @JsonProperty("private")?
 *   "private" is a Java reserved keyword and cannot be used as a field name.
 *   The boolean field is named privateRepo in Java and annotated to map to "private" in JSON.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepo {

    private Long id;
    private String name;
    private String description;
    private String visibility;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("private")
    private Boolean privateRepo;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("clone_url")
    private String cloneUrl;

    @JsonProperty("ssh_url")
    private String sshUrl;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("pushed_at")
    private String pushedAt;
}
