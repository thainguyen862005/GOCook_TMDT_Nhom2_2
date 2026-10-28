package com.james.LMS.seller.orders;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller/orders")
@PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
@RequiredArgsConstructor
public class ChefOrderController {
  private final ChefOrderService service;

  @GetMapping
  public ChefOrderService.OrderPage list(@AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(defaultValue="") String status,
      @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="10") int size,
      @RequestParam(required=false) LocalDate from,
      @RequestParam(required=false) LocalDate to) {
    return service.list(actor.getId(),status,page,size,from,to);
  }

  @GetMapping("/summary")
  public ChefOrderService.Summary summary(@AuthenticationPrincipal SecurityUserDetails actor) {
    return service.summary(actor.getId());
  }

  @GetMapping("/{id}")
  public ChefOrderService.OrderDetail detail(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long id) { return service.get(actor.getId(),id); }

  @PostMapping("/{id}/transitions")
  public ChefOrderService.OrderDetail transition(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long id, @RequestBody ChefOrderService.Transition request) {
    return service.transition(actor.getId(),id,request);
  }
}
