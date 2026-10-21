package com.james.LMS.admin.exception;

import com.james.LMS.admin.controller.*;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    AdminUserController.class, AdminProductController.class, AdminOrderController.class,
    AdminDashboardController.class, AdminStatisticsController.class,
    com.james.LMS.admin.operations.AdminOperationsController.class,
    com.james.LMS.admin.operations.AdminReportController.class,
    com.james.LMS.site.SiteContentController.class,
    com.james.LMS.admin.media.AdminProductGalleryController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {
  @ExceptionHandler(AdminResourceNotFoundException.class)
  public ResponseEntity<?> notFound(AdminResourceNotFoundException error) {
    return error(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", error.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> badRequest(IllegalArgumentException error) {
    return error(HttpStatus.BAD_REQUEST, "ADMIN_VALIDATION", error.getMessage());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<?> malformed(Exception error) {
    return error(HttpStatus.BAD_REQUEST, "ADMIN_INVALID_REQUEST", "Dữ liệu không đúng định dạng.");
  }

  @ExceptionHandler(com.james.LMS.media.MediaStorageUnavailableException.class)
  public ResponseEntity<?> mediaStorage(com.james.LMS.media.MediaStorageUnavailableException error) {
    return error(HttpStatus.BAD_GATEWAY, "MEDIA_STORAGE_UNAVAILABLE", error.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<?> staleEdit(IllegalStateException error) {
    return error(HttpStatus.CONFLICT, "ADMIN_CONCURRENT_CHANGE", error.getMessage());
  }

  @ExceptionHandler({DataIntegrityViolationException.class,
      OptimisticLockingFailureException.class})
  public ResponseEntity<?> conflict(Exception error) {
    return error(HttpStatus.CONFLICT, "ADMIN_DATA_CONFLICT",
        "Dữ liệu vừa bị thay đổi hoặc không thỏa ràng buộc. Vui lòng tải lại.");
  }

  private ResponseEntity<?> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "success", false, "errorCode", code, "message", message));
  }
}
