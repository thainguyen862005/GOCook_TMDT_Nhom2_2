package com.james.LMS.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {
  private static final byte[] PNG = {(byte)137, 80, 78, 71, 13, 10, 26, 10};

  @Test void acceptsGenuinePng() {
    assertThat(ImageFileValidator.validate(new MockMultipartFile("image", "dish.png", "image/png", PNG)))
        .isEqualTo(PNG);
  }
  @Test void rejectsSpoofedPng() {
    assertThatThrownBy(() -> ImageFileValidator.validate(
        new MockMultipartFile("image", "dish.png", "image/png", "not-image".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);
  }
  @Test void rejectsWrongMimeForValidMagic() {
    assertThatThrownBy(() -> ImageFileValidator.validate(
        new MockMultipartFile("image", "dish.png", "image/jpeg", PNG)))
        .isInstanceOf(IllegalArgumentException.class);
  }
  @Test void rejectsEmptyAndUnsupported() {
    assertThatThrownBy(() -> ImageFileValidator.validate(
        new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ImageFileValidator.validate(
        new MockMultipartFile("image", "document.svg", "image/svg+xml", "<svg/>".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
