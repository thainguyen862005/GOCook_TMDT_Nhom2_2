package com.james.LMS.pricing;

import com.james.LMS.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Price per guest of published menus, the only data price guidance is built from. */
public interface MenuPriceReader extends Repository<Product, Long> {

  @Query(
      """
      select p.unitPrice from Product p
      where p.isActive = true and p.status = com.james.LMS.enums.ProductStatus.PUBLISHED
        and lower(p.category) = lower(:cuisine)
      """)
  List<BigDecimal> marketPrices(@Param("cuisine") String cuisine);

  @Query(
      """
      select p.unitPrice from Product p
      where p.isActive = true and p.status = com.james.LMS.enums.ProductStatus.PUBLISHED
        and p.sellerId = :chefId and lower(p.category) = lower(:cuisine)
      """)
  List<BigDecimal> chefPrices(@Param("chefId") Long chefId, @Param("cuisine") String cuisine);
}
