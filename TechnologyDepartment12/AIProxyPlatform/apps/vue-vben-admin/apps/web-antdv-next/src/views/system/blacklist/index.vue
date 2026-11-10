<script lang="ts" setup>
import type { VxeGridListeners } from '#/adapter/vxe-table';
import type {
  BlacklistScope,
  BlacklistTargetType,
  SysBlacklist,
} from '#/api/system/blacklist';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  batchBlacklistApi,
  deleteBlacklistApi,
  fetchBlacklistListApi,
  updateBlacklistApi,
} from '#/api/system/blacklist';
import { $t } from '#/locales';
import {
  SCOPE_COLOR,
  TARGET_TYPE_COLOR,
  useBlacklistColumns,
  useBlacklistSearchSchema,
} from '#/views/system/blacklist/data';
import BlacklistForm from '#/views/system/blacklist/modules/form.vue';

defineOptions({ name: 'SystemBlacklist' });

type BulkAction = 'delete' | 'disable' | 'enable';

const BULK_SUCCESS_KEY: Record<BulkAction, string> = {
  delete: 'system.blacklist.bulkDeleteOk',
  disable: 'system.blacklist.bulkDisableOk',
  enable: 'system.blacklist.bulkEnableOk',
};

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: BlacklistForm,
  destroyOnClose: true,
});

const selectedIds = ref<Set<number>>(new Set());
const bulkLoading = ref(false);
const rowActionLoading = ref<null | number>(null);

const gridEvents: VxeGridListeners<SysBlacklist> = {
  checkboxChange: ({ row, checked }) => {
    if (!row) return;
    const next = new Set(selectedIds.value);
    if (checked) next.add(row.id);
    else next.delete(row.id);
    selectedIds.value = next;
  },
  checkboxAll: ({ checked }) => {
    const records = (gridApi.grid?.getCheckboxRecords?.() ??
      []) as SysBlacklist[];
    selectedIds.value = checked ? new Set(records.map((r) => r.id)) : new Set();
  },
};

const [Grid, gridApi] = useVbenVxeGrid<SysBlacklist>({
  formOptions: {
    collapsed: false,
    schema: useBlacklistSearchSchema(),
    showCollapseButton: false,
  },
  gridEvents,
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: useBlacklistColumns(),
    keepSource: true,
    rowConfig: { isHover: true },
    size: 'small',
    stripe: true,
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          return await fetchBlacklistListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            targetType: formValues.targetType || undefined,
            targetValue: formValues.targetValue || undefined,
            scope: formValues.scope || undefined,
            status: formValues.status ?? undefined,
          });
        },
      },
    },
  } as never,
});

function openCreate() {
  formDrawerApi.setData({}).open();
}

function openEdit(row: SysBlacklist) {
  formDrawerApi.setData({ row }).open();
}

function clearSelection() {
  selectedIds.value = new Set();
  gridApi.grid?.clearCheckboxRow?.();
}

async function handleDelete(row: SysBlacklist) {
  rowActionLoading.value = row.id;
  try {
    await deleteBlacklistApi(row.id);
    message.success($t('system.blacklist.deleteOk'));
    clearSelection();
    gridApi.query();
  } catch (error) {
    message.error(
      `${$t('system.blacklist.deleteFailed')}：${(error as Error).message ?? ''}`,
    );
  } finally {
    rowActionLoading.value = null;
  }
}

/** 单条启停：走 update isEnabled */
async function toggleEnabled(row: SysBlacklist) {
  const next: 0 | 1 = row.isEnabled === 1 ? 0 : 1;
  rowActionLoading.value = row.id;
  try {
    await updateBlacklistApi({
      id: row.id,
      data: { isEnabled: next },
    });
    message.success(
      next === 1
        ? $t('system.blacklist.enableOk')
        : $t('system.blacklist.disableOk'),
    );
    gridApi.query();
  } catch (error) {
    message.error(
      `${$t('system.blacklist.saveFailed')}：${(error as Error).message ?? ''}`,
    );
  } finally {
    rowActionLoading.value = null;
  }
}

