package com.james.LMS.response;

import com.james.LMS.entity.CustomerAddress;

public record CustomerAddressResponse(
    Long id, String label, String recipientName, String phone, String addressLine,
    String ward, String district, String city, String note, boolean isDefault,
    Long version, Long createdAt, Long updatedAt) {
  public static CustomerAddressResponse from(CustomerAddress address) {
    return new CustomerAddressResponse(address.getId(), address.getLabel(),
        address.getRecipientName(), address.getPhone(), address.getAddressLine(),
        address.getWard(), address.getDistrict(), address.getCity(), address.getNote(),
        address.isDefaultAddress(), address.getVersion(), address.getCreatedAt(), address.getUpdatedAt());
  }
}
