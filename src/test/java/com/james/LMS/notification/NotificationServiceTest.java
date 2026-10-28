package com.james.LMS.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.config.SecurityUserDetails;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;

class NotificationServiceTest {
  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final NotificationService service = new NotificationService(jdbc);

  @Test void onlyLocalActionPathsAreAllowed() {
    assertEquals("/orders", NotificationService.safeAction("/orders"));
    assertThrows(IllegalArgumentException.class, () -> NotificationService.safeAction("https://evil.example"));
    assertThrows(IllegalArgumentException.class, () -> NotificationService.safeAction("//evil.example"));
    assertThrows(IllegalArgumentException.class, () -> NotificationService.safeAction("/\\evil"));
    assertThrows(IllegalArgumentException.class, () -> NotificationService.safeAction("/x\ny"));
    assertEquals("/account", NotificationService.safeAction(null));
  }

  @Test void unknownTypeCannotBeSent() {
    assertThrows(IllegalArgumentException.class,
        () -> service.sendToUser(1L,"k","INJECTED","Title","Body","/account"));
    verifyNoInteractions(jdbc);
  }

  @Test void eventKeyAndRecipientAreBoundNotInterpolatedIntoSql() {
    service.sendToUser(24L,"order:1:abc","BOOKING","Order updated","OK","/orders");
    var values=org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("ON CONFLICT (recipient_id,event_key) DO NOTHING"),values.capture());
    assertEquals(24L,values.getValue().getValue("id"));
    assertEquals("order:1:abc", values.getValue().getValue("key"));
  }

  @Test void markReadRequiresMatchingRecipientInUpdate() {
    service.markRead(3L,90L);
    var values=org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("recipient_id=:user"),values.capture());
    assertEquals(3L,values.getValue().getValue("user"));
    assertEquals(90L,values.getValue().getValue("notification"));
  }

  @Test void invalidPaginationRejectedBeforeAnySql() {
    assertThrows(IllegalArgumentException.class,() -> service.inbox(1L,-1,15,false));
    assertThrows(IllegalArgumentException.class,() -> service.inbox(1L,0,501,false));
    verifyNoInteractions(jdbc);
  }

  @Test void adminCampaignMustBeRestricted() throws NoSuchMethodException {
    Method method=NotificationController.class.getMethod("campaign",SecurityUserDetails.class,NotificationController.Campaign.class);
    var annotation=method.getAnnotation(PreAuthorize.class);
    assertNotNull(annotation);
    assertTrue(annotation.value().contains("ROLE_SYSTEM_ADMIN"));
    assertThrows(IllegalArgumentException.class,() -> service.sendCampaign(1L,"ALL","OTHER","Hello","Body"));
    verifyNoInteractions(jdbc);
  }
}
