
package com.james.LMS.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class IntentClassifierParityTest {

    @Test
    void javaShouldMatchPythonPredictions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        IntentClassifier classifier = new IntentClassifier(mapper);

        try (InputStream stream = getClass()
                .getClassLoader()
                .getResourceAsStream("chatbot/parity_cases.json")) {

            assertNotNull(stream, "Missing parity_cases.json");

            JsonNode cases = mapper.readTree(stream);

            assertTrue(cases.isArray());
            assertEquals(48, cases.size());

            int passed = 0;

            for (JsonNode item : cases) {
                String message = item.path("text").asText();
                String expectedIntent = item.path("intent").asText();

                double expectedConfidence =
                        item.path("confidence").asDouble();

                double expectedMargin =
                        item.path("margin").asDouble();

                var actual = classifier.predict(message);

                assertEquals(
                        expectedIntent,
                        actual.intent(),
                        "Intent mismatch for: " + message
                );

                assertEquals(
                        expectedConfidence,
                        actual.confidence(),
                        1e-5,
                        "Confidence mismatch for: " + message
                );

                assertEquals(
                        expectedMargin,
                        actual.margin(),
                        1e-5,
                        "Margin mismatch for: " + message
                );

                passed++;
            }

            System.out.println(
                    "Python/Java parity passed: " + passed
            );
        }
    }
}
