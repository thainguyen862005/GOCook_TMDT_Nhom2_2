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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PublicChefServiceTest {
  @Mock NamedParameterJdbcTemplate jdbc;
  @InjectMocks PublicChefService service;

  @Test
  void rejectsUnsafePaginationBeforeQuery() {
    assertThrows(IllegalArgumentException.class, () -> service.search("", "", "", -1, 12, "rating"));
    assertThrows(IllegalArgumentException.class, () -> service.search("", "", "", 0, 100, "rating"));
    assertThrows(IllegalArgumentException.class, () -> service.search("x".repeat(101), "", "", 0, 12, "rating"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void searchUsesApprovedAndActiveFiltersAndSafeBindParameters() {
    when(jdbc.queryForObject(contains("SELECT COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(60L);
    when(jdbc.queryForList(contains("ORDER BY"), any(MapSqlParameterSource.class)))
        .thenReturn(List.of(Map.of("id", 7L, "displayName", "Bếp Demo")));
    var result = service.search("Bếp", "Hà Nội", "Món chay", 0, 12, "rating");
    assertEquals(60L, result.get("totalElements"));
    assertEquals(5L, result.get("totalPages"));
    verify(jdbc).queryForList(argThat(sql -> sql.contains("cp.is_verified = true")
        && sql.contains("cp.verification_status = 'APPROVED'")
        && sql.contains("u.is_active = true") && sql.contains("LIMIT :limit")), any(MapSqlParameterSource.class));
  }
}
