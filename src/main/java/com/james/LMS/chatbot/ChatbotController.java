package com.james.LMS.chatbot;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Separate public and private endpoints to respect GO Cook's auth-filter bypass on /api/public. */
@RestController
public class ChatbotController {
  public record Input(@NotBlank @Size(min=2,max=500) String message,
                      ChatbotService.Context context) {}
  private final ChatbotService chatbot;
  public ChatbotController(ChatbotService chatbot) { this.chatbot=chatbot; }

  @PostMapping("/api/public/chatbot/message")
  public ChatbotService.Reply publicChat(@Valid @RequestBody Input input) {
    return chatbot.answer(input.message(), input.context(), null);
  }

  @PostMapping("/api/customer/chatbot/message")
  @PreAuthorize("hasAuthority('ROLE_USER') and !hasAnyAuthority('ROLE_INSTRUCTOR','ROLE_SYSTEM_ADMIN','ROLE_COMPANY_ADMIN')")
  public ChatbotService.Reply customerChat(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody Input input) {
    if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return chatbot.answer(input.message(), input.context(), actor.getId());
  }
}
