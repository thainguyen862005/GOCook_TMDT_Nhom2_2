package com.james.LMS.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.service.PaymentGateway.GatewayTransaction;
import com.james.LMS.service.PaymentGateway.Outcome;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentGatewaySignatureTest {
  static final String SECRET = "TEST_SECRET_NOT_REAL_0123456789";
  static final String TMN = "TESTTMN1";
  static final String PAY_URL = "https://sandbox.example.test/paymentv2/vpcpay.html";

  static final String PAY_DATA =
      "vnp_Amount=24000000&vnp_Command=pay&vnp_CreateDate=20261014153045&vnp_CurrCode=VND"
          + "&vnp_ExpireDate=20261014154545&vnp_IpAddr=127.0.0.1&vnp_Locale=vn"
          + "&vnp_OrderInfo=Thanh+toan+don+hang+GC2610147K3QX9&vnp_OrderType=other"
          + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment-result&vnp_TmnCode=TESTTMN1"
          + "&vnp_TxnRef=GC2610147K3QX9&vnp_Version=2.1.0";
  static final String PAY_HASH =
      "26622c3d3090017fbc60aaa84bf1a96f2d687513556e47cdd4ca582260ddf3e3"
          + "6471dcd52604c9afe108b836e92f7f7033b133d9fcf3b494d08f010f0b708c53";

  static final String IPN_HASH =
      "b22c3fc3acba90259195fb810c4459c26ea4f8b57a5740ac37ac11c1270ab9c5"
          + "5041958e7cd1daf1e93f8bb5f1458c1bb45d976848cbfeb0210c0f02f15f4fa0";

  static final String QUERY_OK_HASH =
      "9b042685f0bd1663f5d8dd83728e220bf58623f3d9b6a6ef80f1a4089837a59d"
          + "7f203efc006053d63daffe00538800bf3067280305ee4a34422704680020b3bb";
  static final String QUERY_NOT_FOUND_HASH =
      "acd2dfead17246ffb51b5768dca5084f97d6e8a65c587c8d22dd640690f6a6ec"
          + "4a70cf1168d48f8671da88d793b07ba811f48e35f64104044b4b0ae969116f96";

  final ObjectMapper mapper = new ObjectMapper();
  final VnpayPaymentGateway gateway =
      new VnpayPaymentGateway(properties(), mapper, mock(HttpClient.class));

  static PaymentProperties properties() {
    PaymentProperties.Vnpay vnpay = new PaymentProperties.Vnpay();
    vnpay.setTmnCode(TMN);
    vnpay.setHashSecret(SECRET);
    vnpay.setPayUrl(PAY_URL);
    vnpay.setApiUrl("https://sandbox.example.test/merchant_webapi/api/transaction");
    PaymentProperties properties = new PaymentProperties();
    properties.setFrontendBaseUrl("http://localhost:5173");
    properties.setVnpay(vnpay);
    return properties;
  }

  static Map<String, String> ipnParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_Amount", "24000000");
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_BankTranNo", "VNP14226112");
    params.put("vnp_CardType", "ATM");
    params.put("vnp_OrderInfo", "Thanh toan don hang GC2610147K3QX9");
    params.put("vnp_PayDate", "20261014153300");
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TmnCode", TMN);
    params.put("vnp_TransactionNo", "14226112");
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TxnRef", "GC2610147K3QX9");
    params.put("vnp_SecureHash", IPN_HASH);
    return params;
  }

  @Test
  void buildsPaymentUrlSignedWithHmacSha512OverSortedEncodedParameters() {
    String url =
        gateway.createPaymentUrl(
            new PaymentRequest(
                "GC2610147K3QX9",
                new BigDecimal("240000.00"),
                "Thanh toan don hang GC2610147K3QX9",
                "http://localhost:5173/payment-result",
                "127.0.0.1",
                LocalDateTime.of(2026, 10, 14, 15, 30, 45),
                LocalDateTime.of(2026, 10, 14, 15, 45, 45)));

    assertThat(url).isEqualTo(PAY_URL + "?" + PAY_DATA + "&vnp_SecureHash=" + PAY_HASH);
  }

  @Test
  void sendsAmountInGatewayUnitOfVndTimesOneHundred() {
    assertThat(VnpayPaymentGateway.toGatewayAmount(new BigDecimal("240000.00")))
        .isEqualTo("24000000");
    assertThat(VnpayPaymentGateway.toGatewayAmount(new BigDecimal("10000"))).isEqualTo("1000000");
    assertThat(VnpayPaymentGateway.fromGatewayAmount("24000000"))
        .isEqualByComparingTo("240000");
  }

  @Test
  void refusesAmountsThatAreNotPositiveWholeVnd() {
    assertThatThrownBy(() -> VnpayPaymentGateway.toGatewayAmount(new BigDecimal("120000.50")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VnpayPaymentGateway.toGatewayAmount(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VnpayPaymentGateway.fromGatewayAmount(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsCallbackWithValidSignatureAndReadsItsFields() {
    Optional<GatewayTransaction> result = gateway.verifyCallback(ipnParams());

    assertThat(result).isPresent();
    GatewayTransaction tx = result.get();
    assertThat(tx.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(tx.txnRef()).isEqualTo("GC2610147K3QX9");
    assertThat(tx.amount()).isEqualByComparingTo("240000");
    assertThat(tx.merchantCode()).isEqualTo(TMN);
    assertThat(tx.transactionNo()).isEqualTo("14226112");
    assertThat(tx.payDate()).isEqualTo("20261014153300");
  }

  @Test
  void signatureTypeParameterIsNotPartOfTheSignedData() {
    Map<String, String> params = ipnParams();
    params.put("vnp_SecureHashType", "HmacSHA512");

    assertThat(gateway.verifyCallback(params)).isPresent();
  }

  @Test
  void rejectsCallbackWhenAnySignedValueWasChanged() {
    Map<String, String> params = ipnParams();
    params.put("vnp_Amount", "100");

    assertThat(gateway.verifyCallback(params)).isEmpty();
  }

  @Test
  void rejectsCallbackWithWrongOrMissingSignature() {
    Map<String, String> wrong = ipnParams();
    wrong.put("vnp_SecureHash", "0".repeat(128));
    Map<String, String> missing = ipnParams();
    missing.remove("vnp_SecureHash");

    assertThat(gateway.verifyCallback(wrong)).isEmpty();
    assertThat(gateway.verifyCallback(missing)).isEmpty();
  }

  @Test
  void rejectsCallbackSignedWithAnotherSecret() {
    PaymentProperties other = properties();
    other.getVnpay().setHashSecret("ANOTHER_FAKE_SECRET_9876543210");
    VnpayPaymentGateway otherGateway =
        new VnpayPaymentGateway(other, mapper, mock(HttpClient.class));

    assertThat(otherGateway.verifyCallback(ipnParams())).isEmpty();
  }

  @Test
  void mapsCallbackCodesToOutcomes() {
    assertThat(VnpayPaymentGateway.callbackOutcome("00", "00")).isEqualTo(Outcome.SUCCESS);
    assertThat(VnpayPaymentGateway.callbackOutcome("24", "02")).isEqualTo(Outcome.CANCELLED);
    assertThat(VnpayPaymentGateway.callbackOutcome("51", "02")).isEqualTo(Outcome.FAILED);
    assertThat(VnpayPaymentGateway.callbackOutcome("11", "02")).isEqualTo(Outcome.FAILED);
  }

  @Test
  void treatsSuspectedFraudAndOddCombinationsAsUndecided() {
    assertThat(VnpayPaymentGateway.callbackOutcome("07", "00")).isEqualTo(Outcome.UNKNOWN);
    assertThat(VnpayPaymentGateway.callbackOutcome("00", "01")).isEqualTo(Outcome.UNKNOWN);
    assertThat(VnpayPaymentGateway.callbackOutcome("00", "04")).isEqualTo(Outcome.UNKNOWN);
    assertThat(VnpayPaymentGateway.callbackOutcome(null, null)).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void mapsQueryCodesToDecisiveOrUndecidedOutcomes() {
    assertThat(VnpayPaymentGateway.queryOutcome("00", "00")).isEqualTo(Outcome.SUCCESS);
    assertThat(VnpayPaymentGateway.queryOutcome("00", "02")).isEqualTo(Outcome.FAILED);
    assertThat(VnpayPaymentGateway.queryOutcome("91", "")).isEqualTo(Outcome.NOT_FOUND);
    for (String status : new String[] {"01", "04", "05", "06", "07", "09"}) {
      assertThat(VnpayPaymentGateway.queryOutcome("00", status)).isEqualTo(Outcome.UNKNOWN);
    }
    for (String code : new String[] {"02", "03", "94", "97", "99"}) {
      assertThat(VnpayPaymentGateway.queryOutcome(code, "00")).isEqualTo(Outcome.UNKNOWN);
    }
  }

  Map<String, String> queryOkFields() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("vnp_ResponseId", "RSP00000001");
    fields.put("vnp_Command", "querydr");
    fields.put("vnp_ResponseCode", "00");
    fields.put("vnp_Message", "QueryDR Success");
    fields.put("vnp_TmnCode", TMN);
    fields.put("vnp_TxnRef", "GC2610147K3QX9");
    fields.put("vnp_Amount", "24000000");
    fields.put("vnp_BankCode", "NCB");
    fields.put("vnp_PayDate", "20261014153300");
    fields.put("vnp_TransactionNo", "14226112");
    fields.put("vnp_TransactionType", "01");
    fields.put("vnp_TransactionStatus", "00");
    fields.put("vnp_OrderInfo", "Thanh toan don hang GC2610147K3QX9");
    fields.put("vnp_SecureHash", QUERY_OK_HASH);
    return fields;
  }

  @Test
  void readsSuccessfulQueryResponseWithValidSignature() throws Exception {
    String json = mapper.writeValueAsString(queryOkFields());

    GatewayTransaction tx = gateway.parseQueryResponse("GC2610147K3QX9", json);

    assertThat(tx.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(tx.amount()).isEqualByComparingTo("240000");
    assertThat(tx.merchantCode()).isEqualTo(TMN);
    assertThat(tx.transactionNo()).isEqualTo("14226112");
    assertThat(tx.payDate()).isEqualTo("20261014153300");
  }

  @Test
  void readsNotFoundQueryResponseWithValidSignature() throws Exception {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("vnp_ResponseId", "RSP00000002");
    fields.put("vnp_Command", "querydr");
    fields.put("vnp_ResponseCode", "91");
    fields.put("vnp_Message", "Giao dich khong ton tai");
    fields.put("vnp_TmnCode", TMN);
    fields.put("vnp_TxnRef", "GC2610147K3QX9");
    fields.put("vnp_SecureHash", QUERY_NOT_FOUND_HASH);

    GatewayTransaction tx =
        gateway.parseQueryResponse("GC2610147K3QX9", mapper.writeValueAsString(fields));

    assertThat(tx.outcome()).isEqualTo(Outcome.NOT_FOUND);
    assertThat(tx.amount()).isNull();
  }

  @Test
  void queryResponseWithChangedFieldIsUndecided() throws Exception {
    Map<String, String> fields = queryOkFields();
    fields.put("vnp_Amount", "100");

    GatewayTransaction tx =
        gateway.parseQueryResponse("GC2610147K3QX9", mapper.writeValueAsString(fields));

    assertThat(tx.outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void queryResponseForAnotherReferenceIsUndecided() throws Exception {
    GatewayTransaction tx =
        gateway.parseQueryResponse("GC0000000AAAAAA", mapper.writeValueAsString(queryOkFields()));

    assertThat(tx.outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void unreadableQueryResponseIsUndecided() {
    assertThat(gateway.parseQueryResponse("GC2610147K3QX9", "<html>502</html>").outcome())
        .isEqualTo(Outcome.UNKNOWN);
  }
}
