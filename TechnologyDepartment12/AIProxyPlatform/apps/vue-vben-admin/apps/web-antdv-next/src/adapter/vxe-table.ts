import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import type { ComponentPropsMap, ComponentType } from './component';

import { h } from 'vue';

import {
  setupVbenVxeTable,
  useVbenVxeGrid as useGrid,
} from '@vben/plugins/vxe-table';

import { Button, Image, Tag } from 'antdv-next';

import { useVbenForm } from './form';

// antdv-next Tag 接受的 color 预设（与 antd Tag preset colors 对齐）。
// dict 未命中 / 不在这 17 项内时返回 undefined，Tag 会落到默认无色变体。
type AntdTagColor =
  | 'blue'
  | 'cyan'
  | 'default'
  | 'error'
  | 'geekblue'
  | 'gold'
  | 'green'
  | 'lime'
  | 'magenta'
  | 'orange'
  | 'processing'
  | 'purple'
  | 'red'
  | 'success'
  | 'volcano'
  | 'warning'
  | 'yellow';

const ANTD_TAG_COLOR_SET: ReadonlySet<string> = new Set<AntdTagColor>([
  'blue',
  'cyan',
  'default',
  'error',
  'geekblue',
  'gold',
  'green',
  'lime',
  'magenta',
  'orange',
  'processing',
  'purple',
  'red',
  'success',
  'volcano',
  'warning',
  'yellow',
]);

function normalizeAntdTagColor(
  color: string | undefined,
): AntdTagColor | undefined {
  if (color && ANTD_TAG_COLOR_SET.has(color)) {
    return color as AntdTagColor;
  }
  return undefined;
}

setupVbenVxeTable({
  configVxeTable: (vxeUI) => {
    vxeUI.setConfig({
      grid: {
        align: 'center',
        border: false,
        columnConfig: {
          resizable: true,
        },
        minHeight: 180,
        formConfig: {
          // 全局禁用vxe-table的表单配置，使用formOptions
          enabled: false,
        },
        proxyConfig: {
          autoLoad: true,
          response: {
            result: 'items',
            total: 'total',
            list: 'items',
          },
          showActiveMsg: true,
          showResponseMsg: false,
        },
        round: true,
        showOverflow: true,
        size: 'small',
      } as VxeTableGridOptions,
    });

    // 表格配置项可以用 cellRender: { name: 'CellImage' },
    vxeUI.renderer.add('CellImage', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { column, row } = params;
        return h(Image, { src: row[column.field], ...props });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellLink' },
    vxeUI.renderer.add('CellLink', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(
          Button,
          { size: 'small', type: 'link' },
          { default: () => props?.text },
        );
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellTag' }
    //
    // 支持两种调用形态：
    //   1) options 模式：cellRender: { name: 'CellTag', options: [
    //        { value, label, color? } ]
    //      } —— 查表拿 label/color，与 web-naive vxe-table adapter 同语义。
    //   2) props 函数模式（字典驱动场景）：cellRender: { name: 'CellTag',
    //      props: ({ row }) => ({ color, label }) }
    //      —— 颜色与文本一起由 props 函数返回，渲染时直接用。
    //      字典管理页里 sys_switch_status / sys_platform 列就是这种用法。
    //
    // antdv Tag 对 color 的接受范围（17 项 preset colors）：
    //   - 'magenta' / 'red' / 'volcano' / 'orange' / 'gold' / 'lime' / 'green' /
    //     'cyan' / 'blue' / 'geekblue' / 'purple' / 'default' / 'processing' /
    //     'success' / 'error' / 'warning' / 'yellow'
    //   - 其它值（undefined / '' / 旧值）→ 不传 color，Tag 走默认无色变体；
    //     与 web-naive NTag 的 NAIVE_TAG_TYPE_SET 白名单收敛语义对齐。
    vxeUI.renderer.add('CellTag', {
      renderTableDefault(renderOpts, params) {
        const { options, props } = renderOpts;
        const { column, row } = params;

        if (Array.isArray(options)) {
          // options 查表模式：按 row[column.field] 在 options 里命中。
          const value = (row as Record<string, unknown> | undefined)?.[
            column.field as string
          ];
          const tagItem = options.find((item) => item.value === value);
          const color = (tagItem?.color ?? 'default') as string;
          const label = tagItem?.label ?? String(value ?? '');
          return h(
            Tag,
            { color: normalizeAntdTagColor(color) },
            { default: () => label },
          );
        }

        // props 函数模式（字典驱动）。vxe-table 在不同版本下传给 renderer
        // 的 `props` 形态不一致——可能仍是函数也可能已求值。先按函数试
        // 一次求值；若拿到 undefined / 非对象则回退到读 props 自身字段。
        let resolved: Record<string, unknown> = {};
        if (typeof props === 'function') {
          try {
            const value = (props as (...args: unknown[]) => unknown)({
              row,
              column,
              rowIndex: params.rowIndex,
              columnIndex: params.columnIndex,
            });
            if (value && typeof value === 'object') {
              resolved = value as Record<string, unknown>;
            }
          } catch {
            // 函数求值失败，回退下方路径
          }
        } else if (props && typeof props === 'object') {
          resolved = props as Record<string, unknown>;
        }

        const color = (resolved.color ?? '') as string;
        const label = (resolved.label ?? resolved.text ?? '') as string;
        return h(
          Tag,
          { color: normalizeAntdTagColor(color) },
          { default: () => String(label) },
        );
      },
    });

    // 这里可以自行扩展 vxe-table 的全局配置，比如自定义格式化
    // vxeUI.formats.add
  },
  useVbenForm,
});

export const useVbenVxeGrid = <T extends Record<string, any>>(
  ...rest: Parameters<typeof useGrid<T, ComponentType, ComponentPropsMap>>
) => useGrid<T, ComponentType, ComponentPropsMap>(...rest);

export type * from '@vben/plugins/vxe-table';
