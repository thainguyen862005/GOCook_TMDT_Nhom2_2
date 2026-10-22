package com.james.LMS.pricing;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.pricing.PriceGuidance.Basis;
import com.james.LMS.pricing.PriceGuidance.BudgetPosition;
import com.james.LMS.pricing.PriceGuidance.Factor;
import com.james.LMS.pricing.PriceGuidance.Status;
import com.james.LMS.proposal.ProposalException;
import com.james.LMS.proposal.port.ProposalPorts;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuoteGuidanceService {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

  private final MenuPriceReader menus;
  private final PricingProperties properties;
  private final ProposalPorts ports;

  public PriceGuidance guidanceFor(Long chefId, Long requestId) {
    RequestSnapshot request = ports.requests().load(requestId);
    if (!request.matchedChefIds().contains(chefId)) {
      throw new ProposalException(
          HttpStatus.FORBIDDEN,
          ErrorCode.PROPOSAL_CHEF_NOT_MATCHED,
          "Bạn không nằm trong danh sách đầu bếp được ghép cho yêu cầu này.");
    }
    return guide(chefId, request);
  }

  public PriceGuidance guide(Long chefId, RequestSnapshot request) {
    PricingProperties.Guidance config = properties.getGuidance();
    if (request.guestCount() < 1) {
      return PriceGuidance.insufficient(request.requestId(), request.guestCount(), "INVALID_GUEST_COUNT", 0);
    }
    String cuisine = request.cuisine() == null ? "" : request.cuisine().trim();
    if (cuisine.isEmpty()) {
      return PriceGuidance.insufficient(request.requestId(), request.guestCount(), "NO_CUISINE", 0);
    }

    Basis basis = Basis.CHEF;
    List<BigDecimal> sample = menus.chefPrices(chefId, cuisine);
    if (sample.size() < config.getMinChefSamples()) {
      basis = Basis.MARKET;
      sample = menus.marketPrices(cuisine);
      if (sample.size() < config.getMinMarketSamples()) {
        return PriceGuidance.insufficient(
            request.requestId(), request.guestCount(), "NOT_ENOUGH_MENUS", sample.size());
      }
    }

    BigDecimal baselinePerGuest = median(sample);
    List<Factor> factors = factors(request, config);
    BigDecimal adjustment =
        factors.stream()
            .map(factor -> factor.impactPercent().divide(HUNDRED))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal center =
        baselinePerGuest
            .multiply(BigDecimal.valueOf(request.guestCount()))
            .multiply(BigDecimal.ONE.add(adjustment));
    BigDecimal min =
        center.multiply(BigDecimal.ONE.subtract(config.getRangeSpread())).setScale(0, RoundingMode.HALF_UP);
    BigDecimal max =
        center.multiply(BigDecimal.ONE.add(config.getRangeSpread())).setScale(0, RoundingMode.HALF_UP);

    BudgetPosition position = budgetPosition(request, min, max);
    return new PriceGuidance(
        request.requestId(),
        Status.OK,
        null,
        "VND",
        request.guestCount(),
        basis,
        sample.size(),
        baselinePerGuest,
        min,
        max,
        factors,
        position,
        warning(position));
  }

  private static List<Factor> factors(RequestSnapshot request, PricingProperties.Guidance config) {
    List<Factor> factors = new ArrayList<>();

    if (request.dishCount() != null && request.dishCount() > 0) {
      BigDecimal impact =
          clamp(
              config.getDishStep().multiply(BigDecimal.valueOf(request.dishCount() - config.getReferenceDishCount())),
              config.getDishCap());
      add(
          factors,
          "DISH_COUNT",
          "Số món",
          impact,
          "%d món so với mức tham chiếu %d món.".formatted(request.dishCount(), config.getReferenceDishCount()));
    }
    int dietary = size(request.dietaryTags());
    if (dietary > 0) {
      add(
          factors,
          "DIETARY",
          "Chế độ ăn đặc biệt",
          clamp(config.getDietaryStep().multiply(BigDecimal.valueOf(dietary)), config.getSpecialNeedsCap()),
          "%d yêu cầu về chế độ ăn cần chuẩn bị riêng.".formatted(dietary));
    }
    int allergies = size(request.allergyTags());
    if (allergies > 0) {
      add(
          factors,
          "ALLERGY",
          "Dị ứng",
          clamp(config.getAllergyStep().multiply(BigDecimal.valueOf(allergies)), config.getSpecialNeedsCap()),
          "%d nhóm dị ứng cần tránh và tách quy trình.".formatted(allergies));
    }
    if (request.durationMinutes() != null && request.durationMinutes() > 0) {
      BigDecimal hours =
          BigDecimal.valueOf(request.durationMinutes() - config.getReferenceDurationMinutes())
              .divide(SIXTY, 4, RoundingMode.HALF_UP);
      add(
          factors,
          "DURATION",
          "Thời lượng phục vụ",
          clamp(config.getDurationStepPerHour().multiply(hours), config.getDurationCap()),
          "%d phút so với mức tham chiếu %d phút."
              .formatted(request.durationMinutes(), config.getReferenceDurationMinutes()));
    }
    if (request.serviceArea() != null) {
      BigDecimal impact =
          config.getAreaImpact().get(request.serviceArea().trim().toLowerCase(Locale.ROOT));
      if (impact != null) {
        add(factors, "AREA", "Khu vực", impact, "Khu vực phục vụ: " + request.serviceArea().trim() + ".");
      }
    }
    return factors;
  }

  private static void add(
      List<Factor> factors, String code, String label, BigDecimal impact, String explanation) {
    if (impact.signum() != 0) {
      factors.add(new Factor(code, label, impact.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP), explanation));
    }
  }

  private static BigDecimal clamp(BigDecimal value, BigDecimal cap) {
    return value.max(cap.negate()).min(cap);
  }

  private static int size(Collection<String> tags) {
    return tags == null ? 0 : tags.size();
  }

  private static BigDecimal median(List<BigDecimal> prices) {
    List<BigDecimal> sorted = prices.stream().sorted().toList();
    int middle = sorted.size() / 2;
    BigDecimal value =
        sorted.size() % 2 == 1
            ? sorted.get(middle)
            : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2));
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static BudgetPosition budgetPosition(RequestSnapshot request, BigDecimal min, BigDecimal max) {
    if (request.budgetMin() == null && request.budgetMax() == null) {
      return BudgetPosition.NA;
    }
    if (request.budgetMax() != null && request.budgetMax().compareTo(min) < 0) {
      return BudgetPosition.BELOW_RANGE;
    }
    if (request.budgetMin() != null && request.budgetMin().compareTo(max) > 0) {
      return BudgetPosition.ABOVE_RANGE;
    }
    return BudgetPosition.WITHIN;
  }

  private static String warning(BudgetPosition position) {
    return switch (position) {
      case BELOW_RANGE -> "Ngân sách của khách thấp hơn khoảng giá gợi ý.";
      case ABOVE_RANGE -> "Ngân sách của khách cao hơn khoảng giá gợi ý.";
      default -> null;
    };
  }
}
