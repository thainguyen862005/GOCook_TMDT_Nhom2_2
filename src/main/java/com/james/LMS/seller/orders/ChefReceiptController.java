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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Private ingredient receipts. Authenticated delivery; never expose raw Cloudinary public URLs. */
@RestController
@RequestMapping("/api/seller/orders/{orderId}/receipts")
@PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
@RequiredArgsConstructor
public class ChefReceiptController {
  private static final int MAX = 5 * 1024 * 1024;
  private final JdbcTemplate jdbc;
  private final ChefOrderService orders;

  /** The SQL migration creates chef_receipt_blobs. Database storage is private by construction. */
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ChefOrderService.ReceiptRow upload(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long orderId, @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue="") String note) throws java.io.IOException {
    lockOrder(actor.getId(),orderId);
    var order=orders.get(actor.getId(),orderId).order();
    if (!List.of("PREPARING","COOKING").contains(order.status()))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ được thêm hóa đơn khi chuẩn bị hoặc đang nấu.");
    if (!"HELD".equals(order.escrowStatus()))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Khoản thanh toán không còn đang được tạm giữ.");
    if (note.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi chú quá dài.");
    if (file.isEmpty() || file.getSize()>MAX)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hóa đơn phải có dung lượng từ 1 byte đến 5 MB.");
    byte[] bytes=file.getBytes();
    String mime=detect(bytes);
    if (mime==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ nhận ảnh JPG, PNG hoặc WebP hợp lệ.");
    Long count=jdbc.queryForObject("""
        SELECT count(*) FROM order_ingredient_receipts
        WHERE chef_id=? AND order_id=? AND is_active=true
        """,Long.class,actor.getId(),orderId);
    if (count!=null && count>=10) throw new ResponseStatusException(HttpStatus.CONFLICT,"Tối đa 10 hóa đơn cho mỗi đơn.");
    // A soft-deleted receipt still uses secure storage; cap total attempts to avoid unbounded growth.
    Long total=jdbc.queryForObject("""
        SELECT count(*) FROM order_ingredient_receipts WHERE chef_id=? AND order_id=?
        """,Long.class,actor.getId(),orderId);
    if (total!=null && total>=30)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã đạt giới hạn lưu trữ hóa đơn. Liên hệ hỗ trợ.");
    String filename=file.getOriginalFilename()==null ? "hoa-don" : file.getOriginalFilename();
    filename=filename.replaceAll("[\\r\\n\\t/\\\\]","_");
    if (filename.length()>180) filename=filename.substring(0,180);
    Long id=jdbc.queryForObject("""
        INSERT INTO order_ingredient_receipts(order_id,chef_id,file_url,original_filename,mime_type,note)
        VALUES (?,?,'private:pending',?,?,?) RETURNING id
        """,Long.class,orderId,actor.getId(),filename,mime,note.strip());
    String path="/api/seller/orders/"+orderId+"/receipts/"+id+"/file";
    jdbc.update("UPDATE order_ingredient_receipts SET file_url=? WHERE id=?",path,id);
    jdbc.update("INSERT INTO chef_receipt_blobs(receipt_id,content_bytes) VALUES (?,?)",id,bytes);
    return new ChefOrderService.ReceiptRow(id,filename,mime,note.strip(),path);
  }

  @GetMapping("/{receiptId}/file")
  @Transactional(readOnly=true)
  public ResponseEntity<byte[]> file(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long orderId,@PathVariable long receiptId) {
    var records=jdbc.query("""
        SELECT r.mime_type,b.content_bytes FROM order_ingredient_receipts r
        JOIN chef_receipt_blobs b ON b.receipt_id=r.id
        WHERE r.id=? AND r.chef_id=? AND r.order_id=? AND r.is_active=true
        """,(rs,n)->new ReceiptBytes(rs.getString(1),rs.getBytes(2)),receiptId,actor.getId(),orderId);
    if (records.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hóa đơn.");
    var value=records.get(0);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options","nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=hoa-don-"+receiptId)
        .contentType(MediaType.parseMediaType(value.mime())).body(value.content());
  }

  @DeleteMapping("/{receiptId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void remove(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long orderId,@PathVariable long receiptId) {
    lockOrder(actor.getId(),orderId);
    var order=orders.get(actor.getId(),orderId).order();
    if (!List.of("PREPARING","COOKING").contains(order.status()))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể sửa hóa đơn ở trạng thái hiện tại.");
    int changed=jdbc.update("""
        UPDATE order_ingredient_receipts SET is_active=false,version=version+1,
          updated_at=(extract(epoch from now())*1000)::bigint
        WHERE id=? AND chef_id=? AND order_id=? AND is_active=true
        """,receiptId,actor.getId(),orderId);
    if (changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Hóa đơn không tồn tại.");
  }

  private void lockOrder(long chef,long orderId) {
    var rows=jdbc.query("SELECT id FROM orders WHERE id=? AND chef_id=? AND is_active=true FOR UPDATE",
        (rs,n)->rs.getLong(1),orderId,chef);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn đặt nấu.");
  }

  private record ReceiptBytes(String mime,byte[] content) {}
  static String detect(byte[] b) {
    if (b.length>=4 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) {
      try { if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b))!=null) return "image/jpeg"; }
      catch (java.io.IOException ignored) { return null; }
    }
    if (b.length>=8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71
        && b[4]==13 && b[5]==10 && (b[6]&255)==26 && b[7]==10) {
      try { if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b))!=null) return "image/png"; }
      catch (java.io.IOException ignored) { return null; }
    }
    if (b.length>=12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
        && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P'
        && (Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(b,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt())+8)==b.length) return "image/webp";
    return null;
  }
}
