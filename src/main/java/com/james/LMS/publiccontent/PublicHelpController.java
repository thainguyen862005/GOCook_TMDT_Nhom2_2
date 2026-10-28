package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.notification.NotificationService;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Requests go to the existing Admin ticket inbox. There is no public ticket lookup endpoint. */
@RestController
public class PublicHelpController {
  private final NamedParameterJdbcTemplate jdbc;
  private final NotificationService notifications;
  public PublicHelpController(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
    this.jdbc = jdbc; this.notifications = notifications;
  }

  public record TicketInput(@NotBlank @Size(min=5,max=255) String subject,
      @NotBlank @Size(min=10,max=5000) String message) {}

  @PostMapping("/api/customer/support-tickets")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, String> create(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody TicketInput input) {
    if (actor == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
    var args = new MapSqlParameterSource("id", actor.getId());
    var accounts = jdbc.queryForList("SELECT username,email,email_verified FROM users WHERE id=:id AND is_active=true", args);
    if (accounts.isEmpty() || !Boolean.TRUE.equals(accounts.get(0).get("email_verified")))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Cần xác minh email.");
    var account = accounts.get(0);
    Long hourly = jdbc.queryForObject("""
        SELECT COUNT(*) FROM admin_tickets WHERE contact_email=:email
        AND created_at > now()-interval '1 hour'
        """,new MapSqlParameterSource("email",account.get("email")),Long.class);
    if (hourly != null && hourly >= 3)
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Vui lòng thử lại sau.");
    String code = "WEB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    jdbc.update("""
        INSERT INTO admin_tickets(ticket_code,name,contact_email,subject,message,status,priority)
        VALUES (:code,:name,:email,:subject,:message,'OPEN','MEDIUM')
        """, new MapSqlParameterSource().addValue("code", code)
        .addValue("name", account.get("username")).addValue("email", account.get("email"))
        .addValue("subject", input.subject().trim()).addValue("message", input.message().trim()));
    notifications.sendToUser(actor.getId(), "support-open:"+code, "SUPPORT", "Đã tiếp nhận yêu cầu hỗ trợ",
        "Mã yêu cầu " + code + ". Bộ phận hỗ trợ sẽ xử lý trong thời gian sớm nhất.", "/help");
    notifications.sendToAdmins("support-new:"+code,"SUPPORT","Yêu cầu hỗ trợ mới",
        "Có yêu cầu hỗ trợ mới: " + code + ".", "/admin/manage/tickets");
    return Map.of("ticketCode", code, "status", "OPEN");
  }

  public record FaqInput(@NotBlank @Size(max=255) String question,
      @NotBlank @Size(max=4000) String answer, @Size(max=80) String category,
      int displayOrder, boolean active) {}

  @GetMapping("/api/admin/faqs")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public List<Map<String, Object>> adminFaqs() {
    return jdbc.queryForList("SELECT id,question,answer,category,display_order AS \"displayOrder\",is_active AS \"active\" FROM public_faqs ORDER BY display_order,id", java.util.Collections.emptyMap());
  }

  @PostMapping("/api/admin/faqs")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> addFaq(@Valid @RequestBody FaqInput input) {
    validateOrder(input.displayOrder());
    MapSqlParameterSource p = faqParams(input);
    Long id = jdbc.queryForObject("""
        INSERT INTO public_faqs(question,answer,category,display_order,is_active)
        VALUES (:question,:answer,:category,:displayOrder,:active) RETURNING id
        """, p, Long.class);
    return Map.of("id", id);
  }

  @PutMapping("/api/admin/faqs/{id}")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public Map<String, String> updateFaq(@PathVariable long id, @Valid @RequestBody FaqInput input) {
    validateOrder(input.displayOrder());
    if (id <= 0) throw new IllegalArgumentException("ID FAQ không hợp lệ.");
    MapSqlParameterSource p = faqParams(input).addValue("id", id);
    int updated = jdbc.update("""
        UPDATE public_faqs SET question=:question,answer=:answer,category=:category,
          display_order=:displayOrder,is_active=:active,updated_at=now() WHERE id=:id
        """, p);
    if (updated == 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
    return Map.of("message", "Đã cập nhật câu hỏi.");
  }

  private static MapSqlParameterSource faqParams(FaqInput input) {
    return new MapSqlParameterSource().addValue("question", input.question().trim())
        .addValue("answer", input.answer().trim())
        .addValue("category", input.category() == null ? "Chung" : input.category().trim())
        .addValue("displayOrder", input.displayOrder()).addValue("active", input.active());
  }
  private static void validateOrder(int value) {
    if (value < 0 || value > 100000) throw new IllegalArgumentException("Thứ tự FAQ không hợp lệ.");
  }
}
