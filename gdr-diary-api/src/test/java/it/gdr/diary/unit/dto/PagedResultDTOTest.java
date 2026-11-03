package it.gdr.diary.unit.dto;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static org.assertj.core.api.Assertions.assertThat;

import io.ebean.PagedList;
import it.gdr.diary.dto.PagedResultDTO;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PagedResultDTOTest {

    @ParameterizedTest
    @CsvSource({"0,20,0", "1,20,1", "20,20,1", "21,20,2", "243,100,3"})
    void of_totalRowsAndSize_computesTotalPagesRoundingUp(int totalRows, int size, int expectedPages) {
        PagedResultDTO<String> result = PagedResultDTO.of(List.of(), totalRows, 1, size);

        assertThat(result.getTotalPages()).isEqualTo(expectedPages);
    }

    @Test
    void of_explicitValues_copiesListPageAndSize() {
        PagedResultDTO<String> result = PagedResultDTO.of(List.of("a", "b"), 12, 2, 10);

        assertThat(result)
                .extracting(PagedResultDTO::getList, PagedResultDTO::getTotalRows, PagedResultDTO::getPage, PagedResultDTO::getPageSize)
                .containsExactly(List.of("a", "b"), 12L, 2, 10);
    }

    @Test
    void of_pagedList_mapsEveryItem() {
        PagedList<Integer> paged = pagedList(List.of(1, 2, 3), 0, 20, 3);

        PagedResultDTO<String> result = PagedResultDTO.of(paged, number -> "#" + number);

        assertThat(result.getList()).containsExactly("#1", "#2", "#3");
    }

    @Test
    void of_pagedList_convertsZeroBasedIndexToOneBasedPage() {
        PagedList<Integer> paged = pagedList(List.of(), 2, 20, 100);

        PagedResultDTO<Integer> result = PagedResultDTO.of(paged, number -> number);

        assertThat(result.getPage()).isEqualTo(3);
    }

    @Test
    void of_pagedList_copiesTotalsAndPageSize() {
        PagedList<Integer> paged = pagedList(List.of(), 0, 20, 45);

        PagedResultDTO<Integer> result = PagedResultDTO.of(paged, number -> number);

        assertThat(result)
                .extracting(PagedResultDTO::getTotalRows, PagedResultDTO::getTotalPages, PagedResultDTO::getPageSize)
                .containsExactly(45L, 3, 20);
    }
}
