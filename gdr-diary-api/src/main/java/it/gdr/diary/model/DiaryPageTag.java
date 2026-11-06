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

    @ManyToOne(optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private DiaryPage page;
}
