<script lang="ts" setup>
import type { TaskExecution } from '#/api/system/task-execution';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Descriptions, DescriptionsItem, Tag } from 'antdv-next';

import { $t } from '#/locales';
import {
  executionStatusLabel,
  formatDuration,
  STATUS_TAG_COLOR,
} from '#/views/task/execution/data';

const row = ref<null | TaskExecution>(null);

const [Drawer, drawerApi] = useVbenDrawer({
  footer: false,
  header: true,
  title: $t('task.execution.detailTitle'),
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      row.value = null;
      return;
    }
    const data = drawerApi.getData<{ row?: TaskExecution }>();
    row.value = data?.row ?? null;
  },
});

const statusLabel = computed(() => {
  if (!row.value?.status) return '—';
  return executionStatusLabel(row.value.status);
});

function dash(v: null | number | string | undefined) {
  if (v === null || v === undefined || v === '') return '—';
  return String(v);
}

function jsonText(v: null | Record<string, unknown> | undefined) {
  if (!v) return '—';
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return '—';
  }
}
</script>

<template>
  <Drawer class="w-[640px]">
    <Descriptions v-if="row" :column="1" bordered size="small">
      <DescriptionsItem label="ID">{{ row.id }}</DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.configName')">
        {{ row.configName || $t('task.execution.unknownConfig') }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.workflowId')">
        <span class="break-all">{{ dash(row.workflowId) }}</span>
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.runId')">
        <span class="break-all">{{ dash(row.runId) }}</span>
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.workflowType')">
        {{ dash(row.workflowType) }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.taskQueue')">
        {{ dash(row.taskQueue) }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.status')">
        <Tag :color="STATUS_TAG_COLOR[row.status] ?? 'default'">
          {{ statusLabel }}
        </Tag>
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.retryCount')">
        {{ row.retryCount ?? 0 }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.createdAt')">
        {{ formatDateTime(row.createdAt) || '—' }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.pendingAt')">
        {{ formatDateTime(row.pendingAt) || '—' }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.startedAt')">
        {{ formatDateTime(row.startedAt) || '—' }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.closedAt')">
        {{ formatDateTime(row.closedAt) || '—' }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.duration')">
        {{ formatDuration(row.startedAt, row.closedAt) }}
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.inputSummary')">
        <pre
          class="m-0 max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-xs"
          >{{ jsonText(row.inputSummary) }}</pre>
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.resultSummary')">
        <pre
          class="m-0 max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-xs"
          >{{ jsonText(row.resultSummary) }}</pre>
      </DescriptionsItem>
      <DescriptionsItem :label="$t('task.execution.failureReason')">
        <pre
          v-if="row.failureReason"
          class="m-0 max-h-40 overflow-auto whitespace-pre-wrap break-all font-sans text-sm"
          >{{ row.failureReason }}</pre>
        <span v-else style="color: var(--ant-color-text-secondary)">
          {{ $t('task.execution.noFailure') }}
        </span>
      </DescriptionsItem>
    </Descriptions>
  </Drawer>
</template>
