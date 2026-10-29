package com.james.LMS.seller.orders;

import com.james.LMS.config.SecurityUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Only the purchaser of an order can list/view its private images. */
@RestController
@RequestMapping("/api/customer/orders/{orderId}/evidence")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class CustomerEvidenceController {
  private final JdbcTemplate jdbc;

  public record Evidence(long id, String filename, String mimeType, String note, String evidenceType) {}
  private record Photo(String mime, byte[] content) {}

  private void ensureOwner(long customer,long order) {
    Boolean allowed=jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM orders o
        WHERE o.id=? AND o.customer_id=? AND o.is_active=true)
        """,Boolean.class,order,customer);
    if (!Boolean.TRUE.equals(allowed))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn đặt nấu.");
  }

  @GetMapping
  @Transactional(readOnly=true)
  public List<Evidence> list(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long orderId) {
    ensureOwner(actor.getId(),orderId);
    return jdbc.query("""
        SELECT id, original_filename, mime_type, note, evidence_type
        FROM order_ingredient_receipts WHERE order_id=? AND is_active=true
        ORDER BY created_at ASC, id ASC
        """,(rs,n)->new Evidence(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),orderId);
  }

  @GetMapping("/{evidenceId}/file")
  @Transactional(readOnly=true)
  public ResponseEntity<byte[]> file(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long orderId,@PathVariable long evidenceId) {
    ensureOwner(actor.getId(),orderId);
    var result=jdbc.query("""
        SELECT r.mime_type,b.content_bytes FROM order_ingredient_receipts r
        JOIN chef_receipt_blobs b ON b.receipt_id=r.id
        WHERE r.id=? AND r.order_id=? AND r.is_active=true
        """,(rs,n)->new Photo(rs.getString(1),rs.getBytes(2)),evidenceId,orderId);
    if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ảnh.");
    var photo=result.get(0);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options","nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=bang-chung-"+evidenceId)
        .contentType(MediaType.parseMediaType(photo.mime())).body(photo.content());
  }
}
