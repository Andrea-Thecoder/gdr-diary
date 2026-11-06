package it.gdr.diary.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "diary_page_tag")
public class DiaryPageTag extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /** Diario della pagina, copiato per contare i tag del diario; il database garantisce che coincida con quello della pagina. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @OneToOne(optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private DiaryPage page;
}
