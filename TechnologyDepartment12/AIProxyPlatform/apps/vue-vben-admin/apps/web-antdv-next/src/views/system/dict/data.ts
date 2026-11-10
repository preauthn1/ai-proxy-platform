import type { VbenFormProps } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DictType } from '#/api/system/dict';

import { z } from '#/adapter/form';
import { fetchAllDictTypesApi } from '#/api/system/dict';

/* ============================================================
 * 平台（与 schema v8 + 后端 ?platform= 对齐）
 * ============================================================ */

/** 当前前端平台标识（VITE_APP_PLATFORM）。缺省 'general'。 */
export const DEFAULT_PLATFORM: string =
  (import.meta.env.VITE_APP_PLATFORM as string | undefined) || 'general';

/**
 * 字典项 platform 字段的候选下拉选项。
 * 仅展示「自己」+「通用」两项；本前端不允许选择其他平台。
 */
export const PLATFORM_OPTIONS: Array<{ label: string; value: string }> =
  (() => {
    const list: Array<{ label: string; value: string }> = [
      { label: '通用', value: 'general' },
    ];
    if (DEFAULT_PLATFORM !== 'general') {
      list.push({ label: DEFAULT_PLATFORM, value: DEFAULT_PLATFORM });
    }
    return list;
  })();

/**
 * 字典项 platform 字段的搜索下拉候选（搜索场景不限制「自己」，展示全部 3 项）。
 * 与 PLATFORM_OPTIONS（表单 Select）区分：搜索需要看全 platform；表单只允许维护自己。
 */
export const SEARCH_PLATFORM_OPTIONS: Array<{ label: string; value: string }> =
  [
    { label: '通用', value: 'general' },
    { label: 'React Admin', value: 'react-admin' },
    { label: 'Vue Admin', value: 'vue-admin' },
  ];

/**
 * 预设样式标识（与 schema v9 + mock ALLOWED_TAG_TYPES 对齐）。
 * 用于「字典项新增/编辑」抽屉的「预设样式」Select 选项，以及列表 CellTag 的 color 映射。
 * default 表示无样式；其余 16 项与 antd Tag preset colors 一一对应。
 */
export const TAG_TYPE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '默认', value: 'default' },
  { label: '主要', value: 'primary' },
  { label: '成功', value: 'success' },
  { label: '警告', value: 'warning' },
  { label: '危险', value: 'error' },
  { label: '进行中', value: 'processing' },
  { label: '洋红', value: 'magenta' },
  { label: '红色', value: 'red' },
  { label: '火山', value: 'volcano' },
  { label: '橙色', value: 'orange' },
  { label: '金色', value: 'gold' },
  { label: '青柠', value: 'lime' },
  { label: '绿色', value: 'green' },
  { label: '青色', value: 'cyan' },
  { label: '蓝色', value: 'blue' },
  { label: '极客蓝', value: 'geekblue' },
  { label: '紫色', value: 'purple' },
];

/**
 * antdv-next Tag 原生支持的 17 个 color preset（含 'default' / 'processing' 等）。
 * 列表渲染 CellTag 时：tag_type ∈ 该集合 → 列表渲染 Tag；否则按纯 label 文本渲染
 * （对齐 antdv Tag 的 16 项 preset colors + 'default'）。
 *
 * 与 web-naive 端 NAIVE_TAG_TYPE_SET（6 项）的差异：antdv Tag 多支持 11 项
 * （magenta / red / volcano / orange / gold / lime / green / cyan / blue /
 * geekblue / purple / processing）。本前端保留全 17 项以最大化展示能力。
 */
export const ANTD_TAG_COLOR_SET: Set<string> = new Set(
  TAG_TYPE_OPTIONS.map((o) => o.value),
);

/**
 * 按平台返回预设样式下拉选项。
 * - platform === 'general'（通用）→ 返回 []，开关被 disable、不展示下拉
 * - 其他（自己 / undefined）→ 返回 17 项全集
 *
 * antdv-next `<Tag color>` 与 antd 共享同一组 preset colors（13 项
 * preset + 5 项状态色，参见后端 ALLOWED_TAG_TYPES 17 项无 inverse 子集），
 * 所以下拉保留全部 17 项；与列表 CellTag 渲染保持一致。
 */
