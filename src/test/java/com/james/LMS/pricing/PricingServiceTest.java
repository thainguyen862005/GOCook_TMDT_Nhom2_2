package com.james.LMS.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.james.LMS.entity.Product;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
  final ProductRepository products = mock(ProductRepository.class);
  final PricingProperties properties = new PricingProperties();
  final PricingService service = new PricingService(products, properties);

  Product product(ProductStatus status, boolean active, Long sellerId) {
    return Product.builder()
        .id(11L)
        .name("Mam com sum vay")
        .category("Mon Viet")
        .unitPrice(new BigDecimal("120000.00"))
        .minGuests(2)
        .maxGuests(6)
        .status(status)
        .isActive(active)
        .sellerId(sellerId)
        .build();
  }

  void menu(Product product) {
    when(products.findById(11L)).thenReturn(Optional.of(product));
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void priceIsUnitPriceTimesGuestsWithNoDiscount() {
    menu(product(ProductStatus.PUBLISHED, true, 40L));

    FixedMenuPreview preview = service.previewFixedMenu(11L, 3, null);

    assertThat(preview.productId()).isEqualTo(11L);
    assertThat(preview.productName()).isEqualTo("Mam com sum vay");
    assertThat(preview.unitPrice()).isEqualByComparingTo("120000");
    assertThat(preview.guestCount()).isEqualTo(3);
    assertThat(preview.subtotal()).isEqualByComparingTo("360000");
    assertThat(preview.discount()).isEqualByComparingTo("0");
    assertThat(preview.total()).isEqualByComparingTo("360000");
    assertThat(preview.currency()).isEqualTo("VND");
  }

  @Test
  void previewExpiresAfterTheConfiguredTtl() {
    menu(product(ProductStatus.PUBLISHED, true, 40L));
    properties.setPreviewTtl(Duration.ofMinutes(5));
    OffsetDateTime before = OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE);

    FixedMenuPreview preview = service.previewFixedMenu(11L, 3, null);

    assertThat(preview.priceExpiresAt())
        .isBetween(before.plusMinutes(5), OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE).plusMinutes(5));
  }

  @Test
  void guestCountMustStayInsideTheMenuLimits() {
    menu(product(ProductStatus.PUBLISHED, true, 40L));

    for (int guests : List.of(1, 7)) {
      assertRejected(
          () -> service.previewFixedMenu(11L, guests, null), ErrorCode.PAYMENT_INVALID_GUESTS);
    }
    assertThat(service.previewFixedMenu(11L, 2, null).total()).isEqualByComparingTo("240000");
    assertThat(service.previewFixedMenu(11L, 6, null).total()).isEqualByComparingTo("720000");
  }

  @Test
  void menuThatCannotBeBoughtIsRejected() {
    for (Product hidden :
        List.of(
            product(ProductStatus.DRAFT, true, 40L),
            product(ProductStatus.HIDDEN, true, 40L),
            product(ProductStatus.PUBLISHED, false, 40L),
            product(ProductStatus.PUBLISHED, true, null))) {
      menu(hidden);
      assertRejected(() -> service.previewFixedMenu(11L, 3, null), ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE);
    }
    when(products.findById(11L)).thenReturn(Optional.empty());
    assertRejected(() -> service.previewFixedMenu(11L, 3, null), ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE);
  }

  @Test
  void voucherIsRejectedInsteadOfSilentlyIgnored() {
    assertRejected(
        () -> service.previewFixedMenu(11L, 3, "SALE10"), ErrorCode.PRICING_VOUCHER_NOT_SUPPORTED);
    verifyNoInteractions(products);
  }

  @Test
  void checkoutQuoteUsesTheSameFormulaAndKeepsTheMenuForTheOrderSnapshot() {
    Product menu = product(ProductStatus.PUBLISHED, true, 40L);
    menu(menu);

    PricingService.FixedMenuQuote quote = service.quoteFixedMenu(11L, 4);

    assertThat(quote.product()).isSameAs(menu);
    assertThat(quote.total()).isEqualByComparingTo(service.previewFixedMenu(11L, 4, null).total());
  }
}
