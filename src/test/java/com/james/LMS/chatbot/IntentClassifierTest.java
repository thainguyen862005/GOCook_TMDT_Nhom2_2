
package com.james.LMS.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntentClassifierTest {

    private final IntentClassifier classifier;

    IntentClassifierTest() throws Exception {
        classifier = new IntentClassifier(new ObjectMapper());
    }

    @Test
    void shouldLoadModelAndPredict() {
        Set<String> validIntents = Set.of(
                "GREETING",
                "SEARCH_MENU",
                "SEARCH_CHEF",
                "RECOMMEND_MENU",
                "CART_HELP",
                "BOOKING_HELP",
                "PAYMENT_HELP",
                "ORDER_HELP",
                "FAQ",
                "CONTACT_ADMIN",
                "OUT_OF_SCOPE"
        );

        String[] questions = {
                "Xin chào",
                "Tìm đầu bếp ở Gia Lai",
                "Thanh toán bằng VNPay như thế nào",
                "Gợi ý món ăn phù hợp với tôi"
        };

        for (String question : questions) {
            var result = classifier.predict(question);

            System.out.printf(
                    "Question: %s%nIntent: %s%nConfidence: %.4f%n%n",
                    question,
                    result.intent(),
                    result.confidence()
            );

            assertTrue(validIntents.contains(result.intent()));
            assertTrue(result.confidence() > 0.0);
            assertTrue(result.confidence() <= 1.0);
        }
    }

    @Test
    void shouldHandleEmptyMessage() {
        var result = classifier.predict("");

        assertEquals("UNKNOWN", result.intent());
        assertEquals(0.0, result.confidence());
    }
}
