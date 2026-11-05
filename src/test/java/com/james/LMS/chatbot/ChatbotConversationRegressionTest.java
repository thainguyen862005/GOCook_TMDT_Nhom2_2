package com.james.LMS.chatbot;

import com.james.LMS.publiccontent.PublicChefService;
import com.james.LMS.recommendation.RecommendationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real user-style multi-turn messages; predicted intents are deliberately wrong. */
class ChatbotConversationRegressionTest {
  private final IntentClassifier model = mock(IntentClassifier.class);
  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final PublicChefService chefs = mock(PublicChefService.class);
  private final RecommendationService knn = mock(RecommendationService.class);
  private final ChatbotService chatbot = new ChatbotService(model, new ChatSlots(), jdbc, chefs, knn);

  private void uncertainModel() {
    when(model.predict(anyString())).thenReturn(
        new IntentClassifier.Prediction("OUT_OF_SCOPE", 0.91, 0.52));
  }

  @Test void understandsFullBudgetConversationAndSwitchesToChefSearch() {
    uncertainModel();
    when(chefs.search(eq(""), eq("52"), eq(""), eq(0), eq(4), eq("rating")))
        .thenReturn(Map.of("content", List.of()));

    var a = chatbot.answer("CHo tôi các thực đơn tầm 800k", null, null);
    assertEquals("SEARCH_MENU", a.intent());
    assertEquals(800_000L, a.context().slots().budget());
    assertNull(a.context().slots().guests());
    assertTrue(a.message().contains("bao nhiêu người"));

    var b = chatbot.answer("3 nguoeif", a.context(), null);
    assertEquals("SEARCH_MENU", b.intent());
    assertEquals(3, b.context().slots().guests());
    assertEquals(800_000L, b.context().slots().budget());

    var c = chatbot.answer("giảm xuống còn 100k thì sao", b.context(), null);
    assertEquals("SEARCH_MENU", c.intent());
    assertTrue(c.message().contains("mỗi người"));
    assertEquals(100_000L, c.context().slots().budget());

    var d = chatbot.answer("tức là thực đơn tầm 100k đổ xuống thôi", c.context(), null);
    assertEquals("SEARCH_MENU", d.intent());
    assertEquals(3, d.context().slots().guests());
    assertTrue(d.context().slots().perPerson());
    assertEquals(100_000L, d.context().slots().budget());

    var e = chatbot.answer("Còn trên 100k thì sao", d.context(), null);
    assertEquals("SEARCH_MENU", e.intent());
    assertEquals(3, e.context().slots().guests());
    assertEquals(100_000L, e.context().slots().minUnitPrice());
    assertNull(e.context().slots().budget()); // no contradictory <= 100k ceiling

    var f = chatbot.answer("Cho tôi danh sách các đầu bếp ở Gia Lai", e.context(), null);
    assertEquals("SEARCH_CHEF", f.intent());
    assertEquals("52", f.context().slots().areaCode());
    assertNull(f.context().slots().guests());
    assertNull(f.context().slots().budget());
    assertNull(f.context().slots().minUnitPrice());
    verify(chefs).search("", "52", "", 0, 4, "rating");
  }

  @Test void acceptsGuestCountAndMinimumPriceWithoutCurrencySuffix() {
    uncertainModel();
    var a = chatbot.answer("Tìm món chay dưới 800k", null, null);
    var b = chatbot.answer("3", a.context(), null);
    var c = chatbot.answer("Còn trên 100 thì sao", b.context(), null);
    assertEquals(3, c.context().slots().guests());
    assertEquals(100_000L, c.context().slots().minUnitPrice());
    assertEquals(800_000L, c.context().slots().budget());
  }

  @Test void refundPolicyMustComeFromApprovedFaqOrHelp() {
    uncertainModel();
    var unanswered = chatbot.answer("Bạn có thể nói về chính sách refund được không", null, null);
    assertEquals("FAQ", unanswered.intent());
    assertEquals("/help", unanswered.helpUrl());

    when(jdbc.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class)))
        .thenReturn(List.of(Map.of("question", "Chính sách hoàn tiền",
            "answer", "Nội dung đã được Admin xác nhận trong FAQ.")));
    var approved = chatbot.answer("Bạn có thể nói về chính sách refund được không", null, null);
    assertEquals("FAQ", approved.intent());
    assertEquals("Nội dung đã được Admin xác nhận trong FAQ.", approved.message());
  }

  @Test void doesNotTurnBookingGuidanceIntoChefDiscovery() {
    when(model.predict(anyString())).thenReturn(new IntentClassifier.Prediction("BOOKING_HELP", .85, .5));
    var response = chatbot.answer("Làm sao đặt đầu bếp đến nhà?", null, null);
    assertEquals("BOOKING_HELP", response.intent());
    verifyNoInteractions(chefs);
  }
}
