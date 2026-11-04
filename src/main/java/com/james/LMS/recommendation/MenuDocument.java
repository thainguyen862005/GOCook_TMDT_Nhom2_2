package com.james.LMS.recommendation;

/** Published, active menu as read from existing GO Cook products/users tables. */
public record MenuDocument(
    long id, String name, String category, String dishItems, String description,
    String imageUrl, String unitPrice, int minGuests, int maxGuests, String sellerName) {
}
