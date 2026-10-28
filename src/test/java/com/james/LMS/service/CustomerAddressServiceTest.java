package com.james.LMS.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.CustomerAddress;
import com.james.LMS.entity.User;
import com.james.LMS.repository.CustomerAddressRepository;
import com.james.LMS.request.CustomerAddressRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {
  @Mock CustomerAddressRepository repository;
  @Mock EntityManager entityManager;
  @InjectMocks CustomerAddressService service;
  final Long userId = 10L;

  @BeforeEach
  void owner() {
    lenient().when(entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(User.builder().id(userId).build());
  }

  CustomerAddress address(long id, boolean isDefault) {
    return CustomerAddress.builder().id(id).userId(userId).version(2L)
        .label("Nhà").recipientName("Khách").phone("0901234567").addressLine("12 Nguyễn Trãi")
        .district("Quận 1").city("TP.HCM").defaultAddress(isDefault).build();
  }

  CustomerAddressRequest input(boolean isDefault, Long version) {
    return new CustomerAddressRequest(" Nhà ", " Khách ", "0901234567", "12 Nguyễn Trãi",
        "", "Quận 1", "TP.HCM", "", isDefault, version);
  }

  @Test
  void firstAddressBecomesDefaultAndLocksOwnerBeforeReading() {
    when(repository.findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId)).thenReturn(List.of());
    when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    var response = service.create(userId, input(false, null));
    assertThat(response.isDefault()).isTrue();
    assertThat(response.label()).isEqualTo("Nhà");
    var order = inOrder(entityManager, repository);
    order.verify(entityManager).find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    order.verify(repository).findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId);
  }

  @Test
  void switchingDefaultClearsAndFlushesOldBeforePromotingNew() {
    var old = address(1, true);
    var next = address(2, false);
    when(repository.findByIdAndUserIdAndIsActiveTrue(2L, userId)).thenReturn(Optional.of(next));
    when(repository.findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId)).thenReturn(List.of(old, next));
    doAnswer(call -> {
      assertThat(old.isDefaultAddress() && next.isDefaultAddress()).isFalse();
      return null;
    }).when(repository).flush();
    assertThat(service.setDefault(userId, 2L).isDefault()).isTrue();
    assertThat(old.isDefaultAddress()).isFalse();
    verify(repository, times(2)).flush();
  }

  @Test
  void settingDefaultTwiceIsIdempotent() {
    when(repository.findByIdAndUserIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(address(1, true)));
    service.setDefault(userId, 1L);
    verify(repository, never()).flush();
  }

  @Test
  void deletingDefaultSoftDeletesAndPromotesRemaining() {
    var old = address(1, true);
    var remaining = address(2, false);
    when(repository.findByIdAndUserIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(old));
    when(repository.findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId)).thenReturn(List.of(remaining));
    service.delete(userId, 1L);
    assertThat(old.isActive()).isFalse();
    assertThat(old.isDefaultAddress()).isFalse();
    assertThat(remaining.isDefaultAddress()).isTrue();
    verify(repository, never()).delete(any());
  }

  @Test
  void deletingLastAddressLeavesNoDefault() {
    var old = address(1, true);
    when(repository.findByIdAndUserIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(old));
    when(repository.findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(userId)).thenReturn(List.of());
    service.delete(userId, 1L);
    assertThat(old.isActive()).isFalse();
  }

  @Test
  void staleUpdateDoesNotOverwriteAddress() {
    var current = address(1, true);
    when(repository.findByIdAndUserIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(current));
    assertThatThrownBy(() -> service.update(userId, 1L, input(false, 1L)))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void updateCannotUnsetTheOnlyDefault() {
    var current = address(1, true);
    when(repository.findByIdAndUserIdAndIsActiveTrue(1L, userId)).thenReturn(Optional.of(current));
    when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(service.update(userId, 1L, input(false, 2L)).isDefault()).isTrue();
  }

  @Test
  void anotherUsersOrDeletedAddressIsNotAccessible() {
    when(repository.findByIdAndUserIdAndIsActiveTrue(99L, userId)).thenReturn(Optional.empty());
    for (Runnable operation : List.<Runnable>of(
        () -> service.update(userId, 99L, input(true, 2L)),
        () -> service.delete(userId, 99L),
        () -> service.setDefault(userId, 99L))) {
      assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
          error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
    verify(repository, never()).saveAndFlush(any());
  }
}
