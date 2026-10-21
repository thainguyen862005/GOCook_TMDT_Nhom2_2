package com.james.LMS.admin.operations;

import com.james.LMS.admin.exception.AdminResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only operations projection. Does not mutate booking/payment aggregates;
 * their domain owners remain the only writers of business transactions.
 */
@Service
public class AdminOperationsService {
    private record Source(String sql, String search, String filter) {}

    private static final Map<String, Source> SOURCES = Map.of(
        "customers", new Source("""
            SELECT u.id, u.username, u.email, u.is_active, u.email_verified,
                   (SELECT COUNT(*) FROM orders o WHERE o.customer_id=u.id) AS bookings,
                   COALESCE((SELECT SUM(o.total_amount) FROM orders o
                        WHERE o.customer_id=u.id AND o.status='COMPLETED'),0) AS completed_order_value
            FROM users u WHERE EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                WHERE ur.user_id=u.id AND ur.is_active=true AND r.role_name='USER')
            """, "username,email", "is_active"),
        "chefs", new Source("""
            SELECT cp.user_id AS id, cp.display_name, u.email, cp.service_area,
                   cp.rating, cp.is_verified, cp.verification_status, cp.is_active,
                   cp.verification_note,
                   (SELECT COUNT(*) FROM products p WHERE p.seller_id=cp.user_id AND p.is_active=true) AS menus,
                   (SELECT COUNT(*) FROM orders o WHERE o.chef_id=cp.user_id) AS bookings
            FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
            """, "display_name,email,service_area", "verification_status"),
        "payments", new Source("""
            SELECT pt.id, pt.order_id, o.order_code, o.customer_name,
                   pt.provider, pt.transaction_code, pt.amount, pt.status,
                   pt.paid_at, pt.created_at
            FROM payment_transactions pt LEFT JOIN orders o ON o.id=pt.order_id
            """, "order_code,customer_name,transaction_code", "status"),
        "vouchers", new Source("SELECT * FROM admin_vouchers", "code,description", "is_active"),
        "cases", new Source("SELECT * FROM admin_cases", "case_code,subject,description", "status"),
        "refunds", new Source("""
            SELECT r.*, o.order_code, o.customer_name, o.total_amount AS booking_total
            FROM admin_refund_requests r JOIN orders o ON o.id=r.order_id
            """, "order_code,customer_name,reason", "status"),
        "reviews", new Source("SELECT * FROM admin_review_reports", "reason,decision_note", "status"),
        "tickets", new Source("SELECT * FROM admin_tickets", "ticket_code,name,contact_email,subject,message", "status"),
        "audit", new Source("SELECT * FROM admin_audit_logs", "action,entity_type,details", "entity_type")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditService audit;

    public AdminOperationsService(NamedParameterJdbcTemplate jdbc, AdminAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> page(String resource, String query, String status, int page, int size) {
        Source source = source(resource);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Phân trang phải có page ≥ 0, size từ 1 đến 100.");
        }
        if (query != null && query.length() > 150) throw new IllegalArgumentException("Tìm kiếm quá dài.");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("term", "%" + (query == null ? "" : query.trim()) + "%")
                .addValue("status", status == null ? "" : status.trim());

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (query != null && !query.isBlank()) {
            where.append(" AND (");
            String[] keys = source.search().split(",");
            for (int i=0; i<keys.length; i++) {
                if (i>0) where.append(" OR ");
                // Fixed server-owned whitelist, never taken from request.
                where.append("CAST(q.").append(keys[i]).append(" AS TEXT) ILIKE :term");
            }
            where.append(")");
        }
        if (status != null && !status.isBlank()) {
            if (source.filter().equals("is_active")) {
                if (!status.equalsIgnoreCase("true") && !status.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Trạng thái hợp lệ: true hoặc false.");
                }
                where.append(" AND q.is_active = CAST(:status AS BOOLEAN)");

            } else {
                where.append(" AND CAST(q.").append(source.filter()).append(" AS TEXT) = :status");
            }
        }
        String derived = "(" + source.sql() + ") q";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + derived + where,
                params, Long.class);
        long count = total == null ? 0 : total;
        params.addValue("limit", size).addValue("offset", (long)page * size);
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + derived + where + " ORDER BY q.id DESC LIMIT :limit OFFSET :offset",
                params);
        return Map.of("content", rows, "page", page, "size", size,
                "totalElements", count, "totalPages", (count + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> one(String resource, Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID không hợp lệ.");
        Source source = source(resource);
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT * FROM (" + source.sql() + ") q WHERE q.id = :id",
                new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) throw new AdminResourceNotFoundException("Không tìm thấy bản ghi.");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> overview(Long userId, boolean chef) {
        Map<String,Object> base = one(chef ? "chefs" : "customers", userId);
        List<Map<String,Object>> orders = jdbc.queryForList("""
                SELECT id, order_code, scheduled_at, status, total_amount,
                       product_name, customer_name, chef_name
                FROM orders WHERE """ + (chef ? "chef_id" : "customer_id") + " = :id ORDER BY id DESC LIMIT 30",
                new MapSqlParameterSource("id", userId));
        List<Map<String,Object>> payments = jdbc.queryForList("""
                SELECT p.id,p.order_id,p.provider,p.status,p.amount,p.paid_at
                FROM payment_transactions p JOIN orders o ON o.id=p.order_id
                WHERE """ + (chef ? "o.chef_id" : "o.customer_id") + " = :id ORDER BY p.id DESC LIMIT 30",
                new MapSqlParameterSource("id", userId));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("profile", base);
        result.put("orders", orders);
        result.put("payments", payments);
        if (chef) {
            result.put("verificationHistory", jdbc.queryForList(
                    "SELECT * FROM admin_chef_verifications WHERE chef_id=:id ORDER BY id DESC LIMIT 30",
                    new MapSqlParameterSource("id", userId)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> orderTimeline(Long id) {
        List<Map<String,Object>> items = jdbc.queryForList("""
                SELECT id,order_code,status,scheduled_at,total_amount,customer_name,chef_name,internal_note
                FROM orders WHERE id=:id
                """,new MapSqlParameterSource("id",id));
        if (items.isEmpty()) throw new AdminResourceNotFoundException("Không tìm thấy đơn hàng.");
        return Map.of("order", items.get(0),
                "payments", jdbc.queryForList("""
                    SELECT id,provider,transaction_code,amount,status,paid_at
                    FROM payment_transactions WHERE order_id=:id ORDER BY id DESC
                    """, new MapSqlParameterSource("id",id)),
                "adminHistory", jdbc.queryForList("""
                    SELECT id,actor_id,action,details,created_at
                    FROM admin_audit_logs WHERE entity_type='ORDER' AND entity_id=:id ORDER BY id DESC
                    """, new MapSqlParameterSource("id",id)));
    }

    @Transactional
    public Map<String,Object> verifyChef(Long chefId, String decision, String note, Long actorId) {
        String newStatus = choice(decision,"APPROVED","REJECTED","MORE_INFO","PENDING");
        String reason = required(note,"Lý do",10,1000);
        jdbc.queryForList("SELECT user_id FROM chef_profiles WHERE user_id=:id FOR UPDATE",
                new MapSqlParameterSource("id",chefId));
        Map<String,Object> before = one("chefs", chefId);
        jdbc.update("""
                UPDATE chef_profiles SET verification_status=:status, verification_note=:note,
                    is_verified=:verified, reviewed_by=:actor, reviewed_at=now(),
                    updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint
                WHERE user_id=:id
                """, new MapSqlParameterSource("id",chefId).addValue("status",newStatus)
                .addValue("verified",newStatus.equals("APPROVED")).addValue("note",reason).addValue("actor",actorId));
        jdbc.update("""
                INSERT INTO admin_chef_verifications(chef_id,reviewer_id,old_status,new_status,reason)
                VALUES(:id,:actor,:old,:new,:reason)
                """, new MapSqlParameterSource("id",chefId).addValue("actor",actorId)
                .addValue("old",before.get("verification_status"))
                .addValue("new",newStatus).addValue("reason",reason));
        audit.record(actorId,"CHEF_VERIFICATION","CHEF",chefId,newStatus+": "+reason);
        return one("chefs",chefId);
    }

    @Transactional
    public Map<String,Object> suspendChef(Long chefId, boolean active, String reason, Long actorId) {
        required(reason,"Lý do",10,1000);
        jdbc.queryForList("SELECT user_id FROM chef_profiles WHERE user_id=:id FOR UPDATE",
                new MapSqlParameterSource("id",chefId));
        one("chefs",chefId);
        if (!active) {
            Long pending = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM orders
                    WHERE chef_id=:id AND scheduled_at>=now()
                    AND status NOT IN ('COMPLETED','CANCELLED','REJECTED')
                    """,new MapSqlParameterSource("id",chefId),Long.class);
            if (pending != null && pending>0) {
                throw new IllegalArgumentException("Chef còn "+pending+
                    " booking sắp tới; cần giải quyết trước khi khóa.");
            }
        }
        jdbc.update("""
                UPDATE chef_profiles SET is_active=:active,
                    updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint WHERE user_id=:id
                """, new MapSqlParameterSource("id",chefId).addValue("active",active));
        audit.record(actorId,active?"CHEF_UNSUSPEND":"CHEF_SUSPEND","CHEF",chefId,reason);
        return one("chefs",chefId);
    }

    @Transactional
    public Map<String,Object> create(String resource, Map<String,Object> body, Long actorId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        final String sql;
        switch(resource) {
            case "vouchers" -> {
                String code = required(body.get("code"),"Mã voucher",3,40).toUpperCase(Locale.ROOT);
                if (!code.matches("[A-Z0-9_-]{3,40}")) throw new IllegalArgumentException("Mã voucher chỉ gồm chữ hoa, số, - và _.");
                String type = choice(text(body.get("discountType")),"PERCENT","FIXED");
                BigDecimal value = positive(body.get("discountValue"),"Giá trị giảm");
                if (type.equals("PERCENT") && value.compareTo(BigDecimal.valueOf(100))>0)
                    throw new IllegalArgumentException("Phần trăm giảm không được vượt quá 100.");
                OffsetDateTime from = date(body.get("startsAt"));
                OffsetDateTime to = date(body.get("endsAt"));
                if (!to.isAfter(from)) throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
                long limit = integer(body.get("usageLimit"),1,1_000_000);
                p.addValue("code",code).addValue("description",text(body.get("description")))
                  .addValue("type",type).addValue("value",value)
                  .addValue("min",nonNegative(body.get("minOrderAmount"),"Đơn tối thiểu"))
                  .addValue("max",optionalMoney(body.get("maxDiscount")))
                  .addValue("limit",limit).addValue("start",from).addValue("end",to);
                sql="""
                   INSERT INTO admin_vouchers(code,description,discount_type,discount_value,min_order_amount,
                      max_discount,usage_limit,starts_at,ends_at,is_active)
                   VALUES(:code,:description,:type,:value,:min,:max,:limit,:start,:end,false) RETURNING id
                   """;
            }
            case "cases" -> {
                p.addValue("code","CASE-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT))
                  .addValue("subject",required(body.get("subject"),"Tiêu đề",5,255))
                  .addValue("description",required(body.get("description"),"Nội dung",10,5000))
                  .addValue("priority",choice(defaultValue(body.get("priority"),"MEDIUM"),"LOW","MEDIUM","HIGH","URGENT"))
                  .addValue("booking",optionalId(body.get("bookingId")));
                sql="""
                   INSERT INTO admin_cases(case_code,subject,description,priority,booking_id)
                   VALUES(:code,:subject,:description,:priority,:booking) RETURNING id
                   """;
            }
            case "tickets" -> {
                String email = required(body.get("contactEmail"),"Email",5,255);
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Email không hợp lệ.");
                p.addValue("code","TICKET-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT))
                  .addValue("name",required(body.get("name"),"Tên liên hệ",2,120))
                  .addValue("email",email)
                  .addValue("subject",required(body.get("subject"),"Tiêu đề",5,255))
                  .addValue("message",required(body.get("message"),"Nội dung",10,5000))
                  .addValue("priority",choice(defaultValue(body.get("priority"),"MEDIUM"),"LOW","MEDIUM","HIGH","URGENT"));
                sql="""
                   INSERT INTO admin_tickets(ticket_code,name,contact_email,subject,message,priority)
                   VALUES(:code,:name,:email,:subject,:message,:priority) RETURNING id
                   """;
            }
            case "refunds" -> {
                Long orderId = optionalId(body.get("orderId"));
                if (orderId==null) throw new IllegalArgumentException("Cần mã đơn hàng.");
                Map<String,Object> order=orderById(orderId);
                BigDecimal requested=positive(body.get("amount"),"Số tiền hoàn");
                BigDecimal total=new BigDecimal(order.get("total_amount").toString());
                if (requested.compareTo(total)>0) throw new IllegalArgumentException("Số tiền hoàn vượt giá trị đơn.");
                Long paid=jdbc.queryForObject("""
                   SELECT COUNT(*) FROM payment_transactions WHERE order_id=:id AND status='SUCCESS'
                   """,new MapSqlParameterSource("id",orderId),Long.class);
                if(paid == null || paid==0) throw new IllegalArgumentException("Chưa có giao dịch thành công để hoàn tiền.");
                p.addValue("order",orderId).addValue("amount",requested)
                  .addValue("reason",required(body.get("reason"),"Lý do",10,1000));
                sql="""
                   INSERT INTO admin_refund_requests(order_id,amount,reason)
                   VALUES(:order,:amount,:reason) RETURNING id
                   """;
            }
            case "reviews" -> {
                p.addValue("external",optionalId(body.get("externalReviewId")))
                  .addValue("reason",required(body.get("reason"),"Lý do",10,1000));
                sql="""
                    INSERT INTO admin_review_reports(external_review_id,reason)
                    VALUES(:external,:reason) RETURNING id
                    """;
            }
            default -> throw new IllegalArgumentException("Module không hỗ trợ tạo dữ liệu.");
        }
        Long id=jdbc.queryForObject(sql,p,Long.class);
        audit.record(actorId,"CREATE","ADMIN_"+resource.toUpperCase(Locale.ROOT),id,"Tạo bản ghi quản trị");
        return one(resource,id);
    }

    @Transactional
    public Map<String,Object> change(String resource, Long id, Map<String,Object> body, Long actorId) {
        Map<String,Object> old=one(resource,id);
        String status;
        String note=required(body.get("note"),"Lý do/ghi chú",5,1000);
        MapSqlParameterSource p=new MapSqlParameterSource("id",id).addValue("note",note).addValue("actor",actorId);
        final String sql;
        switch(resource) {
            case "vouchers" -> {
                String activeValue = text(body.get("active"));
                if (!activeValue.equals("true") && !activeValue.equals("false"))
                    throw new IllegalArgumentException("active phải có giá trị true hoặc false.");
                boolean active = Boolean.parseBoolean(activeValue);
                if(active && OffsetDateTime.now().isAfter(asOffset(old.get("ends_at"))))
                    throw new IllegalArgumentException("Voucher hết hạn không thể kích hoạt.");
                p.addValue("active",active);
                sql="UPDATE admin_vouchers SET is_active=:active,updated_at=now(),version=version+1 WHERE id=:id";
                status=active?"ACTIVATED":"DEACTIVATED";
            }
            case "cases" -> {
                status=choice(text(body.get("status")),"OPEN","IN_REVIEW","RESOLVED","CLOSED");
                if("CLOSED".equals(old.get("status")) && !status.equals("CLOSED"))
                    throw new IllegalArgumentException("Case đã đóng, không thể sửa trạng thái.");
                p.addValue("status",status);
                sql="UPDATE admin_cases SET status=:status,internal_note=:note,updated_at=now(),version=version+1 WHERE id=:id";
            }
            case "tickets" -> {
                status=choice(text(body.get("status")),"OPEN","IN_PROGRESS","ANSWERED","CLOSED");
                if("CLOSED".equals(old.get("status")) && !status.equals("CLOSED"))
                    throw new IllegalArgumentException("Ticket đã đóng, không thể mở lại từ màn hình này.");
                p.addValue("status",status);
                sql="UPDATE admin_tickets SET status=:status,internal_note=:note,updated_at=now(),version=version+1 WHERE id=:id";
            }
            case "refunds" -> {
                if(!"PENDING".equals(old.get("status"))) throw new IllegalArgumentException("Chỉ duyệt yêu cầu đang chờ.");
                status=choice(text(body.get("status")),"REVIEWED","REJECTED");
                p.addValue("status",status);
                sql="""
                    UPDATE admin_refund_requests SET status=:status,decision_note=:note,
                       reviewed_by=:actor,reviewed_at=now(),updated_at=now(),version=version+1 WHERE id=:id
                    """;
            }
            case "reviews" -> {
                status=choice(text(body.get("status")),"OPEN","UNDER_REVIEW","HIDE_REQUESTED","DISMISSED");
                p.addValue("status",status);
                sql="""
                    UPDATE admin_review_reports SET status=:status,decision_note=:note,
                        reviewer_id=:actor,reviewed_at=now(),updated_at=now(),version=version+1 WHERE id=:id
                    """;
            }
            default -> throw new IllegalArgumentException("Module không hỗ trợ cập nhật từ endpoint này.");
        }
        p.addValue("expectedVersion",old.get("version"));
        int changed = jdbc.update(sql.replace("WHERE id=:id", "WHERE id=:id AND version=:expectedVersion"),p);
        if (changed != 1) throw new org.springframework.dao.OptimisticLockingFailureException(
                "Bản ghi đã được người khác cập nhật.");
        audit.record(actorId,"UPDATE_"+status,"ADMIN_"+resource.toUpperCase(Locale.ROOT),id,note);
        return one(resource,id);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> summary() {
        return jdbc.queryForList("""
              SELECT 'pending_verifications' AS metric, COUNT(*) AS value FROM chef_profiles WHERE verification_status='PENDING'
              UNION ALL SELECT 'open_cases',COUNT(*) FROM admin_cases WHERE status IN ('OPEN','IN_REVIEW')
              UNION ALL SELECT 'pending_refunds',COUNT(*) FROM admin_refund_requests WHERE status='PENDING'
              UNION ALL SELECT 'open_tickets',COUNT(*) FROM admin_tickets WHERE status IN ('OPEN','IN_PROGRESS')
              UNION ALL SELECT 'pending_review_reports',COUNT(*) FROM admin_review_reports WHERE status IN ('OPEN','UNDER_REVIEW')
              UNION ALL SELECT 'successful_payment_amount',COALESCE(SUM(amount),0) FROM (
                  SELECT DISTINCT ON (order_id) order_id,amount FROM payment_transactions
                  WHERE status='SUCCESS' ORDER BY order_id,id DESC
              ) AS paid
              """, new MapSqlParameterSource());
    }

    private Map<String,Object> orderById(Long id) {
        List<Map<String,Object>> result=jdbc.queryForList("SELECT * FROM orders WHERE id=:id",
                new MapSqlParameterSource("id",id));
        if(result.isEmpty()) throw new AdminResourceNotFoundException("Đơn hàng không tồn tại.");
        return result.get(0);
    }
    private Source source(String resource) {
        Source s=SOURCES.get(resource);
        if(s==null) throw new IllegalArgumentException("Module Admin không hợp lệ.");
        return s;
    }
    private static String text(Object value) { return value==null?"":value.toString().trim(); }
    private static String defaultValue(Object value,String fallback) {String t=text(value);return t.isEmpty()?fallback:t;}
    private static String required(Object value,String field,int min,int max) {
        String t=text(value);
        if(t.length()<min || t.length()>max) throw new IllegalArgumentException(field+" cần từ "+min+" đến "+max+" ký tự.");
        return t;
    }
    private static String choice(String input,String... choices) {
        for (String valid:choices) if(valid.equals(input)) return valid;
        throw new IllegalArgumentException("Trạng thái/loại không hợp lệ: "+input);
    }
    private static BigDecimal nonNegative(Object input,String label) {
        try { BigDecimal value=new BigDecimal(defaultValue(input,"0"));
            if(value.signum()<0 || value.scale()>2 || value.compareTo(new BigDecimal("999999999999.99"))>0)
                throw new IllegalArgumentException(label+" không hợp lệ.");
            return value;
        } catch(NumberFormatException e) { throw new IllegalArgumentException(label+" phải là số."); }
    }
    private static BigDecimal positive(Object input,String label) {
        BigDecimal value=nonNegative(input,label);
        if(value.signum()==0) throw new IllegalArgumentException(label+" phải lớn hơn 0.");
        return value;
    }
    private static BigDecimal optionalMoney(Object input) { return text(input).isEmpty()?null:nonNegative(input,"Giới hạn giảm"); }
    private static long integer(Object input,long min,long max) {
        try { long n=Long.parseLong(text(input));if(n<min || n>max) throw new IllegalArgumentException("Giới hạn sử dụng không hợp lệ.");return n; }
        catch(NumberFormatException e) {throw new IllegalArgumentException("Giới hạn phải là số nguyên.");}
    }
    private static Long optionalId(Object input) {
        if(text(input).isEmpty()) return null;
        try {long id=Long.parseLong(text(input));if(id<=0) throw new IllegalArgumentException("ID phải dương.");return id;}
        catch(NumberFormatException e){throw new IllegalArgumentException("ID phải là số nguyên.");}
    }
    private static OffsetDateTime date(Object input) {
        try {return OffsetDateTime.parse(text(input));}
        catch(Exception ex) {throw new IllegalArgumentException("Ngày giờ phải ở định dạng ISO 8601 có múi giờ.");}
    }
    private static OffsetDateTime asOffset(Object input) {
        if(input instanceof OffsetDateTime dt) return dt;
        if(input instanceof java.sql.Timestamp ts) return ts.toInstant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        return OffsetDateTime.parse(input.toString());
    }
}
