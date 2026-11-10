import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TaskExecutionStatus } from '#/api/system/task-execution';

import { $t } from '#/locales';

export const EXECUTION_STATUS_OPTIONS: Array<{
  label: string;
  value: TaskExecutionStatus;
}> = [
  { label: $t('task.status.PENDING'), value: 'PENDING' },
  { label: $t('task.status.RUNNING'), value: 'RUNNING' },
  { label: $t('task.status.RETRYING'), value: 'RETRYING' },
  { label: $t('task.status.COMPLETED'), value: 'COMPLETED' },
  { label: $t('task.status.FAILED'), value: 'FAILED' },
  { label: $t('task.status.CANCELLED'), value: 'CANCELLED' },
  { label: $t('task.status.TERMINATED'), value: 'TERMINATED' },
  { label: $t('task.status.TIMED_OUT'), value: 'TIMED_OUT' },
  { label: $t('task.status.CONTINUED_AS_NEW'), value: 'CONTINUED_AS_NEW' },
];

export const STATUS_TAG_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  RETRYING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
  TERMINATED: 'error',
  TIMED_OUT: 'warning',
  CONTINUED_AS_NEW: 'blue',
};

/** 执行状态 i18n 标签；未知状态回退原文 */
export function executionStatusLabel(status: string): string {
  const key = `task.status.${status}`;
  const translated = $t(key);
  return translated === key ? status : translated;
}

/** 由 startedAt / closedAt 推算耗时文案 */
export function formatDuration(
  startedAt: null | string | undefined,
  closedAt: null | string | undefined,
): string {
  if (!startedAt || !closedAt) return '—';
  const from = Date.parse(startedAt);
  const to = Date.parse(closedAt);
  if (Number.isNaN(from) || Number.isNaN(to) || to < from) return '—';
  const sec = Math.floor((to - from) / 1000);
  if (sec < 60) return `${sec}s`;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  if (m < 60) return s > 0 ? `${m}m ${s}s` : `${m}m`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  return rm > 0 ? `${h}h ${rm}m` : `${h}h`;
}

export function useExecutionColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: 'ID', width: 70 },
    {
      field: 'workflowId',
      title: $t('task.execution.workflowId'),
      minWidth: 180,
    },
    { field: 'runId', title: $t('task.execution.runId'), minWidth: 120 },
    {
      field: 'configName',
      title: $t('task.execution.configName'),
      minWidth: 120,
      slots: { default: 'configName' },
    },
    {
      field: 'status',
      title: $t('task.execution.status'),
      width: 120,
      slots: { default: 'status' },
    },
    {
      field: 'retryCount',
      title: $t('task.execution.retryCount'),
      width: 90,
    },
    {
      field: 'startedAt',
      title: $t('task.execution.startedAt'),
      minWidth: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'closedAt',
      title: $t('task.execution.closedAt'),
      minWidth: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'waitDuration',
      title: $t('task.execution.waitDuration'),
      width: 100,
      slots: { default: 'waitDuration' },
    },
    {
      field: 'duration',
      title: $t('task.execution.duration'),
      width: 100,
      slots: { default: 'duration' },
    },
    {
      field: 'failureReason',
      title: $t('task.execution.failureReason'),
      minWidth: 160,
      showOverflow: true,
      slots: { default: 'failure' },
    },
  ];
}

export function useExecutionSearchSchema(
  configOptions: Array<{ label: string; value: number }> = [],
  workflowTypeOptions: Array<{ label: string; value: string }> = [],
): Array<{
  component: string;
  componentProps?: Record<string, any>;
  fieldName: string;
  label: string;
}> {
  return [
    {
      component: 'Select',
      fieldName: 'configId',
      label: $t('task.execution.filterConfig'),
      componentProps: {
        options: configOptions,
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: $t('task.execution.filterConfigPlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'workflowType',
      label: $t('task.execution.filterWorkflow'),
      componentProps: {
        options: workflowTypeOptions,
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: $t('task.execution.filterWorkflowPlaceholder'),
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: $t('task.execution.filterStatus'),
      componentProps: {
        options: EXECUTION_STATUS_OPTIONS,
        allowClear: true,
        placeholder: '—',
      },
    },
    {
      component: 'RangePicker',
      fieldName: 'startedAtRange',
      label: $t('task.execution.filterTime'),
      componentProps: {
        showTime: true,
        style: { width: '100%' },
      },
    },
  ];
}
