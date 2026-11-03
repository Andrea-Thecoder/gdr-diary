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
@Index(name = "uq_diary_page_diary_id_page_number", columnNames = {"diary_id", "page_number"}, unique = true)
@Entity
@Table(name = "diary_page")
public class DiaryPage extends AbstractAuditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "page_number", nullable = false)
    private Short pageNumber;

    @DbJsonB
    @Column(name = "content", nullable = false)
    private Map<String, Object> content;

    @ManyToOne(optional = false)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;
}
