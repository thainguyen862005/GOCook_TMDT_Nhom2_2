package com.james.LMS.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
    @NotBlank(message = "Nhập tên gợi nhớ địa chỉ")
    @Size(max = 100, message = "Tên gợi nhớ tối đa 100 ký tự") String label,
    @NotBlank(message = "Nhập tên người nhận")
    @Size(max = 255, message = "Tên người nhận tối đa 255 ký tự") String recipientName,
    @NotBlank(message = "Nhập số điện thoại")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    @Pattern(regexp = "^\\+?[0-9][0-9 ().-]{6,28}[0-9]$", message = "Số điện thoại không hợp lệ") String phone,
    @NotBlank(message = "Nhập số nhà, tên đường")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String addressLine,
    @Size(max = 100, message = "Phường/xã tối đa 100 ký tự") String ward,
    @NotBlank(message = "Nhập quận/huyện")
    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự") String district,
    @NotBlank(message = "Nhập tỉnh/thành phố")
    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự") String city,
    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự") String note,
    boolean isDefault,
    @Min(value = 0, message = "Phiên bản địa chỉ không hợp lệ") Long version) {

  public CustomerAddressRequest {
    label = trim(label);
    recipientName = trim(recipientName);
    phone = trim(phone);
    addressLine = trim(addressLine);
    ward = trim(ward);
    district = trim(district);
    city = trim(city);
    note = trim(note);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
