package it.gdr.diary.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "feedback_document")
public class FeedbackDocument extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
}
