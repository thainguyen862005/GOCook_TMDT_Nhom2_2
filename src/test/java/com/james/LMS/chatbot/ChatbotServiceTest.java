package com.james.LMS.chatbot;

import com.james.LMS.publiccontent.PublicChefService;
import com.james.LMS.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;

class ChatbotServiceTest {
  private final IntentClassifier classifier = mock(IntentClassifier.class);
  private final ChatSlots slots = new ChatSlots();
  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final PublicChefService chefs = mock(PublicChefService.class);
  private final RecommendationService knn = mock(RecommendationService.class);
  private final ChatbotService service = new ChatbotService(classifier, slots, jdbc, chefs, knn);

  @Test void outOfScopeAlwaysFallsBackToHelp() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("OUT_OF_SCOPE", .95, .8));
    var response = service.answer("Giải toán giúp tôi", null, null);
    assertEquals("FALLBACK", response.intent());
    assertEquals("/help", response.helpUrl());
    verifyNoInteractions(jdbc, chefs, knn);
  }

  @Test void publicRecommendationNeverReadsPrivateHistory() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("RECOMMEND_MENU", .9, .6));
    var response = service.answer("Gợi ý món ăn phù hợp với tôi", null, null);
    assertEquals("RECOMMEND_MENU", response.intent());
    verifyNoInteractions(knn);
    assertNotNull(response.message());
  }

  @Test void lowConfidenceFallsBack() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("SEARCH_CHEF", .16, .02));
    var response = service.answer("Tôi không rõ", null, null);
    assertEquals("FALLBACK", response.intent());
    assertEquals("/help", response.helpUrl());
  }

  @Test void customerOrderAdviceDoesNotReadOrModifyOrders() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("ORDER_HELP", .8, .5));
    var response = service.answer("Xem đơn hàng của tôi", null, 123L);
    assertEquals("ORDER_HELP", response.intent());
    verifyNoInteractions(jdbc, chefs, knn);
  }

  @Test void minimumPriceFollowUpInheritsSearchEvenIfClassifierIsUncertain() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("SEARCH_MENU", .88, .5),
      new IntentClassifier.Prediction("OUT_OF_SCOPE", .70, .3));
    var initial = service.answer("Tìm món chay cho 4 người dưới 800k", null, null);
    var followUp = service.answer("Còn trên 100 thì sao", initial.context(), null);
    assertEquals("SEARCH_MENU", followUp.intent());
    assertEquals(Long.valueOf(100000L), followUp.context().slots().minUnitPrice());
    assertEquals(Long.valueOf(800000L), followUp.context().slots().budget());
    assertEquals(Integer.valueOf(4), followUp.context().slots().guests());
  }

  @Test void switchingFromMenuSearchToChefSearchClearsOldFilters() {
    when(classifier.predict(anyString())).thenReturn(
      new IntentClassifier.Prediction("SEARCH_MENU", .91, .60),
      new IntentClassifier.Prediction("SEARCH_CHEF", .89, .55));
    when(chefs.search(eq(""), eq("52"), eq(""), eq(0), eq(4), eq("rating")))
            .thenReturn(Map.of("content", List.of()));
    var initial = service.answer("Tìm món chay cho 4 người dưới 800k", null, null);
    var chef = service.answer("Tìm đầu bếp ở Gia Lai", initial.context(), null);
    assertEquals("SEARCH_CHEF", chef.intent());
    assertNull(chef.context().slots().budget());
    assertNull(chef.context().slots().keyword());
    assertEquals("52", chef.context().slots().areaCode());
    verify(chefs).search("", "52", "", 0, 4, "rating");  }
}
