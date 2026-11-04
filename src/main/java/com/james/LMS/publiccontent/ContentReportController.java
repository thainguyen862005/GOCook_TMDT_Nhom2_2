package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Role-bound abuse reports. Moderation is a human action, never an automatic suspension. */
@RestController
public class ContentReportController {
  private final NamedParameterJdbcTemplate jdbc;
  public ContentReportController(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
  public record ReportInput(@NotBlank String targetType,@NotNull @Positive Long targetId,
     @NotBlank @Size(min=15,max=1500) String reason){}
  public record Decision(@NotBlank String status,@NotBlank @Size(min=10,max=2000) String resolution){}
  private static MapSqlParameterSource p(Object...x){var a=new MapSqlParameterSource();for(int i=0;i<x.length;i+=2)a.addValue((String)x[i],x[i+1]);return a;}
  private static long id(SecurityUserDetails a){if(a==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return a.getId();}
  private static void page(int page,int size){if(page<0||page>10000||size<1||size>30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}

  @PostMapping("/api/reports/content")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> report(@AuthenticationPrincipal SecurityUserDetails actor,@Valid @RequestBody ReportInput input){
    long reporter=id(actor), target=input.targetId();String type=input.targetType();
    if(!List.of("CHEF","CUSTOMER","MENU","QUESTION").contains(type))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Đối tượng báo cáo không hợp lệ.");
    String reason=input.reason().trim();if(reason.length()<15)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lý do quá ngắn.");
    Long orderId=null;
    if("CHEF".equals(type)) {
      Long count=jdbc.queryForObject("SELECT count(*) FROM chef_profiles WHERE user_id=:id AND is_active=true",p("id",target),Long.class);
      if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy Chef.");
    } else if("MENU".equals(type)) {
      Long count=jdbc.queryForObject("SELECT count(*) FROM products WHERE id=:id AND status='PUBLISHED' AND is_active=true",p("id",target),Long.class);
      if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thực đơn.");
    } else if("QUESTION".equals(type)) {
      Long count=jdbc.queryForObject("SELECT count(*) FROM menu_questions WHERE id=:id AND status='ANSWERED'",p("id",target),Long.class);
      if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hỏi đáp.");
    } else {
      // For CUSTOMER reports, the supplied ID is an order ID. The real target is derived server-side.
      Map<String,Object> order=jdbc.query("""
        SELECT id,customer_id FROM orders WHERE id=:id AND chef_id=:chef AND customer_id IS NOT NULL
        """,p("id",target,"chef",reporter),rs->rs.next()?Map.of("id",rs.getLong(1),"customer",rs.getLong(2)):null);
      if(order==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ được báo cáo khách hàng thuộc đơn của bạn.");
      orderId=((Number)order.get("id")).longValue(); target=((Number)order.get("customer")).longValue();
    }
    if(reporter==target && ("CHEF".equals(type)||"CUSTOMER".equals(type)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Không thể tự báo cáo bản thân.");
    Long recent=jdbc.queryForObject("SELECT count(*) FROM content_reports WHERE reporter_id=:uid AND created_at>now()-interval '24 hours'",p("uid",reporter),Long.class);
    if(recent!=null&&recent>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn đã gửi quá nhiều báo cáo hôm nay.");
    Long active=jdbc.queryForObject("""
      SELECT count(*) FROM content_reports WHERE reporter_id=:uid AND target_type=:type
        AND target_id=:target AND status IN ('OPEN','IN_REVIEW')
      """,p("uid",reporter,"type",type,"target",target),Long.class);
    if(active!=null&&active>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Bạn đã báo cáo đối tượng này và đang chờ xử lý.");
    try {
      Long newId=jdbc.queryForObject("""
        INSERT INTO content_reports(reporter_id,target_type,target_id,order_id,reason)
        VALUES (:uid,:type,:target,:order,:reason) RETURNING id
        """,p("uid",reporter,"type",type,"target",target,"order",orderId,"reason",reason),Long.class);
      return Map.of("id",newId,"status","OPEN");
    } catch (DataIntegrityViolationException conflict) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo cáo trùng hoặc dữ liệu đối tượng không hợp lệ.");
    }
  }

  @GetMapping("/api/reports/content/mine")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String,Object>> mine(@AuthenticationPrincipal SecurityUserDetails actor){
    return jdbc.queryForList("""
      SELECT id,target_type AS "targetType",target_id AS "targetId",reason,status,
        resolution,created_at AS "createdAt" FROM content_reports
      WHERE reporter_id=:uid ORDER BY id DESC LIMIT 100
      """,p("uid",id(actor)));
  }

  @GetMapping("/api/admin/content-reports")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public Map<String,Object> all(@RequestParam(defaultValue="") String status,
             @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
    page(page,size);if(!status.isEmpty()&&!List.of("OPEN","IN_REVIEW","RESOLVED","DISMISSED").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var a=p("status",status,"limit",size,"offset",(long)page*size);
    String where=" FROM content_reports WHERE (:status='' OR status=:status) ";
    Long total=jdbc.queryForObject("SELECT count(*)"+where,a,Long.class);
    var content=jdbc.queryForList("""
      SELECT id,reporter_id AS "reporterId",target_type AS "targetType",target_id AS "targetId",
        order_id AS "orderId",reason,status,resolution,created_at AS "createdAt"
      """+where+" ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",a);
    return Map.of("content",content,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
  }

  @PatchMapping("/api/admin/content-reports/{reportId}")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @Transactional
  public Map<String,Object> decide(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long reportId,
                                   @Valid @RequestBody Decision decision){
    if(!List.of("IN_REVIEW","RESOLVED","DISMISSED").contains(decision.status()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ.");
    String reason=decision.resolution().trim();if(reason.length()<10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi chú xử lý quá ngắn.");
    int changed=jdbc.update("""
      UPDATE content_reports SET status=:status,resolution=:note,resolved_by=:admin,
      resolved_at=CASE WHEN :status IN ('RESOLVED','DISMISSED') THEN now() ELSE NULL END,updated_at=now()
      WHERE id=:id AND status IN ('OPEN','IN_REVIEW')
      """,p("status",decision.status(),"note",reason,"admin",id(actor),"id",reportId));
    if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo cáo không tồn tại hoặc đã được đóng.");
    return Map.of("status",decision.status());
  }
}
