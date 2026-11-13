package it.gdr.diary.model;

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
@Table(name = "feedback")
public class Feedback extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Index
    private FeedbackType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Index
    private FeedbackStatus status;

    @Column(name = "email", nullable = false, length = 254)
    @Index
    private String email;

    @Column(name = "privacy_accepted", nullable = false)
    private boolean privacyAccepted;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @DbJsonB
    @Column(name = "technical_info")
    private Map<String, Object> technicalInfo;
}
