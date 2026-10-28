package com.james.LMS.notification;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Every route requires an authenticated cookie; the recipient is NEVER supplied by the client. */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service=service; }

  private static long userId(SecurityUserDetails actor) {
    if (actor == null || actor.getId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return actor.getId();
  }

  @GetMapping
  public Map<String,Object> list(@AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="15") int size,
      @RequestParam(defaultValue="false") boolean unreadOnly) {
    return service.inbox(userId(actor),page,size,unreadOnly);
  }
  @GetMapping("/unread-count")
  public Map<String,Long> unread(@AuthenticationPrincipal SecurityUserDetails actor) {
    return Map.of("unread",service.unreadCount(userId(actor)));
  }
  @PatchMapping("/{id}/read")
  public Map<String,Boolean> read(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id) {
    // Foreign IDs are never disclosed; same response as an already-read item.
    return Map.of("updated",service.markRead(userId(actor),id));
  }
  @PatchMapping("/read-all")
  public Map<String,Integer> readAll(@AuthenticationPrincipal SecurityUserDetails actor) {
    return Map.of("updated",service.markAllRead(userId(actor)));
  }
  @GetMapping("/preferences")
  public Map<String,Boolean> preferences(@AuthenticationPrincipal SecurityUserDetails actor) {
    return Map.of("marketingEnabled",service.marketingEnabled(userId(actor)));
  }
  public record Preferences(@jakarta.validation.constraints.NotNull Boolean marketingEnabled) {}
  @PutMapping("/preferences")
  public Map<String,Boolean> preferences(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody Preferences body) {
    service.setMarketingEnabled(userId(actor),body.marketingEnabled());
    return Map.of("marketingEnabled",body.marketingEnabled());
  }
  public record Campaign(@NotBlank @Size(max=20) String target,@NotBlank @Size(max=20) String category,@NotBlank @Size(max=180) String title,
                         @NotBlank @Size(max=600) String body) {}
  @PostMapping("/admin/campaigns")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Integer> campaign(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody Campaign body) {
    return Map.of("recipients",service.sendCampaign(userId(actor),body.target(),body.category(),body.title(),body.body()));
  }
}
