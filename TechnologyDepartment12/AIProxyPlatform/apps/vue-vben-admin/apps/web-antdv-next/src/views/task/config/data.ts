import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { $t } from '#/locales';

export const TASK_STATUS_OPTIONS = [
  { label: $t('task.config.enabled'), value: 1 },
  { label: $t('task.config.disabled'), value: 0 },
];

export function useConfigColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 48 },
    { field: 'id', title: 'ID', width: 70 },
    { field: 'code', title: $t('task.config.code'), minWidth: 130 },
    { field: 'name', title: $t('task.config.name'), minWidth: 120 },
    {
      field: 'workflowType',
      title: $t('task.config.workflowType'),
      minWidth: 160,
    },
    {
      field: 'taskQueue',
      title: $t('task.config.taskQueue'),
      minWidth: 110,
    },
    {
      field: 'cronExpr',
      title: $t('task.config.cronExpr'),
      minWidth: 140,
      slots: { default: 'cron' },
    },
    {
      field: 'timeoutSeconds',
      title: $t('task.config.timeoutSeconds'),
      width: 100,
    },
    {
      field: 'isEnabled',
      title: $t('task.config.status'),
      width: 90,
      slots: { default: 'status' },
    },
    {
      title: $t('task.config.actions'),
      fixed: 'right',
      width: 280,
      slots: { default: 'action' },
    },
  ];
}

export function useConfigSearchSchema(
  workflowTypeOptions: Array<{ label: string; value: string }> = [],
  taskQueueOptions: Array<{ label: string; value: string }> = [],
): Array<{
  component: string;
  componentProps?: Record<string, any>;
  fieldName: string;
  label: string;
}> {
  return [
    {
      component: 'Input',
      fieldName: 'code',
      label: $t('task.config.code'),
      componentProps: {
        placeholder: $t('task.config.codePlaceholder'),
        allowClear: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('task.config.name'),
      componentProps: {
        placeholder: $t('task.config.namePlaceholder'),
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'workflowType',
      label: $t('task.config.workflowType'),
      componentProps: {
        options: workflowTypeOptions,
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: $t('task.config.workflowTypeFilterPlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'taskQueue',
      label: $t('task.config.taskQueue'),
      componentProps: {
        options: taskQueueOptions,
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: $t('task.config.taskQueueFilterPlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: $t('task.config.status'),
      componentProps: {
        options: TASK_STATUS_OPTIONS,
        allowClear: true,
        placeholder: '—',
      },
    },
  ];
}