export function PLATFORM_TAG_TYPE_OPTIONS(
  platform: string | undefined,
): Array<{ label: string; value: string }> {
  if (platform === 'general') return [];
  return TAG_TYPE_OPTIONS;
}

export type TagType = (typeof TAG_TYPE_OPTIONS)[number]['value'];

/* ============================================================
 * 共享：字典类型下拉选项（用于左右两个搜索框的「类型编码」下拉）
 * ============================================================ */
function useDictTypeCodeOptions(multiple = false) {
  return {
    component: 'ApiSelect',
    componentProps: () => ({
      api: () => fetchAllDictTypesApi(),
      afterFetch: (data: DictType[]) =>
        data.map((t) => ({
          label: `${t.name}（${t.code}）`,
          value: t.code,
        })),
      allowClear: true,
      filterable: true,
      multiple,
      placeholder: '请选择类型编码',
    }),
  };
}

/* ============================================================
 * 字典类型编码校验：^[a-z][a-z0-9_]{0,63}$
 * ============================================================ */
export const DICT_CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

/* ============================================================
 * 字典类型 form schema（抽屉内表单）
 * ============================================================ */
export function useTypeFormSchema(): VbenFormProps['schema'] {
  return [
    {
      component: 'Input',
      fieldName: 'code',
      label: '类型编码',
      rules: z
        .string()
        .min(1, '请输入类型编码')
        .regex(DICT_CODE_PATTERN, '以小写字母开头，仅含字母数字下划线'),
      componentProps: { placeholder: '例如 sys_user_sex' },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '类型名称',
      rules: z.string().min(1, '请输入类型名称').max(64, '最长 64 字符'),
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: { placeholder: '选填', rows: 3 },
    },
    {
      component: 'Switch',
      fieldName: 'isEnabled',
      label: '启用',
      defaultValue: 1,
    },
  ];
}

/* ============================================================
 * 字典项 form schema（抽屉内表单）
 *
 * 布局策略（三段分组）：
 *   1. wrapperClass = grid-cols-2 → 大屏两列网格
 *   2. 每个分组前用一个 'Divider' 字段（fieldName=_section_*）跨满两列
 *   3. 跨列字段（所属类型 / 备注 / 预览）formItemClass='col-span-2'
 *   4. 两列并排字段自然成对（字典值+字典标签 / 排序+归属平台 / 是否默认+启用）
 *
 * usePresetStyle：默认开；编辑时回显由 row.tagType 决定（既非空又非 'default' → 开）。
 * tagType：预设样式标识；usePresetStyle=false 时强制 ''（列表按纯 label 文本渲染）；
 *           usePresetStyle=true 时沿用 schema 默认值 'primary'（"主要"）。
 * 预览：form.vue 模板里通过 `<template #tag_preview>` 注入，schema 中
 *       tag_preview 占位字段（Divider + col-span-2）只承担"挂载点"和
 *       dependencies.show 显隐控制；slot 完全替换默认渲染。
 * ============================================================ */
