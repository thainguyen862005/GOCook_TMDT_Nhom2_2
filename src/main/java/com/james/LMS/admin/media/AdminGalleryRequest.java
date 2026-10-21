package com.james.LMS.admin.media;

import com.james.LMS.admin.dto.request.ProductRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminGalleryRequest(
    @NotNull @Valid ProductRequest product,
    @NotNull @Size(max = 10) List<String> imageKeys,
    String coverKey,
    @NotNull @Size(max = 10) List<String> imageAlts,
    Long version) {}
