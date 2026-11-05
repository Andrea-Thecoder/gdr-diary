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
@Table(name = "diary")
public class Diary extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    @Index
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cover_color", length = 7)
    private String coverColor;

    @Column(name = "font", length = 100)
    private String font;

    @Column(name = "font_size")
    private Short fontSize;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @ManyToOne
    @JoinColumn(name = "cover_document_id")
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    private Document coverDocument;

    @ManyToOne
    @JoinColumn(name = "bookmark_page_id")
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    private DiaryPage bookmarkPage;
}
