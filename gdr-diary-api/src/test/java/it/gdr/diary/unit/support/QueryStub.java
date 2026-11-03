package it.gdr.diary.unit.support;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.ExpressionList;
import io.ebean.PagedList;
import io.ebean.Query;
import java.util.List;

public record QueryStub<T>(Query<T> query, ExpressionList<T> where) {

    @SuppressWarnings("unchecked")
    public static <T> QueryStub<T> stubFind(Database database, Class<T> type) {
        Query<T> query = mock(Query.class, RETURNS_SELF);
        ExpressionList<T> where = mock(ExpressionList.class, RETURNS_SELF);
        when(database.find(type)).thenReturn(query);
        when(query.where()).thenReturn(where);
        return new QueryStub<>(query, where);
    }

    @SuppressWarnings("unchecked")
    public static <T> QueryStub<T> stubFindLeniently(Database database, Class<T> type) {
        Query<T> query = mock(Query.class, RETURNS_SELF);
        ExpressionList<T> where = mock(ExpressionList.class, RETURNS_SELF);
        lenient().when(database.find(type)).thenReturn(query);
        lenient().when(query.where()).thenReturn(where);
        return new QueryStub<>(query, where);
    }

    @SuppressWarnings("unchecked")
    public static <T> PagedList<T> pagedList(List<T> items, int pageIndex, int pageSize, int totalCount) {
        PagedList<T> pagedList = mock(PagedList.class);
        when(pagedList.getList()).thenReturn(items);
        when(pagedList.getPageIndex()).thenReturn(pageIndex);
        when(pagedList.getPageSize()).thenReturn(pageSize);
        when(pagedList.getTotalCount()).thenReturn(totalCount);
        when(pagedList.getTotalPageCount()).thenReturn((totalCount + pageSize - 1) / pageSize);
        return pagedList;
    }
}
