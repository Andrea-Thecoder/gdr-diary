package it.gdr.diary.model;

import io.ebean.annotation.ConstraintMode;
import io.ebean.annotation.DbForeignKey;
import io.ebean.annotation.Index;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payment")
@Index(name = "ix_payment_app_user_id_paid_at", columnNames = {"app_user_id", "paid_at"})
public class Payment extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "app_user_id")
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    private AppUser appUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private PaymentProvider provider;

    @Column(name = "provider_payment_id", nullable = false, unique = true, length = 255)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_tax_code", length = 32)
    private String customerTaxCode;

    @Column(name = "customer_country", length = 2)
    private String customerCountry;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @Column(name = "receipt_url", length = 2048)
    private String receiptUrl;

    @Column(name = "retain_until", nullable = false)
    private LocalDate retainUntil;
}
