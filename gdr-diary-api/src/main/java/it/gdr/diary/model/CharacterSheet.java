package it.gdr.diary.model;

import io.ebean.annotation.Index;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "character_sheet")
public class CharacterSheet extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "title", nullable = false, length = 100)
    @Index
    private String title;

    @Column(name = "game_system", length = 100)
    @Index
    private String gameSystem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
}
