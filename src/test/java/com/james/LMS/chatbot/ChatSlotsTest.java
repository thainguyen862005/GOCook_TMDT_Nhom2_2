package com.james.LMS.chatbot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatSlotsTest {
  private final ChatSlots slots = new ChatSlots();

  @Test void readsVietnameseBudgetGuestsArea() {
    var v = slots.parse("Tìm món chay cho 4 người dưới 800k ở Gia Lai", null);
    assertEquals(4, v.guests());
    assertEquals(800000L, v.budget());
    assertEquals("52", v.areaCode());
    assertEquals("chay", v.keyword());
    assertFalse(v.perPerson());
  }

  @Test void handlesPerPersonAndFollowUp() {
    var a = slots.parse("Món chay cho 5 người", null);
    var b = slots.parse("dưới 200k/người", a);
    assertEquals(5, b.guests());
    assertEquals(200000L, b.budget());
    assertTrue(b.perPerson());
  }

  @Test void rejectsAbsurdValues() {
    var v = slots.parse("cho 999 người dưới 999 triệu", null);
    assertNull(v.guests());
    assertNull(v.budget());
  }

  @Test void understandsMinimumPriceFollowUpWithoutLosingBudget() {
    var original = slots.parse("Tìm món chay cho 4 người dưới 800k", null);
    var followUp = slots.parse("Còn trên 100 thì sao", original);
    assertEquals(Integer.valueOf(4), followUp.guests());
    assertEquals(Long.valueOf(800000L), followUp.budget());
    assertEquals(Long.valueOf(100000L), followUp.minUnitPrice());
    assertEquals("chay", followUp.keyword());

    var clarified = slots.parse("ý là giá trên 100k thì sao", original);
    assertEquals(Long.valueOf(100000L), clarified.minUnitPrice());
    assertEquals(Long.valueOf(800000L), clarified.budget());
  }
}
