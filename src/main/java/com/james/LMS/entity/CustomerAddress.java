package com.james.LMS.entity;

import com.james.LMS.request.CustomerAddressRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_addresses")
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  private Long userId;
  @Column(nullable = false, length = 100)
  private String label;
  @Column(name = "recipient_name", nullable = false, length = 255)
  private String recipientName;
  @Column(nullable = false, length = 30)
  private String phone;
  @Column(name = "address_line", nullable = false, length = 500)
  private String addressLine;
  @Column(length = 100)
  private String ward;
  @Column(nullable = false, length = 100)
  private String district;
  @Column(nullable = false, length = 100)
  private String city;
  @Column(columnDefinition = "TEXT")
  private String note;
  @Column(name = "is_default", nullable = false)
  private boolean defaultAddress;

  public void update(CustomerAddressRequest request) {
    label = request.label();
    recipientName = request.recipientName();
    phone = request.phone();
    addressLine = request.addressLine();
    ward = request.ward();
    district = request.district();
    city = request.city();
    note = request.note();
  }

  public void changeDefault(boolean value) {
    defaultAddress = value;
  }

  public void deactivate() {
    defaultAddress = false;
    isActive = false;
  }
}
