
package com.james.LMS.entity;

import com.james.LMS.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "orders")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CookOrder extends BaseEntity {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "chef_name", length = 255)
    private String chefName;

    @Column(name = "chef_id")
    private Long chefId;

    @Column(name = "service_address", length = 1000)
    private String serviceAddress;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Column(name = "service_type",insertable = false,updatable = false)
    private String serviceType;

    public String getScheduledAtDisplay() {
        return scheduledAt == null ? "—" : scheduledAt.format(DISPLAY_FORMAT);
    }

    public String getStatusName() {
        return status == null ? "" : status.name();
    }
}
