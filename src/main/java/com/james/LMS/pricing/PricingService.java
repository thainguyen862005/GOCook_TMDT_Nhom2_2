package com.james.LMS.pricing;

import com.james.LMS.entity.Product;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Single place where a fixed menu is priced: unitPrice x guests, validated against the menu. */
@Service
@RequiredArgsConstructor
public class PricingService {

  private static final String CURRENCY = "VND";

  private final ProductRepository products;
  private final PricingProperties properties;

  public record FixedMenuQuote(
      Product product, int guestCount, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}

  public FixedMenuPreview previewFixedMenu(Long productId, int guests, String voucherCode) {
    if (voucherCode != null) {
      throw new PaymentException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PRICING_VOUCHER_NOT_SUPPORTED,
          "Voucher chưa được hỗ trợ.");
    }
    FixedMenuQuote quote = quoteFixedMenu(productId, guests);
    Product product = quote.product();
    return new FixedMenuPreview(
        product.getId(),
        product.getName(),
        product.getUnitPrice(),
        quote.guestCount(),
        quote.subtotal(),
        quote.discount(),
        quote.total(),
        CURRENCY,
        OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE).plus(properties.getPreviewTtl()));
  }

  public FixedMenuQuote quoteFixedMenu(Long productId, int guests) {
    Product product = payableProduct(productId);
    requireGuestCount(product, guests);
    BigDecimal subtotal = product.getUnitPrice().multiply(BigDecimal.valueOf(guests));
    return new FixedMenuQuote(product, guests, subtotal, BigDecimal.ZERO, subtotal);
  }

  private Product payableProduct(Long productId) {
    Product product = products.findById(productId).orElse(null);
    boolean payable =
        product != null
            && product.isActive()
            && product.getStatus() == ProductStatus.PUBLISHED
            && product.getSellerId() != null;
    if (!payable) {
      throw new PaymentException(
          HttpStatus.NOT_FOUND,
          ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE,
          "Thực đơn không còn khả dụng.");
    }
    return product;
  }

  private static void requireGuestCount(Product product, int guestCount) {
    if (guestCount < product.getMinGuests() || guestCount > product.getMaxGuests()) {
      throw new PaymentException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PAYMENT_INVALID_GUESTS,
          "Số khách phải từ %d đến %d người."
              .formatted(product.getMinGuests(), product.getMaxGuests()));
    }
  }
}
