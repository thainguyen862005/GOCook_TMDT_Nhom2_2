package com.james.LMS.notification;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, per-recipient inbox. Producers call this service from their business transaction.
 * Do not expose event creation to regular authenticated users.
 * A separate transport (email/push) may be added later without changing these contracts.
 */
@Service
public class NotificationService {
  private static final Set<String> TYPES = Set.of(
      "ACCOUNT", "BOOKING", "PAYMENT", "CHEF", "SUPPORT", "REQUEST", "SYSTEM", "PROMOTION");
  private final NamedParameterJdbcTemplate jdbc;

  public NotificationService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  public static String safeAction(String path) {
    if (path == null || path.isBlank()) return "/account";
    // Internal navigation only; prevent javascript:, //evil, backslash, CRLF and HTML injection.
    if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")
        || path.chars().anyMatch(c -> c < 32 || c == 127) || path.length() > 260)
      throw new IllegalArgumentException("Đường dẫn thông báo không hợp lệ.");
    return path;
  }

  public static String checkedType(String type) {
    if (type == null || !TYPES.contains(type))
      throw new IllegalArgumentException("Loại thông báo không hợp lệ.");
    return type;
  }

  private static String limit(String value, int length, String field) {
    if (value == null || value.isBlank() || value.strip().length() > length)
      throw new IllegalArgumentException(field + " không hợp lệ.");
    return value.strip();
  }

  /** Idempotent by (recipient_id,event_key); same database transaction as business change. */
  @Transactional
  public void sendToUser(Long recipientId, String eventKey, String type, String title,
                         String body, String actionPath) {
    if (recipientId == null || recipientId <= 0) return;
    var p = new MapSqlParameterSource()
        .addValue("id", recipientId).addValue("key", limit(eventKey, 140, "Event key"))
        .addValue("type", checkedType(type)).addValue("title", limit(title, 180, "Tiêu đề"))
        .addValue("body", limit(body, 600, "Nội dung"))
        .addValue("path", safeAction(actionPath));
    jdbc.update("""
        INSERT INTO app_notifications(recipient_id,event_key,type,title,body,action_path)
        SELECT u.id,:key,:type,:title,:body,:path FROM users u
        WHERE u.id=:id AND u.is_active=true
          AND (:type <> 'PROMOTION' OR NOT EXISTS
                (SELECT 1 FROM notification_preferences pref
                 WHERE pref.user_id=u.id AND pref.marketing_enabled=false))
        ON CONFLICT (recipient_id,event_key) DO NOTHING
        """, p);
  }

  /** Bulk send without loading user emails into application memory. ADMIN-only entry point. */
  @Transactional
  public int sendCampaign(Long adminId, String target, String category, String title, String body) {
    if (!"SYSTEM".equals(category) && !"PROMOTION".equals(category))
      throw new IllegalArgumentException("Chỉ gửi thông báo hệ thống hoặc ưu đãi.");
    String normalized = target == null ? "" : target.trim();
    String role = switch (normalized) {
      case "ALL" -> null;
      case "CUSTOMER" -> "USER";
      case "CHEF" -> "INSTRUCTOR";
      case "ADMIN" -> "ADMIN";
      case "COMPANY_ADMIN" -> "COMPANY_ADMIN";
      default -> throw new IllegalArgumentException("Nhóm người nhận không hợp lệ.");
    };
    var p = new MapSqlParameterSource().addValue("admin", adminId)
        .addValue("role", role).addValue("category", category).addValue("title", limit(title,180,"Tiêu đề"))
        .addValue("body", limit(body,600,"Nội dung"))
        .addValue("key", "campaign:" + UUID.randomUUID());
    // Guard against flooding by one compromised admin account.
    jdbc.queryForList("SELECT pg_advisory_xact_lock(:admin)", p);
    Long today = jdbc.queryForObject("""
        SELECT COUNT(*) FROM notification_campaigns
        WHERE actor_id=:admin AND created_at > now()-interval '24 hours'
        """, p, Long.class);
    if (today != null && today >= 10) throw new IllegalArgumentException("Đã đạt giới hạn 10 thông báo hệ thống mỗi 24 giờ.");
    // Also bound recipient count per campaign to prevent unexpected mass sends.
    Long total = jdbc.queryForObject("""
        SELECT COUNT(DISTINCT u.id) FROM users u
        WHERE u.is_active=true AND (CAST(:role AS varchar) IS NULL OR EXISTS (
          SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
          WHERE ur.user_id=u.id AND r.role_name=:role AND ur.is_active=true))
          AND (:category <> 'PROMOTION' OR NOT EXISTS (
            SELECT 1 FROM notification_preferences pref WHERE pref.user_id=u.id AND pref.marketing_enabled=false))
        """, p, Long.class);
    if (total != null && total > 50000) throw new IllegalArgumentException("Chiến dịch vượt giới hạn người nhận.");
    jdbc.update("""
        INSERT INTO notification_campaigns(actor_id,target_group,event_key,type,title,body,recipient_count)
        VALUES (:admin,:target,:key,:category,:title,:body,:total)
        """, p.addValue("target",normalized).addValue("total",total == null ? 0 : total));
    return jdbc.update("""
        INSERT INTO app_notifications(recipient_id,event_key,type,title,body,action_path)
        SELECT DISTINCT u.id,:key,:category,:title,:body,'/notifications' FROM users u
        WHERE u.is_active=true AND (CAST(:role AS varchar) IS NULL OR EXISTS (
          SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
          WHERE ur.user_id=u.id AND r.role_name=:role AND ur.is_active=true))
          AND (:category <> 'PROMOTION' OR NOT EXISTS (
            SELECT 1 FROM notification_preferences pref WHERE pref.user_id=u.id AND pref.marketing_enabled=false))
        ON CONFLICT (recipient_id,event_key) DO NOTHING
        """,p);
  }

  /** Internal helper: notify every active System Admin of an actionable event. */
  @Transactional
  public void sendToAdmins(String eventKey, String type, String title, String body, String actionPath) {
    var p = new MapSqlParameterSource()
        .addValue("key", limit(eventKey,140,"Event key"))
        .addValue("type",checkedType(type)).addValue("title",limit(title,180,"Tiêu đề"))
        .addValue("body",limit(body,600,"Nội dung"))
        .addValue("path",safeAction(actionPath));
    jdbc.update("""
        INSERT INTO app_notifications(recipient_id,event_key,type,title,body,action_path)
        SELECT DISTINCT u.id,:key,:type,:title,:body,:path FROM users u
          JOIN user_roles ur ON ur.user_id=u.id AND ur.is_active=true
          JOIN roles r ON r.id=ur.role_id AND r.role_name='ADMIN'
        WHERE u.is_active=true
        ON CONFLICT (recipient_id,event_key) DO NOTHING
        """,p);
  }

  @Transactional(readOnly = true)
  public Map<String,Object> inbox(Long userId, int page, int size, boolean unreadOnly) {
    if (page < 0 || page > 100000 || size < 1 || size > 50)
      throw new IllegalArgumentException("Phân trang không hợp lệ.");
    var p = new MapSqlParameterSource("id",userId).addValue("unread",unreadOnly)
        .addValue("limit",size).addValue("offset",(long)page*size);
    var rows = jdbc.queryForList("""
        SELECT id,type,title,body,action_path AS "actionPath",is_read AS "read",
               created_at AS "createdAt",read_at AS "readAt"
        FROM app_notifications WHERE recipient_id=:id AND (:unread=false OR is_read=false)
        ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset
        """,p);
    Long total = jdbc.queryForObject("""
        SELECT count(*) FROM app_notifications WHERE recipient_id=:id
        AND (:unread=false OR is_read=false)""",p,Long.class);
    return Map.of("content",rows,"page",page,"size",size,"totalElements",total == null?0:total);
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long userId) {
    Long value=jdbc.queryForObject("SELECT count(*) FROM app_notifications WHERE recipient_id=:id AND is_read=false",
        new MapSqlParameterSource("id",userId),Long.class);
    return value == null ? 0 : value;
  }

  @Transactional
  public boolean markRead(Long userId, long notificationId) {
    return jdbc.update("""
        UPDATE app_notifications SET is_read=true,read_at=now()
        WHERE id=:notification AND recipient_id=:user AND is_read=false
        """,new MapSqlParameterSource("notification",notificationId).addValue("user",userId)) > 0;
  }

  @Transactional
  public int markAllRead(Long userId) {
    return jdbc.update("""
        UPDATE app_notifications SET is_read=true,read_at=now()
        WHERE recipient_id=:id AND is_read=false
        """,new MapSqlParameterSource("id",userId));
  }

  @Transactional(readOnly = true)
  public boolean marketingEnabled(Long userId) {
    var rows=jdbc.queryForList("SELECT marketing_enabled FROM notification_preferences WHERE user_id=:id",
        new MapSqlParameterSource("id",userId));
    return rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("marketing_enabled"));
  }

  @Transactional
  public void setMarketingEnabled(Long userId, boolean value) {
    jdbc.update("""
        INSERT INTO notification_preferences(user_id,marketing_enabled,updated_at)
        VALUES (:id,:enabled,now())
        ON CONFLICT (user_id) DO UPDATE SET marketing_enabled=EXCLUDED.marketing_enabled,updated_at=now()
        """,new MapSqlParameterSource("id",userId).addValue("enabled",value));
  }
}
