package com.xmut.forma.common.page;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页：{@code items} 为本页数据；{@code nextToken} 非空表示还有下一页（传回下次请求）。
 */
public final class Page<T> {

    private final List<T> items;
    private final String nextToken;

    public Page(List<T> items, String nextToken) {
        this.items = items == null
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(items);
        this.nextToken = nextToken;
    }

    public static <T> Page<T> empty() {
        return new Page<T>(Collections.<T>emptyList(), null);
    }

    public static <T> Page<T> of(List<T> items, String nextToken) {
        return new Page<T>(items, nextToken);
    }

    public List<T> getItems() {
        return items;
    }

    public String getNextToken() {
        return nextToken;
    }
}
