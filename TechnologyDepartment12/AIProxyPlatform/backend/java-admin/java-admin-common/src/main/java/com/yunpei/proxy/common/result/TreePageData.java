package com.yunpei.proxy.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 树形资源分页 data 形状：{@code { items, total, itemTotal }}。
 *
 * <p>菜单列表等场景：{@code total} 为根节点数（分页基数），{@code itemTotal} 为展开后的条目数。
 *
 * @param <T> 列表元素类型
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "树形分页结果 {items,total,itemTotal}")
public class TreePageData<T> {

    @Schema(description = "当前页数据（含展开的子树扁平列表）")
    private List<T> items;

    @Schema(description = "根节点总数（分页 total）")
    private long total;

    @Schema(description = "展开后的条目总数（展示用）")
    private long itemTotal;

    public static <T> TreePageData<T> of(List<T> items, long total, long itemTotal) {
        return new TreePageData<>(items == null ? Collections.emptyList() : items, total, itemTotal);
    }

    public static <T> TreePageData<T> empty() {
        return new TreePageData<>(Collections.emptyList(), 0L, 0L);
    }
}
