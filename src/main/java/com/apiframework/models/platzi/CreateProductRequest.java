package com.apiframework.models.platzi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /products on the Platzi Fake Store API.
 *
 * WHY categoryId (Integer) instead of a nested Category object?
 *   The Platzi API only requires a category ID in the request body; the full category
 *   object is returned in the response. Sending just the ID keeps the request minimal
 *   and matches the API contract exactly.
 *
 * WHY List<String> for images?
 *   The API rejects requests with an empty images array. Using List.of("url") in the
 *   builder enforces at least one entry and allows future tests to supply multiple URLs
 *   without changing the POJO structure.
 *
 * HOW it is used:
 *   CreateProductRequest body = CreateProductRequest.builder()
 *       .title("Test Product — " + randomId())
 *       .price(149)
 *       .description("...")
 *       .categoryId(1)
 *       .images(List.of("https://picsum.photos/200/300"))
 *       .build();
 *   given().body(body).post("/products");
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    private String title;
    private Integer price;
    private String description;

    /** References an existing category by ID. */
    private Integer categoryId;

    /** At least one image URL must be provided. */
    private List<String> images;
}
