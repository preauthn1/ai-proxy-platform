import type { VbenFormProps } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { I18nLocale } from '#/api/system/i18n';

import { fetchAllI18nLocalesApi } from '#/api/system/i18n';

/* ============================================================
 * 语言编码校验：BCP-47 风格（如 zh-CN / en-US / ja-JP）
 * ============================================================ */
export const I18N_LOCALE_CODE_PATTERN = /^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$/;

/* ============================================================
 * 语言 列表检索 schema
 * ============================================================ */
export function useLocaleSearchSchema(): VbenFormProps['schema'] {
  return [
    { component: 'Input', fieldName: 'code', label: '语言代码' },
    { component: 'Input', fieldName: 'name', label: '语言名称' },
  ];
}

/* ============================================================
 * 翻译 列表检索 schema
 * ============================================================ */
export function useTranslationSearchSchema(): VbenFormProps['schema'] {
  return [{ component: 'Input', fieldName: 'value', label: '键或值' }];
}

/* ============================================================
 * 表格列定义
 * 第一列 type='checkbox' 用于多选；批量工具栏由父组件通过 gridEvents
 * （checkboxChange / checkboxAll）拿到勾选行后渲染。
 * ============================================================ */
export function useLocaleColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 44, fixed: 'left' },
    { field: 'id', title: 'ID', width: 80 },
    { field: 'code', title: '语言代码', width: 140, showOverflow: 'tooltip' },
    { field: 'name', title: '语言名称', width: 140, showOverflow: 'tooltip' },
    {
      field: 'isDefault',
      title: '默认',
      width: 80,
      slots: { default: 'locale_default' },
    },
    { field: 'sort', title: '排序', width: 80 },
    {
      slots: { default: 'locale_status' },
      field: 'isEnabled',
      title: '状态',
      width: 90,
    },
    { field: 'remark', title: '备注', minWidth: 160, showOverflow: 'tooltip' },
    {
      field: 'updatedAt',
      title: '更新时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      fixed: 'right',
      slots: { default: 'action' },
      width: 140,
    },
  ];
}

export function useTranslationColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 44, fixed: 'left' },
    { field: 'id', title: 'ID', width: 80 },
    {
      field: 'translationKey',
      title: '翻译键',
      width: 220,
      showOverflow: 'tooltip',
    },
    {
      field: 'value',
      title: '翻译值',
      width: 220,
      showOverflow: 'tooltip',
    },
    {
      slots: { default: 'translation_status' },
      field: 'isEnabled',
      title: '状态',
      width: 90,
    },
    { field: 'remark', title: '备注', minWidth: 160, showOverflow: 'tooltip' },
    {
      field: 'updatedAt',
      title: '更新时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'action',
      title: '操作',
      fixed: 'right',
      slots: { default: 'action' },
      width: 140,
    },
  ];
}

/**
 * 默认视图：按 translationKey 聚合的主行列。
 * 通过 type='expand' 触发 vxe-table 展开行；展开内容由父组件
 * template #translation_key_expand 渲染 antdv-next Table 列出各 locale 子行。
 * 主行不显示 value/状态/备注/更新时间——这些在子行里展开后看。
 */
export function useTranslationKeyColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'expand', width: 44, slots: { content: 'translation_key_expand' } },
    {
      field: 'translationKey',
      title: '翻译键',
      minWidth: 240,
      showOverflow: 'tooltip',
    },
    {
      slots: { default: 'translation_key_count' },
      field: 'localeCount',
      title: '语言数',
      width: 100,
    },
    {
      field: 'action',
      title: '操作',
      fixed: 'right',
      slots: { default: 'translation_key_action' },
      width: 120,
    },
  ];
}

/**
 * 全部语言下拉（与字典类型下拉同构）：用于翻译新建/编辑时选择语言。
 * 不缓存：与父组件 typeOptions 同生命周期，避免 stale 数据。
 */
export async function fetchLocaleOptions(): Promise<
  Array<{ label: string; value: number }>
> {
  const list = await fetchAllI18nLocalesApi({ status: 1 });
  return list.map((l: I18nLocale) => ({
    label: `${l.name}（${l.code}）`,
    value: l.id,
  }));
}
