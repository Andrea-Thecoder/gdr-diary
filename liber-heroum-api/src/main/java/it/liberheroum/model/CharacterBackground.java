package it.liberheroum.model;

import io.ebean.annotation.DbJsonB;
import io.ebean.annotation.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Index(name = "uq_character_background_character_id", columnNames = {"character_id"}, unique = true)
@Entity
@Table(name = "character_background")
public class CharacterBackground extends AbstractAuditable {

    public static final int PREVIEW_LENGTH = 300;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @DbJsonB
    @Column(name = "content", nullable = false)
    private Map<String, Object> content;

    @Column(name = "preview", nullable = false, length = PREVIEW_LENGTH)
    private String preview;

    @Column(name = "text_length", nullable = false)
    private Integer textLength;
}
