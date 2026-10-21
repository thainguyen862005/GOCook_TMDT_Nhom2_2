package com.james.LMS.response;

/** Lightweight public catalog projection. Price is serialized as a string to preserve precision. */
public record PublicMenuCardResponse(
    Long id,
    String name,
    String category,
    String unitPrice,
    Integer minGuests,
    Integer maxGuests,
    String dishItems,
    String imageUrl,
    String sellerName) {}
