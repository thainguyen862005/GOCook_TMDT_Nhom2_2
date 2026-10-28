package com.james.LMS.seller.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class ChefOrderServiceTest {
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  final ChefOrderService service=new ChefOrderService(jdbc);

  @Test void invalidTransitionsCannotTouchDatabase() {
    var bad=new ChefOrderService.Transition("PENDING","COMPLETED",1L,"");
    var e=assertThrows(ResponseStatusException.class,()->service.transition(2L,4L,bad));
    assertEquals(HttpStatus.CONFLICT,e.getStatusCode());
    verifyNoInteractions(jdbc);
  }

  @Test void chefCannotMarkCustomerAcceptanceOrCancelOrder() {
    for(String target:List.of("COMPLETED","CANCELLED")) {
      var request=new ChefOrderService.Transition("AWAITING_ACCEPTANCE",target,0L,"");
      assertThrows(ResponseStatusException.class,()->service.transition(2L,4L,request));
    }
    verifyNoInteractions(jdbc);
  }

  @Test void staleOrderVersionsAreRejected() {
    doReturn(List.of(5L)).when(jdbc).query(contains("FOR UPDATE OF u"),any(RowMapper.class),eq(5L));
    var actual=new ChefOrderService.OrderRow(11,9,"GC-100","Customer","Menu","MENU",null,null,null,
        2,LocalDateTime.now().plusDays(3),120,new BigDecimal("500000"),"PENDING","HELD",null,0L,null);
    doReturn(List.of(actual)).when(jdbc).query(contains("FOR UPDATE OF o"),any(RowMapper.class),eq(5L),eq(11L));
    var request=new ChefOrderService.Transition("PENDING","CONFIRMED",8L,"");
    var error=assertThrows(ResponseStatusException.class,()->service.transition(5L,11L,request));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    verify(jdbc,never()).update(contains("UPDATE orders"),any(Object[].class));
  }

  @Test void unpaidOrdersCannotBeAccepted() {
    doReturn(List.of(5L)).when(jdbc).query(contains("FOR UPDATE OF u"),any(RowMapper.class),eq(5L));
    var unpaid=new ChefOrderService.OrderRow(12,0,"GC-UNPAID","Buyer","Menu","MENU",null,null,null,
        3,LocalDateTime.now().plusDays(2),120,new BigDecimal("300000"),"PENDING",null,null,0L,null);
    doReturn(List.of(unpaid)).when(jdbc).query(contains("FOR UPDATE OF o"),any(RowMapper.class),eq(5L),eq(12L));
    doReturn(List.of()).when(jdbc).query(contains("FROM order_escrows WHERE order_id"),any(RowMapper.class),eq(12L));
    var request=new ChefOrderService.Transition("PENDING","CONFIRMED",0L,"");
    var error=assertThrows(ResponseStatusException.class,()->service.transition(5L,12L,request));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }

  @Test void listRejectsExcessivePageSizesAndInvalidStatuses() {
    assertThrows(ResponseStatusException.class,()->service.list(5L,"",0,51,null,null));
    assertThrows(ResponseStatusException.class,()->service.list(5L,"NOT_A_STATUS",0,10,null,null));
    verifyNoInteractions(jdbc);
  }
}
