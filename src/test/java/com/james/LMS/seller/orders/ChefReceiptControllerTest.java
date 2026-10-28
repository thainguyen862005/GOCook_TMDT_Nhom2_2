package com.james.LMS.seller.orders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChefReceiptControllerTest {
  @Test void acceptsOnlyImagesByHeaderNotFileExtension() {
    assertNull(ChefReceiptController.detect(new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1}));
    assertNull(ChefReceiptController.detect(new byte[]{(byte)137,80,78,71,13,10,26,10}));
    assertNull(ChefReceiptController.detect("fake.jpg".getBytes()));
    assertNull(ChefReceiptController.detect(new byte[]{}));
  }
}
