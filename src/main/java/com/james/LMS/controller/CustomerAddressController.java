package com.james.LMS.controller;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.request.CustomerAddressRequest;
import com.james.LMS.response.BaseResponse;
import com.james.LMS.response.CustomerAddressResponse;
import com.james.LMS.service.CustomerAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer addresses belong to the authenticated principal, never to a client-supplied userId. */
@RestController
@RequestMapping("/api/v1/users/addresses")
@PreAuthorize("hasAuthority('ROLE_USER')")
@RequiredArgsConstructor
public class CustomerAddressController {
  private final CustomerAddressService addresses;

  @GetMapping
  public BaseResponse<List<CustomerAddressResponse>> list(
      @AuthenticationPrincipal SecurityUserDetails actor) {
    return BaseResponse.build(addresses.list(actor.getId()), true);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BaseResponse<CustomerAddressResponse> create(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody CustomerAddressRequest request) {
    return BaseResponse.build(addresses.create(actor.getId(), request), true);
  }

  @PutMapping("/{id}")
  public BaseResponse<CustomerAddressResponse> update(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long id, @Valid @RequestBody CustomerAddressRequest request) {
    return BaseResponse.build(addresses.update(actor.getId(), id, request), true);
  }

  @PatchMapping("/{id}/default")
  public BaseResponse<CustomerAddressResponse> setDefault(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long id) {
    return BaseResponse.build(addresses.setDefault(actor.getId(), id), true);
  }

  @DeleteMapping("/{id}")
  public BaseResponse<Void> delete(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long id) {
    addresses.delete(actor.getId(), id);
    return BaseResponse.ok();
  }
}
