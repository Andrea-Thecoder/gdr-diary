package it.gdr.diary.model;

import io.ebean.annotation.ConstraintMode;
import io.ebean.annotation.DbDefault;
import io.ebean.annotation.DbForeignKey;
import io.ebean.annotation.DbJsonB;
import io.ebean.annotation.Index;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 36)
    private String keycloakId;

    @Column(name = "email", length = 255)
    @Index
    private String email;

    @DbJsonB
    @Column(name = "settings")
    private Map<String, Object> settings;

    @DbDefault("false")
    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    @ManyToOne
    @JoinColumn(name = "avatar_document_id")
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    private Document avatarDocument;
}
