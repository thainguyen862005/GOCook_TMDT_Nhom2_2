
package com.james.LMS.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class IntentClassifier {
    public record Prediction(String intent, double confidence, double margin) {
    }

    private final String[] classes;
    private final Map<String, Integer> vocabulary;
    private final double[] idf;
    private final double[][] weights;
    private final double[] bias;

    private final int minNgram;
    private final int maxNgram;
    private final boolean lowercase;
    private final boolean sublinearTf;

    public IntentClassifier(ObjectMapper mapper) throws IOException {
        var resource = new ClassPathResource("chatbot/intent-model.json");

        JsonNode root;

        try (var stream = resource.getInputStream()) {
            root = mapper.readTree(stream);
        }

        if (!"tfidf_logistic_regression".equals(root.path("model_type").asText())) {
            throw new IOException("Unsupported chatbot model");
        }

        JsonNode vectorizer = root.path("vectorizer");
        JsonNode classifier = root.path("classifier");

        if (!"char".equals(vectorizer.path("analyzer").asText())
                || !"l2".equals(vectorizer.path("norm").asText())) {
            throw new IOException("Unsupported TF-IDF settings");
        }

        minNgram = vectorizer.path("ngram_min").asInt();
        maxNgram = vectorizer.path("ngram_max").asInt();
        lowercase = vectorizer.path("lowercase").asBoolean();
        sublinearTf = vectorizer.path("sublinear_tf").asBoolean();

        JsonNode classNodes = root.path("classes");
        JsonNode featureNodes = vectorizer.path("features");
        JsonNode idfNodes = vectorizer.path("idf");
        JsonNode weightNodes = classifier.path("weights");
        JsonNode biasNodes = classifier.path("bias");

        if (!classNodes.isArray()
                || !featureNodes.isArray()
                || !idfNodes.isArray()
                || !weightNodes.isArray()
                || !biasNodes.isArray()) {
            throw new IOException("Invalid model JSON arrays");
        }

        int classCount = classNodes.size();
        int featureCount = featureNodes.size();

        if (classCount == 0 || featureCount == 0
                || minNgram < 1 || maxNgram < minNgram
                || idfNodes.size() != featureCount
                || weightNodes.size() != classCount
                || biasNodes.size() != classCount) {
            throw new IOException("Model dimensions mismatch");
        }

        classes = new String[classCount];
        vocabulary = new HashMap<>();
        idf = new double[featureCount];
        weights = new double[classCount][featureCount];
        bias = new double[classCount];

        for (int i = 0; i < classCount; i++) {
            classes[i] = classNodes.get(i).asText();
            bias[i] = biasNodes.get(i).asDouble();
        }

        for (int j = 0; j < featureCount; j++) {
            String token = featureNodes.get(j).asText();
            vocabulary.put(token, j);
            idf[j] = idfNodes.get(j).asDouble();
        }

        for (int i = 0; i < classCount; i++) {
            JsonNode row = weightNodes.get(i);

            if (!row.isArray() || row.size() != featureCount) {
                throw new IOException("Invalid classifier weight row");
            }

            for (int j = 0; j < featureCount; j++) {
                weights[i][j] = row.get(j).asDouble();
            }
        }
    }

    public Prediction predict(String message) {
        if (message == null || message.isBlank()) {
            return new Prediction("UNKNOWN", 0.0, 0.0);
        }

        String text = lowercase
                ? message.toLowerCase(Locale.ROOT)
                : message;

        text = text.replaceAll("\\s\\s+", " ");

        int[] codePoints = text.codePoints().toArray();

        Map<Integer, Integer> counts = new HashMap<>();

        for (int n = minNgram; n <= maxNgram; n++) {
            for (int start = 0;
                 start + n <= codePoints.length;
                 start++) {

                String token = new String(codePoints, start, n);
                Integer index = vocabulary.get(token);

                if (index != null) {
                    counts.merge(index, 1, Integer::sum);
                }
            }
        }

        if (counts.isEmpty()) {
            return new Prediction("UNKNOWN", 0.0, 0.0);
        }

        // TF-IDF: calculate term frequencies and L2 normalization.
        Map<Integer, Double> tfidf = new HashMap<>();
        double squaredNorm = 0.0;

        for (var entry : counts.entrySet()) {
            int index = entry.getKey();
            int frequency = entry.getValue();

            double tf = sublinearTf
                    ? 1.0 + Math.log(frequency)
                    : frequency;

            double value = tf * idf[index];

            tfidf.put(index, value);
            squaredNorm += value * value;
        }

        double norm = Math.sqrt(squaredNorm);

        // Logistic Regression logits.
        double[] scores = Arrays.copyOf(bias, bias.length);

        for (var entry : tfidf.entrySet()) {
            int index = entry.getKey();
            double value = entry.getValue() / norm;

            for (int c = 0; c < classes.length; c++) {
                scores[c] += weights[c][index] * value;
            }
        }

        // Softmax probabilities.
        double maxScore = Arrays.stream(scores)
                .max().orElse(0.0);

        double sum = 0.0;

        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.exp(scores[i] - maxScore);
            sum += scores[i];
        }

        int bestIndex = 0;
        double bestProbability = -1.0;
        double secondProbability = 0.0;

        for (int i = 0; i < scores.length; i++) {
            double probability = scores[i] / sum;

            if (probability > bestProbability) {
                secondProbability = bestProbability;
                bestProbability = probability;
                bestIndex = i;
            } else if (probability > secondProbability) {
                secondProbability = probability;
            }
        }

        return new Prediction(
                classes[bestIndex],
                bestProbability,
                bestProbability - Math.max(0.0, secondProbability)
        );
    }
}