export function useDataFormSchema(): VbenFormProps['schema'] {
  return [
    // === 基础信息 ===
    {
      // 视觉分组标题（纯展示，提交时丢弃 _section_* 字段）
      component: 'Divider',
      fieldName: '_section_basic',
      formItemClass: 'col-span-2 mb-0',
      componentProps: { title: '基础信息', titlePlacement: 'left' },
    },
    {
      component: 'Select',
      fieldName: 'typeId',
      label: '所属类型',
      rules: 'selectRequired',
      formItemClass: 'col-span-2',
      componentProps: {
        // options 由 form.vue 的 buildSchema 注入 localTypeOptions
        options: [] as Array<{ label: string; value: number }>,
        filterable: true,
        placeholder: '请选择类型',
      },
    },
    {
      component: 'Input',
      fieldName: 'value',
      label: '字典值',
      rules: z.string().min(1, '请输入字典值').max(64, '最长 64 字符'),
      componentProps: { placeholder: '例如 Y / N / 0 / 1' },
    },
    {
      component: 'Input',
      fieldName: 'label',
      label: '字典标签',
      rules: z.string().min(1, '请输入字典标签').max(128, '最长 128 字符'),
      componentProps: { placeholder: '例如 是 / 否 / 启用' },
    },

    // === 样式设置 ===
    {
      component: 'Divider',
      fieldName: '_section_style',
      formItemClass: 'col-span-2 mb-0',
      componentProps: { title: '样式设置', titlePlacement: 'left' },
    },
    {
      component: 'Switch',
      fieldName: 'usePresetStyle',
      label: '开启预设样式',
      defaultValue: true,
    },
    {
      // 关键：依赖 usePresetStyle 字段，关闭时整行隐藏（避免视觉残留）。
      // 下拉 options 收敛到 antdv Tag 原生支持的子集（TAG_TYPE_OPTIONS，17 项）；
      // 与 PRD 行为契约表一致。runtime form 走 form.vue 手写 reactive model，
      // schema 仅作 reference；这里静态导出保证 schema 编译通过。
      component: 'Select',
      fieldName: 'tagType',
      label: '预设样式',
      defaultValue: 'primary',
      dependencies: {
        triggerFields: ['usePresetStyle'],
        show: (values: Record<string, unknown>) =>
          values.usePresetStyle !== false,
      },
      componentProps: {
        options: TAG_TYPE_OPTIONS,
        filterable: false,
        placeholder: '请选择预设样式',
      },
    },
    {
      // 实时预览占位字段：横跨整行（col-span-2），置于「开启预设样式 / 预设样式」下方；
      // 始终可见，由 form.vue 模板里 <template #tag_preview> 内部根据 previewUsePreset
      // 切换「彩色 Tag / 纯文本」（关闭预设样式时降级为纯文本）。
      // 占位字段本身不注册到 form.values，提交逻辑无需过滤。
      component: 'Divider',
      fieldName: 'tag_preview',
      hideLabel: true,
      formItemClass: 'col-span-2',
    },

    // === 其他属性 ===
    {
      component: 'Divider',
      fieldName: '_section_other',
      formItemClass: 'col-span-2 mb-0',
      componentProps: { title: '其他属性', titlePlacement: 'left' },
    },
    {
      component: 'InputNumber',
      fieldName: 'sort',
      label: '排序',
      defaultValue: 0,
      componentProps: { placeholder: '升序排序，0 排在前' },
    },
    {
      component: 'Select',
      fieldName: 'platform',
      label: '归属平台',
      defaultValue: DEFAULT_PLATFORM,
      componentProps: {
        options: PLATFORM_OPTIONS,
        filterable: false,
        placeholder: '请选择归属平台',
      },
    },
    {
      component: 'Switch',
      fieldName: 'isDefault',
      label: '是否默认',
      defaultValue: false,
    },
    {
      component: 'Switch',
      fieldName: 'isEnabled',
      label: '启用',
      defaultValue: true,
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      formItemClass: 'col-span-2',
      componentProps: { placeholder: '选填', rows: 3 },
    },
  ];
}

/**
 * 字典项表单的全局配置（两列布局）。在 form.vue 的 useVbenForm 中传入。
 */
export const DATA_FORM_COMMON_CONFIG: VbenFormProps['commonConfig'] = {
  componentProps: {
    class: 'w-full',
  },
};

/**
 * 字典项表单的 wrapperClass（两列网格）。
 *
 * 抽屉宽度约 640px，2 列网格刚好让「字典值+字典标签」「排序+平台」「是否默认+启用」
 * 配对同行。样式设置段额外新增 `tag_preview` 字段横跨整行
 * （form.vue 模板里通过 `<template #tag_preview>` 注入 Tag 预览），让 preview
 * 视觉上紧贴预设样式下拉。
 */
export const DATA_FORM_WRAPPER_CLASS = 'grid-cols-1 md:grid-cols-2';

/* ============================================================
 * 字典类型 列表检索 schema
 * ============================================================ */
export function useTypeSearchSchema(): VbenFormProps['schema'] {
  const typeCode = useDictTypeCodeOptions(true);
  return [
    { ...typeCode, fieldName: 'code', label: '类型编码' },
    { component: 'Input', fieldName: 'name', label: '类型名称' },
  ];
}

