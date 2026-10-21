package com.james.LMS.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.james.LMS.service.impl.CloudinaryServiceImpl;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudinarySiteUploadTest {
  @Test void generatedSitePublicIdAndHttpsCdn() throws Exception {
    Cloudinary c = mock(Cloudinary.class);
    Uploader u = mock(Uploader.class);
    when(c.uploader()).thenReturn(u);
    when(u.upload(any(), anyMap())).thenReturn(Map.of(
        "secure_url", "https://res.cloudinary.com/demo/image/upload/site/banner.webp"));
    var saved = new CloudinaryServiceImpl(c).uploadSiteImage(new byte[] {1, 2});
    assertThat(saved.publicId()).startsWith("gocook/site/");
    assertThat(saved.url()).startsWith("https://res.cloudinary.com/");
  }

  @Test void rejectsUntrustedRemoteLocation() throws Exception {
    Cloudinary c = mock(Cloudinary.class);
    Uploader u = mock(Uploader.class);
    when(c.uploader()).thenReturn(u);
    when(u.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://evil.example/a.jpg"));
    assertThatThrownBy(() -> new CloudinaryServiceImpl(c).uploadSiteImage(new byte[] {1}))
        .isInstanceOf(MediaStorageUnavailableException.class);
  }
}
