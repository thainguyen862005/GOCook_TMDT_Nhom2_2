package com.james.LMS.service;

import com.james.LMS.entity.CustomerAddress;
import com.james.LMS.entity.User;
import com.james.LMS.repository.CustomerAddressRepository;
import com.james.LMS.request.CustomerAddressRequest;
import com.james.LMS.response.CustomerAddressResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.james.LMS.exception.CustomerAddressException;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {
  private final CustomerAddressRepository addresses;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public List<CustomerAddressResponse> list(Long userId) {
    return active(userId).stream().map(CustomerAddressResponse::from).toList();
  }

  @Transactional
  public CustomerAddressResponse create(Long userId, CustomerAddressRequest request) {
    lockOwner(userId);
    List<CustomerAddress> existing = active(userId);
    boolean makeDefault = request.isDefault() || existing.isEmpty();
    if (makeDefault) clearDefaults(existing);
    CustomerAddress address = CustomerAddress.builder().userId(userId).build();
    address.update(request);
    address.changeDefault(makeDefault);
    return CustomerAddressResponse.from(addresses.saveAndFlush(address));
  }

  @Transactional
  public CustomerAddressResponse update(Long userId, Long id, CustomerAddressRequest request) {
    lockOwner(userId);
    CustomerAddress address = owned(userId, id);
    if (request.version() == null || !Objects.equals(request.version(), address.getVersion())) {
      throw new CustomerAddressException(HttpStatus.CONFLICT,
          "Địa chỉ đã thay đổi. Hãy tải lại danh sách trước khi sửa.");
    }
    address.update(request);
    if (request.isDefault() && !address.isDefaultAddress()) {
      clearDefaults(active(userId));
      address.changeDefault(true);
    }
    return CustomerAddressResponse.from(addresses.saveAndFlush(address));
  }

  @Transactional
  public void delete(Long userId, Long id) {
    lockOwner(userId);
    CustomerAddress address = owned(userId, id);
    boolean wasDefault = address.isDefaultAddress();
    address.deactivate();
    addresses.flush();
    if (wasDefault) {
      active(userId).stream().findFirst().ifPresent(next -> next.changeDefault(true));
      addresses.flush();
    }
  }

  @Transactional
  public CustomerAddressResponse setDefault(Long userId, Long id) {
    lockOwner(userId);
    CustomerAddress address = owned(userId, id);
    if (!address.isDefaultAddress()) {
      clearDefaults(active(userId));
      address.changeDefault(true);
      addresses.flush();
    }
    return CustomerAddressResponse.from(address);
  }

  private List<CustomerAddress> active(Long userId) {
    return addresses.findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId);
  }

  private CustomerAddress owned(Long userId, Long id) {
    return addresses.findByIdAndUserIdAndIsActiveTrue(id, userId)
        .orElseThrow(() -> new CustomerAddressException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ"));
  }

  private void lockOwner(Long userId) {
    User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    if (user == null || !user.isActive()) {
      throw new CustomerAddressException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
    }
  }

  private void clearDefaults(List<CustomerAddress> existing) {
    existing.stream().filter(CustomerAddress::isDefaultAddress)
        .forEach(address -> address.changeDefault(false));
    addresses.flush();
  }
}
