package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public Q&A is separate from verified order ratings: unanswered questions are not public. */
@RestController
public class MenuQuestionController {
  private final NamedParameterJdbcTemplate jdbc;
  public MenuQuestionController(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
  public record Ask(@NotBlank @Size(min=10,max=1500) String question){}
  public record Answer(@NotBlank @Size(min=5,max=2000) String answer){}
  private static MapSqlParameterSource p(Object...pairs){var a=new MapSqlParameterSource();for(int i=0;i<pairs.length;i+=2)a.addValue((String)pairs[i],pairs[i+1]);return a;}
  private static long id(SecurityUserDetails user){if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return user.getId();}
  private static void page(int page,int size){if(page<0||page>10000||size<1||size>30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phân trang không hợp lệ.");}

  @GetMapping("/api/public/menus/{productId}/questions")
  public Map<String,Object> publicList(@PathVariable long productId,@RequestParam(defaultValue="0") int page,
                                       @RequestParam(defaultValue="10") int size){
    page(page,size);
    var a=p("product",productId,"limit",size,"offset",(long)page*size);
    Long total=jdbc.queryForObject("""
       SELECT count(*) FROM menu_questions q JOIN products p ON p.id=q.product_id
       WHERE q.product_id=:product AND q.status='ANSWERED' AND p.status='PUBLISHED' AND p.is_active=true
       """,a,Long.class);
    var content=jdbc.queryForList("""
       SELECT q.id,q.question,q.answer,q.created_at AS "createdAt",q.answered_at AS "answeredAt",
         cp.display_name AS "chefName"
       FROM menu_questions q JOIN products p ON p.id=q.product_id
       LEFT JOIN chef_profiles cp ON cp.user_id=q.chef_id
       WHERE q.product_id=:product AND q.status='ANSWERED' AND p.status='PUBLISHED' AND p.is_active=true
       ORDER BY q.answered_at DESC,q.id DESC LIMIT :limit OFFSET :offset
       """,a);
    return Map.of("content",content,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
  }

  @PostMapping("/api/customer/menus/{productId}/questions")
  @PreAuthorize("hasAuthority('ROLE_USER')")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> ask(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long productId,
                                @Valid @RequestBody Ask input){
    long uid=id(actor);
    String question=input.question().trim();
    if(question.length()<10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Câu hỏi cần ít nhất 10 ký tự.");
    Long seller=jdbc.query("""
      SELECT seller_id FROM products WHERE id=:id AND is_active=true AND status='PUBLISHED' AND seller_id IS NOT NULL
      """,p("id",productId),rs->rs.next()?rs.getLong(1):null);
    if(seller==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Thực đơn không còn công khai.");
    if(seller==uid)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không tự hỏi đáp thực đơn của mình.");
    Long recent=jdbc.queryForObject("""
       SELECT count(*) FROM menu_questions WHERE customer_id=:user AND created_at>now()-interval '24 hours'
       """,p("user",uid),Long.class);
    if(recent!=null&&recent>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn đã gửi nhiều câu hỏi hôm nay.");
    Long id=jdbc.queryForObject("""
      INSERT INTO menu_questions(product_id,customer_id,chef_id,question)
      VALUES (:product,:user,:chef,:question) RETURNING id
      """,p("product",productId,"user",uid,"chef",seller,"question",question),Long.class);
    return Map.of("id",id,"status","PENDING");
  }

  @GetMapping("/api/customer/menu-questions")
  @PreAuthorize("hasAuthority('ROLE_USER')")
  public List<Map<String,Object>> mine(@AuthenticationPrincipal SecurityUserDetails actor){
    return jdbc.queryForList("""
      SELECT q.id,q.product_id AS "productId",p.name AS "menuName",q.question,q.answer,q.status,
        q.created_at AS "createdAt" FROM menu_questions q JOIN products p ON p.id=q.product_id
      WHERE q.customer_id=:user ORDER BY q.id DESC LIMIT 100
      """,p("user",id(actor)));
  }

  @GetMapping("/api/seller/menu-questions")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public Map<String,Object> chef(@AuthenticationPrincipal SecurityUserDetails actor,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int size){
    page(page,size);if(!status.isEmpty()&&!List.of("PENDING","ANSWERED","HIDDEN").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc không hợp lệ.");
    var a=p("chef",id(actor),"status",status,"limit",size,"offset",(long)page*size);
    String where=" FROM menu_questions q JOIN products p ON p.id=q.product_id WHERE q.chef_id=:chef AND (:status='' OR q.status=:status) ";
    Long total=jdbc.queryForObject("SELECT count(*)"+where,a,Long.class);
    var content=jdbc.queryForList("""
      SELECT q.id,q.product_id AS "productId",p.name AS "menuName",q.question,q.answer,
        q.status,q.created_at AS "createdAt"
      """+where+" ORDER BY q.created_at DESC,q.id DESC LIMIT :limit OFFSET :offset",a);
    return Map.of("content",content,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
  }

  @PatchMapping("/api/seller/menu-questions/{questionId}/answer")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  @Transactional
  public Map<String,Object> answer(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long questionId,
                                   @Valid @RequestBody Answer input){
    String answer=input.answer().trim();
    if(answer.length()<5)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trả lời quá ngắn.");
    int changed=jdbc.update("""
      UPDATE menu_questions SET answer=:answer,status='ANSWERED',answered_at=now(),updated_at=now()
      WHERE id=:id AND chef_id=:chef AND status='PENDING'
      """,p("answer",answer,"id",questionId,"chef",id(actor)));
    if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Câu hỏi không tồn tại hoặc đã được xử lý.");
    return Map.of("status","ANSWERED");
  }

  @GetMapping("/api/admin/menu-questions")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public Map<String,Object> admin(@RequestParam(defaultValue="") String status,
       @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
    page(page,size);if(!status.isEmpty()&&!List.of("PENDING","ANSWERED","HIDDEN").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var a=p("status",status,"limit",size,"offset",(long)page*size);
    String where=" FROM menu_questions q JOIN products p ON p.id=q.product_id WHERE (:status='' OR q.status=:status) ";
    Long total=jdbc.queryForObject("SELECT COUNT(*)"+where,a,Long.class);
    var content=jdbc.queryForList("""
       SELECT q.id,p.name AS "menuName",q.question,q.answer,q.status,q.created_at AS "createdAt"
       """+where+" ORDER BY q.id DESC LIMIT :limit OFFSET :offset",a);
    return Map.of("content",content,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
  }

  @PatchMapping("/api/admin/menu-questions/{questionId}/visibility")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @Transactional
  public Map<String,Object> moderate(@PathVariable long questionId,@RequestParam boolean hidden){
    int count=jdbc.update("""
      UPDATE menu_questions SET status=CASE WHEN :hidden THEN 'HIDDEN'
        WHEN answer IS NULL THEN 'PENDING' ELSE 'ANSWERED' END,updated_at=now() WHERE id=:id
      """,p("hidden",hidden,"id",questionId));
    if(count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return Map.of("status",hidden?"HIDDEN":"VISIBLE");
  }
}
