package com.james.LMS.media;

import java.io.IOException;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** Apply the same file policy to every public image workflow. */
public final class ImageFileValidator {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

  private ImageFileValidator() {}

  public static byte[] validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
      throw new IllegalArgumentException("Ảnh phải có dữ liệu và không vượt quá 5 MB.");
    }
    if (file.getContentType() == null || !ALLOWED.contains(file.getContentType().toLowerCase())) {
      throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.");
    }
    try {
      byte[] b = file.getBytes();
      if (b.length == 0 || b.length > MAX_BYTES) throw new IllegalArgumentException("Dung lượng ảnh không hợp lệ.");
      boolean jpeg = b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
      boolean png = b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71 && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
      boolean webp = b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
      String type = file.getContentType().toLowerCase();
      if (!(jpeg && type.equals("image/jpeg") || png && type.equals("image/png") || webp && type.equals("image/webp"))) {
        throw new IllegalArgumentException("Nội dung ảnh không khớp định dạng file.");
      }
      return b;
    } catch (IOException ex) {
      throw new IllegalArgumentException("Không thể đọc ảnh được chọn.", ex);
    }
  }
}