async function bulkAction(action: BulkAction) {
  const ids = [...selectedIds.value];
  if (ids.length === 0) {
    message.warning($t('system.blacklist.selectFirst'));
    return;
  }
  bulkLoading.value = true;
  try {
    await batchBlacklistApi({ action, ids });
    message.success($t(BULK_SUCCESS_KEY[action]));
    clearSelection();
    gridApi.query();
  } catch (error) {
    message.error(
      `${$t('system.blacklist.bulkFailed')}：${(error as Error).message ?? ''}`,
    );
  } finally {
    bulkLoading.value = false;
  }
}

function targetTypeColor(t: string) {
  return TARGET_TYPE_COLOR[t as BlacklistTargetType] ?? 'default';
}

function scopeColor(s: string) {
  return SCOPE_COLOR[s as BlacklistScope] ?? 'default';
}
</script>

<template>
  <Page>
    <Grid>
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <span
            v-if="selectedIds.size > 0"
            class="text-xs"
            style="color: var(--ant-color-text-secondary)"
          >
            {{ $t('system.blacklist.selected') }}
            <strong style="color: var(--ant-color-text)">{{
              selectedIds.size
            }}</strong>
            {{ $t('system.blacklist.selectedUnit') }}
          </span>
          <Button
            v-if="selectedIds.size > 0"
            size="small"
            :loading="bulkLoading"
            @click="bulkAction('enable')"
          >
            {{ $t('system.blacklist.bulkEnable') }}
          </Button>
          <Button
            v-if="selectedIds.size > 0"
            size="small"
            :loading="bulkLoading"
            @click="bulkAction('disable')"
          >
            {{ $t('system.blacklist.bulkDisable') }}
          </Button>
          <Popconfirm
            v-if="selectedIds.size > 0"
            :title="$t('system.blacklist.confirmBulkDelete')"
            :description="
              $t('system.blacklist.confirmBulkDeleteDesc', [selectedIds.size])
            "
            @confirm="bulkAction('delete')"
          >
            <Button size="small" danger ghost :loading="bulkLoading">
              {{ $t('system.blacklist.bulkDelete') }}
            </Button>
          </Popconfirm>
          <Button
            v-if="selectedIds.size === 0"
            type="primary"
            @click="openCreate"
          >
            + {{ $t('system.blacklist.create') }}
          </Button>
        </Space>
      </template>

      <template #targetType="{ row }">
        <Tag :color="targetTypeColor(row.targetType)" size="small">
          {{ row.targetType }}
        </Tag>
      </template>

      <template #scope="{ row }">
        <Tag :color="scopeColor(row.scope)" size="small">
          {{ row.scope }}
        </Tag>
      </template>

      <template #expiresAt="{ row }">
        <span v-if="row.expiresAt">{{ row.expiresAt }}</span>
        <span v-else style="color: #999">{{
          $t('system.blacklist.permanent')
        }}</span>
      </template>

      <template #isEnabled="{ row }">
        <Tag :color="row.isEnabled === 1 ? 'success' : 'default'" size="small">
          {{
            row.isEnabled === 1
              ? $t('system.blacklist.enabled')
              : $t('system.blacklist.disabled')
          }}
        </Tag>
      </template>

      <template #action="{ row }">
        <Space>
          <a @click="openEdit(row as SysBlacklist)">
            {{ $t('system.blacklist.editAction') }}
          </a>
          <a
            :style="
              rowActionLoading === (row as SysBlacklist).id
                ? { pointerEvents: 'none', opacity: 0.5 }
                : undefined
            "
            @click="toggleEnabled(row as SysBlacklist)"
          >
            {{
              (row as SysBlacklist).isEnabled === 1
                ? $t('system.blacklist.toggleDisable')
                : $t('system.blacklist.toggleEnable')
            }}
          </a>
          <Popconfirm
            :title="$t('system.blacklist.confirmDelete')"
            @confirm="handleDelete(row as SysBlacklist)"
          >
            <a style="color: #ff4d4f">{{ $t('system.blacklist.delete') }}</a>
          </Popconfirm>
        </Space>
      </template>
    </Grid>
    <FormDrawer @success="gridApi.query()" />
  </Page>
</template>
