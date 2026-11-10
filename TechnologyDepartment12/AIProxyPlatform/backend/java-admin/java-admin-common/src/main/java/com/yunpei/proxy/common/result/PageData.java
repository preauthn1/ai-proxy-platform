package com.yunpei.proxy.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页 data 形状：{@code { items, total }}。
 *
 * @param <T> 列表元素类型
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果 {items,total}")
public class PageData<T> {

    @Schema(description = "当前页数据")
    private List<T> items;

    @Schema(description = "符合条件的总条数")
    private long total;

    public static <T> PageData<T> of(List<T> items, long total) {
        return new PageData<>(items == null ? Collections.emptyList() : items, total);
    }

    public static <T> PageData<T> empty() {
        return new PageData<>(Collections.emptyList(), 0L);
    }
}
