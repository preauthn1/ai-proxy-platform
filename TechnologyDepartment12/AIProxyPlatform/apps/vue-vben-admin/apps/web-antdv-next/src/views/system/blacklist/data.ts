import type { VbenFormProps } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  BlacklistScope,
  BlacklistTargetType,
} from '#/api/system/blacklist';

import { $t } from '#/locales';

/* ============================================================
 * target / scope / 状态 常量
 * ============================================================ */

export const TARGET_TYPE_OPTIONS: Array<{
  label: string;
  value: BlacklistTargetType;
}> = [
  { label: 'IP', value: 'IP' },
  { label: 'USER', value: 'USER' },
  { label: 'DEVICE', value: 'DEVICE' },
];

export const SCOPE_OPTIONS: Array<{ label: string; value: BlacklistScope }> = [
  { label: 'LOGIN', value: 'LOGIN' },
  { label: 'API', value: 'API' },
  { label: 'ALL', value: 'ALL' },
];

export const TARGET_TYPE_COLOR: Record<BlacklistTargetType, string> = {
  IP: 'blue',
  USER: 'purple',
  DEVICE: 'orange',
};

export const SCOPE_COLOR: Record<BlacklistScope, string> = {
  LOGIN: 'cyan',
  API: 'geekblue',
  ALL: 'magenta',
};

export function getStatusOptions() {
  return [
    { label: $t('system.blacklist.enabled'), value: 1 },
    { label: $t('system.blacklist.disabled'), value: 0 },
  ];
}

/* ============================================================
 * 列表列
 * ============================================================ */
export function useBlacklistColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 48 },
    { field: 'id', title: 'ID', width: 80 },
    {
      field: 'targetType',
      title: $t('system.blacklist.targetType'),
      width: 100,
      slots: { default: 'targetType' },
    },
    {
      field: 'targetValue',
      title: $t('system.blacklist.targetValue'),
      minWidth: 140,
    },
    {
      field: 'scope',
      title: $t('system.blacklist.scope'),
      width: 100,
      slots: { default: 'scope' },
    },
    {
      field: 'startsAt',
      title: $t('system.blacklist.startsAt'),
      width: 170,
    },
    {
      field: 'expiresAt',
      title: $t('system.blacklist.expiresAt'),
      width: 170,
      slots: { default: 'expiresAt' },
    },
    {
      field: 'reason',
      title: $t('system.blacklist.reason'),
      minWidth: 120,
      showOverflow: true,
    },
    {
      field: 'remark',
      title: $t('system.blacklist.remark'),
      minWidth: 100,
      showOverflow: true,
    },
    {
      field: 'isEnabled',
      title: $t('system.blacklist.isEnabled'),
      width: 80,
      slots: { default: 'isEnabled' },
    },
    {
      title: $t('system.blacklist.action'),
      fixed: 'right',
      width: 180,
      slots: { default: 'action' },
    },
  ];
}

/* ============================================================
 * 搜索 schema
 * ============================================================ */
export function useBlacklistSearchSchema(): VbenFormProps['schema'] {
  return [
    {
      component: 'Select',
      fieldName: 'targetType',
      label: $t('system.blacklist.targetType'),
      componentProps: {
        options: TARGET_TYPE_OPTIONS,
        allowClear: true,
        placeholder: $t('system.blacklist.all'),
      },
    },
    {
      component: 'Input',
      fieldName: 'targetValue',
      label: $t('system.blacklist.targetValue'),
      componentProps: {
        placeholder: $t('system.blacklist.targetValuePlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'scope',
      label: $t('system.blacklist.scope'),
      componentProps: {
        options: SCOPE_OPTIONS,
        allowClear: true,
        placeholder: $t('system.blacklist.all'),
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: $t('system.blacklist.isEnabled'),
      componentProps: {
        options: getStatusOptions(),
        allowClear: true,
        placeholder: $t('system.blacklist.all'),
      },
    },
  ];
}

/* ============================================================
 * 表单 schema（抽屉）
 * ============================================================ */
export function useBlacklistFormSchema(): VbenFormProps['schema'] {
  return [
    {
      component: 'Select',
      fieldName: 'targetType',
      label: $t('system.blacklist.targetType'),
      defaultValue: 'IP',
      rules: 'required',
      componentProps: {
        options: TARGET_TYPE_OPTIONS,
        placeholder: $t('system.blacklist.selectTargetType'),
      },
    },
    {
      component: 'Input',
      fieldName: 'targetValue',
      label: $t('system.blacklist.targetValue'),
      rules: 'required',
      componentProps: {
        placeholder: $t('system.blacklist.targetValuePlaceholder'),
        maxlength: 128,
      },
    },
    {
      component: 'Select',
      fieldName: 'scope',
      label: $t('system.blacklist.scope'),
      defaultValue: 'ALL',
      rules: 'required',
      componentProps: {
        options: SCOPE_OPTIONS,
        placeholder: $t('system.blacklist.selectScope'),
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'startsAt',
      label: $t('system.blacklist.startsAt'),
      rules: 'required',
      componentProps: {
        showTime: true,
        style: { width: '100%' },
        placeholder: $t('system.blacklist.startsAtPlaceholder'),
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'expiresAt',
      label: $t('system.blacklist.expiresAt'),
      componentProps: {
        showTime: true,
        allowClear: true,
        style: { width: '100%' },
        placeholder: $t('system.blacklist.expiresAtPlaceholder'),
      },
    },
    {
      component: 'Textarea',
      fieldName: 'reason',
      label: $t('system.blacklist.reason'),
      componentProps: {
        placeholder: $t('system.blacklist.reasonPlaceholder'),
        rows: 2,
        maxlength: 512,
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('system.blacklist.remark'),
      componentProps: {
        placeholder: $t('system.blacklist.remarkPlaceholder'),
        rows: 2,
        maxlength: 512,
      },
    },
    {
      component: 'Switch',
      fieldName: 'isEnabled',
      label: $t('system.blacklist.isEnabled'),
      defaultValue: true,
    },
  ];
}
