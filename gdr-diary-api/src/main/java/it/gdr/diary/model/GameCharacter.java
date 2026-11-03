package it.gdr.diary.model;

import io.ebean.annotation.ConstraintMode;
import io.ebean.annotation.DbForeignKey;
import io.ebean.annotation.Index;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "game_character")
public class GameCharacter extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    @Index
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "campaign_name", length = 255)
    @Index
    private String campaignName;

    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private GameCharacterKind kind;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne
    @JoinColumn(name = "avatar_document_id")
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    private Document avatarDocument;
}
