package com.james.LMS.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.service.PaymentGateway;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VnpayPaymentGateway implements PaymentGateway {

  private static final String VERSION = "2.1.0";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private static final Set<String> FAILURE_CODES =
      Set.of("09", "10", "11", "12", "13", "51", "65", "75", "79", "99");

  private static final List<String> QUERY_RESPONSE_FIELDS =
      List.of(
          "vnp_ResponseId",
          "vnp_Command",
          "vnp_ResponseCode",
          "vnp_Message",
          "vnp_TmnCode",
          "vnp_TxnRef",
          "vnp_Amount",
          "vnp_BankCode",
          "vnp_PayDate",
          "vnp_TransactionNo",
          "vnp_TransactionType",
          "vnp_TransactionStatus",
          "vnp_OrderInfo",
          "vnp_PromotionCode",
          "vnp_PromotionAmount");

  private final PaymentProperties.Vnpay config;
  private final ObjectMapper mapper;
  private final HttpClient http;

  @Autowired
  public VnpayPaymentGateway(PaymentProperties properties, ObjectMapper mapper) {
    this(properties, mapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
  }

  VnpayPaymentGateway(PaymentProperties properties, ObjectMapper mapper, HttpClient http) {
    this.config = properties.getVnpay();
    this.mapper = mapper;
    this.http = http;
  }

  @Override
  public String merchantCode() {
    return config.getTmnCode();
  }

  @Override
  public String createPaymentUrl(PaymentRequest request) {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Version", VERSION);
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", config.getTmnCode());
    params.put("vnp_Amount", toGatewayAmount(request.amount()));
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_TxnRef", request.txnRef());
    params.put("vnp_OrderInfo", request.orderInfo());
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    params.put("vnp_ReturnUrl", request.returnUrl());
    params.put("vnp_IpAddr", request.ipAddress());
    params.put("vnp_CreateDate", request.createdAt().format(GATEWAY_DATE));
    params.put("vnp_ExpireDate", request.expiresAt().format(GATEWAY_DATE));

    String signedData = canonical(params);
    return config.getPayUrl() + "?" + signedData + "&vnp_SecureHash=" + sign(signedData);
  }

  @Override
  public Optional<GatewayTransaction> verifyCallback(Map<String, String> params) {
    String received = params.get("vnp_SecureHash");
    if (received == null || received.isBlank()) {
      return Optional.empty();
    }

    Map<String, String> signed = new TreeMap<>();
    params.forEach(
        (name, value) -> {
          boolean isGatewayField = name.startsWith("vnp_");
          boolean isSignature = name.equals("vnp_SecureHash") || name.equals("vnp_SecureHashType");
          if (isGatewayField && !isSignature) {
            signed.put(name, value);
          }
        });
    if (!sameHash(sign(canonical(signed)), received)) {
      return Optional.empty();
    }

    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");
    return Optional.of(
        new GatewayTransaction(
            params.get("vnp_TxnRef"),
            fromGatewayAmount(params.get("vnp_Amount")),
            params.get("vnp_TmnCode"),
            callbackOutcome(responseCode, transactionStatus),
            params.get("vnp_TransactionNo"),
            params.get("vnp_PayDate"),
            responseCode,
            transactionStatus));
  }

  @Override
  public GatewayTransaction query(String txnRef, String transactionDate, String ipAddress) {
    String requestId = UUID.randomUUID().toString().replace("-", "");
    String createDate = LocalDateTime.now(GATEWAY_ZONE).format(GATEWAY_DATE);
    String orderInfo = "Kiem tra ket qua GD " + txnRef;
    String signedData =
        String.join(
            "|",
            requestId,
            VERSION,
            "querydr",
            config.getTmnCode(),
            txnRef,
            transactionDate,
            createDate,
            ipAddress,
            orderInfo);

    Map<String, String> body = new LinkedHashMap<>();
    body.put("vnp_RequestId", requestId);
    body.put("vnp_Version", VERSION);
    body.put("vnp_Command", "querydr");
    body.put("vnp_TmnCode", config.getTmnCode());
    body.put("vnp_TxnRef", txnRef);
    body.put("vnp_OrderInfo", orderInfo);
    body.put("vnp_TransactionDate", transactionDate);
    body.put("vnp_CreateDate", createDate);
    body.put("vnp_IpAddr", ipAddress);
    body.put("vnp_SecureHash", sign(signedData));

    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(config.getApiUrl()))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        log.warn("vnpay querydr txnRef={} http={}", txnRef, response.statusCode());
        return unknown(txnRef);
      }
      return parseQueryResponse(txnRef, response.body());
    } catch (IOException e) {
      log.warn("vnpay querydr txnRef={} failed={}", txnRef, e.getClass().getSimpleName());
      return unknown(txnRef);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return unknown(txnRef);
    }
  }

  GatewayTransaction parseQueryResponse(String txnRef, String json) {
    JsonNode node;
    try {
      node = mapper.readTree(json);
    } catch (IOException e) {
      log.warn("vnpay querydr txnRef={} unreadable response", txnRef);
      return unknown(txnRef);
    }

    Map<String, String> fields = new LinkedHashMap<>();
    QUERY_RESPONSE_FIELDS.forEach(name -> fields.put(name, node.path(name).asText("")));

    String signedData = String.join("|", fields.values());
    if (!sameHash(sign(signedData), node.path("vnp_SecureHash").asText(""))) {
      log.warn("vnpay querydr txnRef={} response signature mismatch", txnRef);
      return unknown(txnRef);
    }

    String echoedRef = fields.get("vnp_TxnRef");
    if (!echoedRef.isEmpty() && !echoedRef.equals(txnRef)) {
      log.warn("vnpay querydr txnRef={} answered for another reference", txnRef);
      return unknown(txnRef);
    }

    String responseCode = fields.get("vnp_ResponseCode");
    String transactionStatus = fields.get("vnp_TransactionStatus");
    Outcome outcome = queryOutcome(responseCode, transactionStatus);
    BigDecimal amount =
        outcome == Outcome.SUCCESS || outcome == Outcome.FAILED
            ? fromGatewayAmount(fields.get("vnp_Amount"))
            : null;
    return new GatewayTransaction(
        txnRef,
        amount,
        fields.get("vnp_TmnCode"),
        outcome,
        fields.get("vnp_TransactionNo"),
        fields.get("vnp_PayDate"),
        responseCode,
        transactionStatus);
  }

  static Outcome callbackOutcome(String responseCode, String transactionStatus) {
    if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
      return Outcome.SUCCESS;
    }
    if ("24".equals(responseCode)) {
      return Outcome.CANCELLED;
    }
    if (responseCode != null && FAILURE_CODES.contains(responseCode)) {
      return Outcome.FAILED;
    }
    return Outcome.UNKNOWN;
  }

  static Outcome queryOutcome(String apiResponseCode, String transactionStatus) {
    if ("91".equals(apiResponseCode)) {
      return Outcome.NOT_FOUND;
    }
    if (!"00".equals(apiResponseCode)) {
      return Outcome.UNKNOWN;
    }
    return switch (transactionStatus) {
      case "00" -> Outcome.SUCCESS;
      case "02" -> Outcome.FAILED;
      default -> Outcome.UNKNOWN;
    };
  }

  static String canonical(Map<String, String> sortedParams) {
    StringBuilder out = new StringBuilder();
    sortedParams.forEach(
        (name, value) -> {
          if (value == null || value.isEmpty()) {
            return;
          }
          if (!out.isEmpty()) {
            out.append('&');
          }
          out.append(URLEncoder.encode(name, StandardCharsets.US_ASCII))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        });
    return out.toString();
  }

  static String toGatewayAmount(BigDecimal vnd) {
    if (vnd == null || vnd.signum() <= 0 || vnd.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("Payment amount must be a positive whole VND value");
    }
    return vnd.multiply(HUNDRED).toBigIntegerExact().toString();
  }

  static BigDecimal fromGatewayAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Gateway amount is missing");
    }
    return new BigDecimal(raw.trim()).movePointLeft(2);
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(config.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA512 is unavailable", e);
    }
  }

  private static boolean sameHash(String expected, String received) {
    return MessageDigest.isEqual(
        expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
        received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }

  private static GatewayTransaction unknown(String txnRef) {
    return new GatewayTransaction(txnRef, null, null, Outcome.UNKNOWN, null, null, null, null);
  }
}
