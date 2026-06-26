package com.apiframework.models.platzi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Deserializes a Platzi Fake Store product from POST/GET /products responses.
 *
 * WHY Map<String, Object> for category?
 *   The nested "category" object { "id": 1, "name": "...", "image": "..." } is only
 *   referenced in TC_PF_004 to confirm the response is structurally valid. Mapping it
 *   to Map<String, Object> avoids creating a separate Category class for one test assertion.
 *   If category fields need direct typed access in future tests, extract a Category POJO.
 *
 * WHY List<String> for images?
 *   The API contract requires at least one image URL per product. Using List<String>
 *   allows asserting images.size() >= 1 and iterating over URLs without extra parsing.
 *
 * HOW it is used:
 *   Product p = response.as(Product.class);
 *   assertNotNull(p.getId());
 *   assertTrue(p.getPrice() >= 0);
 *   assertFalse(p.getImages().isEmpty());
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {

    private Integer id;
    private String title;
    private Integer price;
    private String description;
    private List<String> images;

    /** Nested category object returned by the API. */
    private Map<String, Object> category;
}
