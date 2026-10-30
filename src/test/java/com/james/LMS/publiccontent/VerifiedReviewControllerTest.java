package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.james.LMS.config.SecurityUserDetails;

class VerifiedReviewControllerTest {
  private final NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
  private final VerifiedReviewController ctrl=new VerifiedReviewController(db);
  private final SecurityUserDetails buyer=SecurityUserDetails.builder().id(10L).build();
  @Test void onlyCompletedPaidRealOrderMayBeReviewedOnce() {
    when(db.queryForList(contains("INSERT INTO order_reviews"), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of(900L));
    var response=ctrl.create(buyer,101L,new VerifiedReviewController.Input(5,"Món ngon, giao đúng hẹn."));
    assertEquals("PUBLISHED",response.get("status"));
    verify(db).update(contains("UPDATE chef_profiles"),any(MapSqlParameterSource.class));
  }
  @Test void duplicateOrUnfinishedOrderDoesNotPublishReview() {
    when(db.queryForList(contains("INSERT INTO order_reviews"), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of());
    var error=assertThrows(ResponseStatusException.class,()->
        ctrl.create(buyer,102L,new VerifiedReviewController.Input(4,"Phục vụ đúng giờ, ổn định.")));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    verify(db,never()).update(anyString(),any(MapSqlParameterSource.class));
  }
  @Test void rejectsOnlyWhitespaceEvenIfRequestObjectBypassesValidation() {
    assertThrows(ResponseStatusException.class,()->ctrl.create(buyer,101L,new VerifiedReviewController.Input(5,"                ")));
    verifyNoInteractions(db);
  }
}
