package com.james.LMS.repository;

import com.james.LMS.entity.Product;
import com.james.LMS.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  @Query(
      """
      select p from Product p
      where (:query = ''
             or lower(p.name) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.dishItems, '')) like lower(concat('%', :query, '%')))
        and (:category = '' or lower(p.category) = lower(:category))
        and (:status is null or p.status = :status)
      order by p.updatedAt desc
      """)
  Page<Product> search(
      @Param("query") String query,
      @Param("category") String category,
      @Param("status") ProductStatus status,
      Pageable pageable);

  @Query(
      "select distinct p.category from Product p where p.category is not null order by p.category")
  List<String> findDistinctCategories();

  @Query(
      """
      select p from Product p where p.sellerId = :sellerId and p.isActive = true
      and (:query = '' or lower(p.name) like lower(concat('%', :query, '%')))
      and (:category = '' or p.category = :category)
      and (:status is null or p.status = :status)
      """)
  Page<Product> searchOwned(
      Long sellerId, String query, String category, ProductStatus status, Pageable pageable);

  @Query(
      "select p from Product p where p.id = :id and p.sellerId = :sellerId and p.isActive = true")
  Optional<Product> findOwned(Long id, Long sellerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from Product p where p.id = :id and p.sellerId = :sellerId and p.isActive = true")
  Optional<Product> lockOwned(Long id, Long sellerId);

  @Query(
      "select distinct p.category from Product p where p.sellerId = :sellerId and p.isActive = true"
          + " order by p.category")
  List<String> findOwnedCategories(Long sellerId);

  /** Only active, published menus are visible in the customer catalog. */
  @Query(value = """
      select p from Product p
      where p.isActive = true and p.status = :status
        and (:query = '' or lower(p.name) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.dishItems, '')) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :query, '%')))
        and (:category = '' or lower(p.category) = lower(:category))
        and (:minPrice is null or p.unitPrice >= :minPrice)
        and (:maxPrice is null or p.unitPrice <= :maxPrice)
      """, countQuery = """
      select count(p) from Product p
      where p.isActive = true and p.status = :status
        and (:query = '' or lower(p.name) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.dishItems, '')) like lower(concat('%', :query, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :query, '%')))
        and (:category = '' or lower(p.category) = lower(:category))
        and (:minPrice is null or p.unitPrice >= :minPrice)
        and (:maxPrice is null or p.unitPrice <= :maxPrice)
      """)
  Page<Product> findPublicMenus(
      @Param("status") ProductStatus status,
      @Param("query") String query,
      @Param("category") String category,
      @Param("minPrice") BigDecimal minPrice,
      @Param("maxPrice") BigDecimal maxPrice,
      Pageable pageable);

  @Query("""
      select distinct p.category from Product p
      where p.isActive = true and p.status = :status
      order by p.category
      """)
  List<String> findPublicCategories(@Param("status") ProductStatus status);

  long countByStatus(ProductStatus status);

  @Query(
      "select p from Product p where p.id = :id and p.isActive = true and p.status = com.james.LMS.enums.ProductStatus.PUBLISHED")
  Optional<Product> findPublished(@Param("id") Long id);
}