/* ============================================================
 * 字典项 列表检索 schema
 *
 * typeCode 由代码逻辑控制（点击行 / 关闭按钮），不进搜索表单。
 * platform 进搜索表单：下拉显示 3 项，UI 与「包含通用」checkbox 同行
 * （#form-platform 插槽内联渲染）。
 * includeGeneral：作为独立 schema 字段注册（不依赖插槽的 formApi.setFieldValue
 * 兜底），defaultValue=true 让首次 ajax.query 一定带上 includeGeneral=true。
 *   - show: false 让字段在搜索栏里隐藏（实际 UI 在 platform 插槽内）；
 *   - 这里 *不* 通过 component: 'Switch' 渲染，避免再占一个 form item 视觉位，
 *     用一个最小占位组件 + show:false，让 form 仍能感知到字段并把 defaultValue
 *     落到 form.values 里。
 * ============================================================ */
export function useDataSearchSchema(): VbenFormProps['schema'] {
  return [
    { component: 'Input', fieldName: 'value', label: '字典值' },
    {
      component: 'Select',
      fieldName: 'platform',
      label: '归属平台',
      defaultValue: DEFAULT_PLATFORM,
      controlClass: 'platform-control',
      componentProps: {
        options: SEARCH_PLATFORM_OPTIONS,
        allowClear: true,
        placeholder: '请选择归属平台',
      },
    },
    {
      // 关键：必须作为 schema 字段注册进 form，defaultValue 才会被 vben form
      // 落进 form.values（之前在 onMounted 里 setFieldValue 的兜底是 no-op，
      // 因为 form 里根本没有 includeGeneral 这个 field）。
      // dependencies.show: false 用 v-show 把 FormItem 隐藏（FormField 仍然挂载，
      // vee-validate 才能感知到字段；不能直接用 hide: true / if: false，否则
      // v-if 摘掉 FormField，field 不会被 useField 注册，form.values 里就没这个
      // 字段了）。triggerFields 不能为空数组（useDependencies 在 triggerFields
      // 为空时直接 return，连带 show 都不会求值），所以绑一个 platform 字段。
      component: 'Switch',
      fieldName: 'includeGeneral',
      defaultValue: true,
      dependencies: {
        triggerFields: ['platform'],
        show: false,
      },
    },
  ];
}

/* ============================================================
 * 表格列定义
 * 第一列 type='checkbox' 用于多选；批量工具栏由父组件通过 gridEvents
 * （checkboxChange / checkboxAll）拿到勾选行后渲染。
 *
 * 字典状态列 / 平台列走 slots（`dict_status` / `dict_platform`），由
 * index.vue 模板中根据 dictLookups 查表后渲染 <Tag>。
 * 选 slots 而不是 cellRender 是因为 vxe-table 在不同版本下对
 * cellRender.props 函数求值的语义不一致，模板插槽完全在 Vue 渲染
 * 体系内，颜色/文本随 dict 数据更新即时同步。
 * ============================================================ */
export function useTypeColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 44, fixed: 'left' },
    { field: 'id', title: 'ID', width: 80 },
    { field: 'code', title: '类型编码', width: 140, showOverflow: 'tooltip' },
    { field: 'name', title: '类型名称', width: 140, showOverflow: 'tooltip' },
    { field: 'remark', title: '备注', minWidth: 160, showOverflow: 'tooltip' },
    {
      slots: { default: 'dict_status' },
      field: 'isEnabled',
      title: '状态',
      width: 90,
    },
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

export function useDataColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 44, fixed: 'left' },
    { field: 'id', title: 'ID', width: 80 },
    {
      field: 'typeCode',
      title: '类型编码',
      width: 140,
      showOverflow: 'tooltip',
    },
    { field: 'value', title: '字典值', width: 120 },
    { field: 'label', title: '字典标签', width: 140, showOverflow: 'tooltip' },
    {
      slots: { default: 'dict_platform' },
      field: 'platform',
      title: '归属平台',
      width: 110,
    },
    { field: 'sort', title: '排序', width: 80 },
    {
      slots: { default: 'dict_default' },
      field: 'isDefault',
      title: '默认',
      width: 80,
    },
    {
      slots: { default: 'dict_status' },
      field: 'isEnabled',
      title: '状态',
      width: 90,
    },
    { field: 'remark', title: '备注', minWidth: 160, showOverflow: 'tooltip' },
    {
      field: 'action',
      title: '操作',
      fixed: 'right',
      slots: { default: 'action' },
      width: 140,
    },
  ];
}
