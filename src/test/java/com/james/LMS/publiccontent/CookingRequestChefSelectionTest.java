package com.james.LMS.publiccontent;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CookingRequestChefSelectionTest {
  @Test void generalRequestDoesNotRequirePreferredChef() {
    assertThat(CookingRequestController.resolveChefId(null, null)).isNull();
  }
  @Test void explicitPreferredChefRemainsSelected() {
    assertThat(CookingRequestController.resolveChefId(53L, null)).isEqualTo(53L);
  }
  @Test void menuWithoutPreferenceDoesNotForceOwningChef() {
    assertThat(CookingRequestController.resolveChefId(null, Map.of("chefId", 53L))).isNull();
  }
  @Test void customerCanChooseDifferentChefForMenu() {
    assertThat(CookingRequestController.resolveChefId(54L, Map.of("chefId", 53L))).isEqualTo(54L);
  }
}
