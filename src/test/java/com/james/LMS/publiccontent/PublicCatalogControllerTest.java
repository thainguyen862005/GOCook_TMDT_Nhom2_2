package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PublicCatalogControllerTest {
  @Mock PublicChefService chefs;
  @Mock JdbcTemplate jdbc;
  @InjectMocks PublicCatalogController controller;

  @Test
  void offersQueryDoesNotReturnDisabledOrExpiredCodes() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of());
    assertTrue(controller.offers().isEmpty());
    verify(jdbc).queryForList(argThat(query -> query.contains("is_active=true")
        && query.contains("starts_at <= now()") && query.contains("ends_at > now()")
        && query.contains("used_count < usage_limit")));
  }
}
