package com.james.LMS.seller.orders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderEvidencePolicyTest {
  @Test void requiresIngredientReceiptBeforeCooking() {
    assertThrows(ResponseStatusException.class,
        () -> OrderEvidencePolicy.requireBeforeTransition("PREPARING", "COOKING", 0, 0));
    assertDoesNotThrow(
        () -> OrderEvidencePolicy.requireBeforeTransition("PREPARING", "COOKING", 1, 0));
  }
  @Test void requiresMealPhotoBeforeCustomerAcceptance() {
    assertThrows(ResponseStatusException.class,
        () -> OrderEvidencePolicy.requireBeforeTransition("COOKING", "AWAITING_ACCEPTANCE", 1, 0));
    assertDoesNotThrow(
        () -> OrderEvidencePolicy.requireBeforeTransition("COOKING", "AWAITING_ACCEPTANCE", 1, 1));
  }
  @Test void removalCannotEraseEvidenceAfterNextMilestone() {
    assertThrows(ResponseStatusException.class,
        () -> OrderEvidencePolicy.validateRemovalStage("COOKING", "INGREDIENT_RECEIPT"));
    assertThrows(ResponseStatusException.class,
        () -> OrderEvidencePolicy.validateRemovalStage("AWAITING_ACCEPTANCE", "FINISHED_MEAL"));
    assertDoesNotThrow(
        () -> OrderEvidencePolicy.validateRemovalStage("PREPARING", "INGREDIENT_RECEIPT"));
    assertDoesNotThrow(
        () -> OrderEvidencePolicy.validateRemovalStage("COOKING", "FINISHED_MEAL"));
  }
  @Test void receiptNeedsRealDateAndSource() {
    assertThrows(ResponseStatusException.class, () -> OrderEvidencePolicy.validateReceiptNote("Không rõ cửa hàng"));
    assertThrows(ResponseStatusException.class, () -> OrderEvidencePolicy.validateReceiptNote("Cửa hàng, 31/02/2026, thịt rau"));
    assertDoesNotThrow(() -> OrderEvidencePolicy.validateReceiptNote("Co.opmart, 01/10/2026, thịt và rau"));
  }
  @Test void wrongTypeAndStageAreRejected() {
    assertThrows(ResponseStatusException.class,() -> OrderEvidencePolicy.validateType("FAKE"));
    assertThrows(ResponseStatusException.class,
        () -> OrderEvidencePolicy.validateUploadStage("PREPARING","FINISHED_MEAL"));
  }
}
