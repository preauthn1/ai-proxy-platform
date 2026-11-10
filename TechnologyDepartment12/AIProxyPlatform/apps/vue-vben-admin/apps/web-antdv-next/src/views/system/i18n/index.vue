<script lang="ts" setup>
import type { VxeGridListeners } from '#/adapter/vxe-table';
import type {
  I18nLocale,
  I18nTranslation,
  I18nTranslationKey,
} from '#/api/system/i18n';

import { ref, watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import {
  Button,
  Card,
  Col,
  message,
  Modal,
  Popconfirm,
  Row,
  Space,
  Table,
  Tag,
} from 'antdv-next';
import JSZip from 'jszip';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useDictLookups } from '#/api/system/dict/hooks';
import {
  fetchI18nLocaleListApi,
  fetchI18nTranslationKeyListApi,
  fetchI18nTranslationListApi,
} from '#/api/system/i18n';
import {
  useBatchI18nLocale,
  useBatchI18nTranslation,
  useDeleteI18nTranslation,
  useExportI18nBatch,
} from '#/api/system/i18n/hooks';
import {
  useLocaleColumns,
  useLocaleSearchSchema,
  useTranslationColumns,
  useTranslationKeyColumns,
  useTranslationSearchSchema,
} from '#/views/system/i18n/data';
import Form from '#/views/system/i18n/modules/form.vue';
import ImportModal from '#/views/system/i18n/modules/import-modal.vue';
import TranslationKeyDrawer from '#/views/system/i18n/modules/translation-key-drawer.vue';

defineOptions({ name: 'SystemI18n' });

type BulkAction = 'delete' | 'disable' | 'enable';

const BULK_SUCCESS_TEXT: Record<BulkAction, string> = {
  delete: '批量删除成功',
  disable: '批量禁用成功',
  enable: '批量启用成功',
};

const selectedLocaleId = ref<null | number>(null);
const selectedLocale = ref<I18nLocale | null>(null);

const entryLocaleCode = ref<string | undefined>(undefined);

const localeSelectedIds = ref<Set<number>>(new Set());
const translationSelectedIds = ref<Set<number>>(new Set());
const bulkLoading = ref({ locale: false, translation: false });

const exportType = ref<'raw' | 'simple'>('simple');
const importModalOpen = ref(false);
const exportModalOpen = ref(false);

const [LocaleFormDrawer, localeFormDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

// 翻译多语抽屉：TranslationKeyDrawer 自己持有 useVbenDrawer + props 直传,
// 父组件通过 v-model:open + :source-row 直传参数 (不走 connectedComponent
// 的 setData 通道, 避免响应式同步问题)。

const editingTranslation = ref<I18nTranslation | null>(null);
const translationDrawerOpen = ref(false);

// 字典驱动：locale_default / translation_status 走 useDictLookups，
// 颜色与文本由 sys_default_status / sys_switch_status 字典供给。
const dictLookups = useDictLookups();

// mutation hooks：对齐 role/menu index.vue 的分层（裸 API 列表 + hooks mutation）
const deleteTranslationMut = useDeleteI18nTranslation({
  onSuccess: () => message.success('删除成功'),
  onError: (err: Error) =>
    message.error(`删除失败：${err.message ?? '未知错误'}`),
});
const batchLocaleMut = useBatchI18nLocale({
  onSuccess: (_res, vars) => message.success(BULK_SUCCESS_TEXT[vars.action]),
  onError: (err: Error) =>
    message.error(`批量操作失败：${err.message ?? '未知错误'}`),
});
const batchTranslationMut = useBatchI18nTranslation({
  onSuccess: (_res, vars) => message.success(BULK_SUCCESS_TEXT[vars.action]),
  onError: (err: Error) =>
    message.error(`批量操作失败：${err.message ?? '未知错误'}`),
});
const exportBatchMut = useExportI18nBatch({
  onError: (err: Error) =>
    message.error(`导出失败：${err.message ?? '未知错误'}`),
});

const localeColumns: ReturnType<typeof useLocaleColumns> = useLocaleColumns();

const localeGridEvents: VxeGridListeners<I18nLocale> = {
  currentRowChange: ({ row }) => {
    if (!row) return;
    entryLocaleCode.value = row.code;
    selectedLocaleId.value = row.id;
    selectedLocale.value = row;
    translationGridApi.reload();
    translationKeyGridApi.reload();
  },
  checkboxChange: ({ row, checked }) => {
    if (!row) return;
    const next = new Set(localeSelectedIds.value);
    if (checked) next.add(row.id);
    else next.delete(row.id);
    localeSelectedIds.value = next;
  },
  checkboxAll: ({ checked }) => {
    const records = (localeGridApi.grid?.getCheckboxRecords?.() ??
      []) as I18nLocale[];
    localeSelectedIds.value = checked
      ? new Set(records.map((r) => r.id))
      : new Set();
  },
};

const [LocaleGrid, localeGridApi] = useVbenVxeGrid<I18nLocale>({
  formOptions: {
    collapsed: false,
    schema: useLocaleSearchSchema(),
    showCollapseButton: false,
  },
  gridEvents: localeGridEvents,
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: localeColumns,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, unknown>,
        ) => {
          return await fetchI18nLocaleListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            code: (formValues.code as string) || undefined,
            name: (formValues.name as string) || undefined,
          });
        },
      },
    },
    rowConfig: { isCurrent: true },
    size: 'small',
    stripe: true,
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
  } as never,
});

const translationColumns: ReturnType<typeof useTranslationColumns> =
  useTranslationColumns();

const translationGridEvents: VxeGridListeners<I18nTranslation> = {
  checkboxChange: ({ row, checked }) => {
    if (!row) return;
    const next = new Set(translationSelectedIds.value);
    if (checked) next.add(row.id);
    else next.delete(row.id);
    translationSelectedIds.value = next;
  },
  checkboxAll: ({ checked }) => {
    const records = (translationGridApi.grid?.getCheckboxRecords?.() ??
      []) as I18nTranslation[];
    translationSelectedIds.value = checked
      ? new Set(records.map((r) => r.id))
      : new Set();
  },
};

const [TranslationGrid, translationGridApi] = useVbenVxeGrid<I18nTranslation>({
  formOptions: {
    collapsed: false,
    schema: useTranslationSearchSchema(),
    showCollapseButton: false,
  },
  gridEvents: translationGridEvents,
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: translationColumns,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, unknown>,
        ) => {
          return await fetchI18nTranslationListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            localeCode: entryLocaleCode.value,
            value: (formValues.value as string) || undefined,
          });
        },
      },
    },
    size: 'small',
    stripe: true,
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
  } as never,
});

/* ============================================================
 * 默认视图：按 translationKey 聚合的主行 + 展开子表
 * ============================================================ */

const translationKeyColumns: ReturnType<typeof useTranslationKeyColumns> =
  useTranslationKeyColumns();

const expandedKeyRows = ref<Set<string>>(new Set());
const childRowsByKey = ref<Record<string, I18nTranslation[]>>({});
const childLoadingKey = ref<null | string>(null);

const [TranslationKeyGrid, translationKeyGridApi] =
  useVbenVxeGrid<I18nTranslationKey>({
    formOptions: {
      collapsed: false,
      schema: useTranslationSearchSchema(),
      showCollapseButton: false,
    },
    gridEvents: {
      toggleRowExpand: ({ expanded, row }) => {
        onExpandRowChange({ expanded, row });
      },
    } as VxeGridListeners<I18nTranslationKey>,
    gridOptions: {
      columns: translationKeyColumns,
      keepSource: true,
      proxyConfig: {
        ajax: {
          query: async (
            { page }: { page: { currentPage: number; pageSize: number } },
            formValues: Record<string, unknown>,
          ) => {
            return await fetchI18nTranslationKeyListApi({
              page: page.currentPage,
              pageSize: page.pageSize,
              value: (formValues.value as string) || undefined,
            });
          },
        },
      },
      size: 'small',
      stripe: true,
      toolbarConfig: {
        custom: true,
        refresh: true,
        zoom: true,
      },
    } as never,
  });

async function ensureChildRows(row: I18nTranslationKey) {
  if (childRowsByKey.value[row.translationKey]) return;
  childLoadingKey.value = row.translationKey;
  try {
    // byKey 接口返回精简 values（缺 translationKey/updatedAt），无法满足子表
    // 编辑与展示，仍需完整 I18nTranslation 行。后端暂无「按 key 取完整翻译行」
    // 接口，故保留全量拉取 + 前端 filter；待后端补字段后可切 byKey 消除 N+1。
    const res = await fetchI18nTranslationListApi({
      page: 1,
      pageSize: 1000,
    });
    childRowsByKey.value = {
      ...childRowsByKey.value,
      [row.translationKey]: res.items.filter(
        (it) => it.translationKey === row.translationKey,
      ),
    };
  } finally {
    childLoadingKey.value = null;
  }
}

async function onExpandRowChange(payload: {
  expanded: boolean;
  row?: I18nTranslationKey;
}) {
  if (!payload.row) return;
  const { translationKey } = payload.row;
  const next = new Set(expandedKeyRows.value);
  if (payload.expanded) {
    next.add(translationKey);
    await ensureChildRows(payload.row);
  } else {
    next.delete(translationKey);
  }
  expandedKeyRows.value = next;
}

async function deleteChildRow(parentKey: string, row: I18nTranslation) {
  await deleteTranslationMut.mutateAsync(row.id);
  childRowsByKey.value = {
    ...childRowsByKey.value,
    [parentKey]: (childRowsByKey.value[parentKey] ?? []).filter(
      (it) => it.id !== row.id,
    ),
  };
  // 主行 localeCount 也变了 -> reload 主表
  translationKeyGridApi.reload();
}

// 双表视图（collapsed view）的行删除：走 mutation hook，避免模板内联裸 API
async function deleteTranslationRow(row: I18nTranslation) {
  await deleteTranslationMut.mutateAsync(row.id);
  translationGridApi.reload();
}

function editChildRow(row: I18nTranslation) {
  openTranslationEdit(row);
}

function editKeyRow(row: I18nTranslationKey) {
  // 用 sampleRowId 作为 sourceRow（抽屉内部走 byKeyQuery，只看 translationKey）
  const placeholder: I18nTranslation = {
    id: row.sampleRowId,
    localeId: row.sampleLocaleId,
    translationKey: row.translationKey,
    localeCode: row.sampleLocaleCode,
    value: '',
    remark: '',
    isEnabled: 1,
    deletedAt: 0,
    createdAt: '',
    updatedAt: row.sampleUpdatedAt,
  };
  openTranslationEdit(placeholder);
}

const isCollapsedView = ref(true);

watch(selectedLocaleId, (val) => {
  isCollapsedView.value = val === null;
});

function openLocaleCreate() {
  localeFormDrawerApi.setData({}).open();
}

function openTranslationCreate() {
  editingTranslation.value = null;
  translationDrawerOpen.value = true;
}

function openTranslationEdit(row: I18nTranslation) {
  editingTranslation.value = row;
  translationDrawerOpen.value = true;
}

function clearTranslationSelection() {
  entryLocaleCode.value = undefined;
  selectedLocaleId.value = null;
  selectedLocale.value = null;
  translationGridApi.reload();
  translationKeyGridApi.reload();
}

function onLocaleSaved() {
  localeGridApi.reload();
  if (selectedLocaleId.value === null) {
    clearTranslationSelection();
  } else {
    translationGridApi.reload();
    translationKeyGridApi.reload();
  }
}

async function bulkLocaleAction(action: BulkAction) {
  const ids = [...localeSelectedIds.value];
  if (ids.length === 0) {
    message.warning('请先勾选要操作的语言');
    return;
  }
  bulkLoading.value.locale = true;
  try {
    await batchLocaleMut.mutateAsync({ action, ids });
    localeSelectedIds.value = new Set();
    localeGridApi.grid?.clearCheckboxRow?.();
    localeGridApi.reload();
    if (action === 'delete' && selectedLocaleId.value !== null) {
      clearTranslationSelection();
    }
  } finally {
    bulkLoading.value.locale = false;
  }
}

async function bulkTranslationAction(action: BulkAction) {
  const ids = [...translationSelectedIds.value];
  if (ids.length === 0) {
    message.warning('请先勾选要操作的翻译');
    return;
  }
  bulkLoading.value.translation = true;
  try {
    await batchTranslationMut.mutateAsync({ action, ids });
    translationSelectedIds.value = new Set();
    translationGridApi.grid?.clearCheckboxRow?.();
    translationGridApi.reload();
  } finally {
    bulkLoading.value.translation = false;
  }
}

function openExportModal() {
  if (localeSelectedIds.value.size === 0) {
    message.warning('请先勾选要导出的语言');
    return;
  }
  exportModalOpen.value = true;
}

function openImportModal() {
  importModalOpen.value = true;
}

async function confirmExport() {
  const ids = [...localeSelectedIds.value];
  const data = await exportBatchMut.mutateAsync({
    ids,
    format: exportType.value,
  });
  const zip = new JSZip();
  for (const file of data.files) {
    const filename = `${file.code}.${file.format}.json`;
    zip.file(filename, JSON.stringify(file.content, null, 2));
  }
  const blob = await zip.generateAsync({ type: 'blob' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `i18n-export-${exportType.value}.zip`;
  a.click();
  URL.revokeObjectURL(url);
  message.success(`导出成功：${data.files.length} 个语言已打包下载`);
  exportModalOpen.value = false;
}

function onImportSuccess() {
  localeGridApi.reload();
  translationGridApi.reload();
  translationKeyGridApi.reload();
}

/* 子表列（主行展开后显示）—— antdv-next Table 用 key/title/bodyCell 插槽 */
const childColumns = [
  { key: 'localeCode', title: '语言', dataIndex: 'localeCode', width: 120 },
  {
    key: 'value',
    title: '翻译值',
    dataIndex: 'value',
    ellipsis: true,
    width: 240,
  },
  {
    key: 'isEnabled',
    title: '状态',
    dataIndex: 'isEnabled',
    width: 90,
  },
  {
    key: 'remark',
    title: '备注',
    dataIndex: 'remark',
    ellipsis: true,
    width: 160,
  },
  {
    key: 'updatedAt',
    title: '更新时间',
    dataIndex: 'updatedAt',
    width: 170,
  },
  { key: 'childAction', title: '操作', width: 140, fixed: 'right' as const },
];

function formatUpdatedAt(value: string | undefined): string {
  return formatDateTime(value) || '-';
}
</script>

<template>
  <Page auto-content-height>
    <Row :gutter="16">
      <Col :span="24" :md="12">
        <Card>
          <LocaleGrid table-title="语言">
            <template #toolbar-tools>
              <Space :size="8" align="center">
                <span
                  v-if="localeSelectedIds.size > 0"
                  class="text-xs"
                  style="color: var(--ant-color-text-secondary)"
                >
                  已选
                  <strong style="color: var(--ant-color-text)">{{
                    localeSelectedIds.size
                  }}</strong>
                  条
                </span>
                <Button
                  v-if="localeSelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.locale"
                  @click="bulkLocaleAction('enable')"
                >
                  批量启用
                </Button>
                <Button
                  v-if="localeSelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.locale"
                  @click="bulkLocaleAction('disable')"
                >
                  批量禁用
                </Button>
                <Popconfirm
                  v-if="localeSelectedIds.size > 0"
                  title="确定要删除选中的语言？"
                  :description="`若仍有翻译将无法删除；默认语言禁止删除。当前共 ${localeSelectedIds.size} 项。`"
                  @confirm="bulkLocaleAction('delete')"
                >
                  <Button
                    size="small"
                    danger
                    ghost
                    :loading="bulkLoading.locale"
                  >
                    批量删除
                  </Button>
                </Popconfirm>
                <!-- 导入 / 导出 -->
                <Button
                  v-if="localeSelectedIds.size === 0"
                  size="small"
                  @click="openImportModal"
                >
                  导入
                </Button>
                <Button
                  size="small"
                  :disabled="localeSelectedIds.size === 0"
                  @click="openExportModal"
                >
                  导出
                </Button>
                <Button
                  v-if="localeSelectedIds.size === 0"
                  type="primary"
                  @click="openLocaleCreate"
                >
                  新建语言
                </Button>
              </Space>
            </template>
            <template #action="{ row }">
              <Space>
                <Button
                  type="link"
                  size="small"
                  @click="localeFormDrawerApi.setData(row).open()"
                >
                  编辑
                </Button>
                <Popconfirm
                  title="确定要删除该语言？"
                  description="若仍有翻译将无法删除；默认语言禁止删除。"
                >
                  <Button type="link" size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            </template>

            <template #locale_default="{ row }">
              <Tag
                :color="
                  dictLookups.lookupDefaultTagType(row.isDefault as 0 | 1) ??
                  'default'
                "
                size="small"
              >
                {{ dictLookups.lookupDefaultLabel(row.isDefault as 0 | 1) }}
              </Tag>
            </template>
            <template #locale_status="{ row }">
              <Tag
                :color="row.isEnabled === 1 ? 'success' : 'default'"
                size="small"
              >
                {{ row.isEnabled === 1 ? '启用' : '禁用' }}
              </Tag>
            </template>
          </LocaleGrid>
        </Card>
      </Col>

      <Col :span="24" :md="12">
        <Card>
          <template v-if="isCollapsedView">
            <TranslationKeyGrid>
              <template #table-title>
                <Space align="center" :size="8">
                  <span style="font-size: 16px">翻译</span>
                  <Tag color="default" style="font-size: 14px">
                    按 key 聚合视图
                  </Tag>
                </Space>
              </template>
              <template #toolbar-tools>
                <Button type="primary" @click="openTranslationCreate">
                  新建翻译
                </Button>
              </template>
              <template #translation_key_count="{ row }">
                <Tag color="blue" size="small">
                  {{ row.localeCount }} 语言
                </Tag>
              </template>
              <template #translation_key_action="{ row }">
                <Button type="link" size="small" @click="editKeyRow(row)">
                  编辑
                </Button>
              </template>
              <template #translation_key_expand="{ row }">
                <div style="padding: 12px 24px">
                  <Table
                    :columns="childColumns"
                    :data-source="childRowsByKey[row.translationKey] ?? []"
                    :loading="childLoadingKey === row.translationKey"
                    :pagination="false"
                    size="small"
                    row-key="id"
                    :scroll="{ x: 'max-content' }"
                  >
                    <template #bodyCell="{ column, record: childRow }">
                      <template v-if="column.key === 'localeCode'">
                        <Tag color="default" size="small">
                          {{ childRow.localeCode || '-' }}
                        </Tag>
                      </template>
                      <template v-else-if="column.key === 'isEnabled'">
                        <Tag
                          :color="
                            dictLookups.lookupSwitchTagType(
                              childRow.isEnabled as 0 | 1,
                            ) ?? 'default'
                          "
                          size="small"
                        >
                          {{
                            dictLookups.lookupSwitchLabel(
                              childRow.isEnabled as 0 | 1,
                            )
                          }}
                        </Tag>
                      </template>
                      <template v-else-if="column.key === 'remark'">
                        <span v-if="childRow.remark" :title="childRow.remark">
                          {{ childRow.remark }}
                        </span>
                        <span v-else style="color: #999">-</span>
                      </template>
                      <template v-else-if="column.key === 'updatedAt'">
                        {{ formatUpdatedAt(childRow.updatedAt) }}
                      </template>
                      <template v-else-if="column.key === 'childAction'">
                        <Space :size="4">
                          <Button
                            type="link"
                            size="small"
                            @click="editChildRow(childRow)"
                          >
                            编辑
                          </Button>
                          <Popconfirm
                            title="确定要删除该翻译？"
                            @confirm="
                              deleteChildRow(row.translationKey, childRow)
                            "
                          >
                            <Button type="link" size="small" danger>
                              删除
                            </Button>
                          </Popconfirm>
                        </Space>
                      </template>
                      <template v-else>
                        {{
                          childRow[column.dataIndex as keyof I18nTranslation]
                        }}
                      </template>
                    </template>
                  </Table>
                </div>
              </template>
            </TranslationKeyGrid>
          </template>
          <template v-else>
            <TranslationGrid>
              <template #table-title>
                <Space align="center" :size="8">
                  <span style="font-size: 16px">翻译</span>
                  <Tag
                    v-if="selectedLocale"
                    color="blue"
                    closable
                    style="font-size: 14px"
                    @close="clearTranslationSelection"
                  >
                    {{ selectedLocale.name }}（{{ selectedLocale.code }}）
                  </Tag>
                </Space>
              </template>
              <template #translation_status="{ row }">
                <Tag
                  :color="
                    dictLookups.lookupSwitchTagType(row.isEnabled as 0 | 1) ??
                    'default'
                  "
                  size="small"
                >
                  {{ dictLookups.lookupSwitchLabel(row.isEnabled as 0 | 1) }}
                </Tag>
              </template>
              <template #toolbar-tools>
                <Space :size="8" align="center">
                  <span
                    v-if="translationSelectedIds.size > 0"
                    class="text-xs"
                    style="color: var(--ant-color-text-secondary)"
                  >
                    已选
                    <strong style="color: var(--ant-color-text)">{{
                      translationSelectedIds.size
                    }}</strong>
                    条
                  </span>
                  <Button
                    v-if="translationSelectedIds.size > 0"
                    size="small"
                    :loading="bulkLoading.translation"
                    @click="bulkTranslationAction('enable')"
                  >
                    批量启用
                  </Button>
                  <Button
                    v-if="translationSelectedIds.size > 0"
                    size="small"
                    :loading="bulkLoading.translation"
                    @click="bulkTranslationAction('disable')"
                  >
                    批量禁用
                  </Button>
                  <Popconfirm
                    v-if="translationSelectedIds.size > 0"
                    title="确定要删除选中的翻译？"
                    @confirm="bulkTranslationAction('delete')"
                  >
                    <Button
                      size="small"
                      danger
                      ghost
                      :loading="bulkLoading.translation"
                    >
                      批量删除
                    </Button>
                  </Popconfirm>
                  <Button
                    v-if="translationSelectedIds.size === 0"
                    type="primary"
                    @click="openTranslationCreate"
                  >
                    新建翻译
                  </Button>
                </Space>
              </template>
              <template #action="{ row }">
                <Space>
                  <Button
                    type="link"
                    size="small"
                    @click="openTranslationEdit(row)"
                  >
                    编辑
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    danger
                    @click="deleteTranslationRow(row)"
                  >
                    删除
                  </Button>
                </Space>
              </template>
            </TranslationGrid>
          </template>
        </Card>
      </Col>
    </Row>

    <LocaleFormDrawer kind="locale" @success="onLocaleSaved" />
    <ImportModal v-model:open="importModalOpen" @success="onImportSuccess" />
    <Modal
      v-model:open="exportModalOpen"
      title="导出 JSON"
      :width="400"
      :destroy-on-close="true"
      ok-text="导出"
      cancel-text="取消"
      @ok="confirmExport"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm" style="color: var(--ant-color-text-secondary)">
          导出格式
        </span>
        <Button
          size="small"
          :type="exportType === 'simple' ? 'primary' : 'default'"
          @click="exportType = 'simple'"
        >
          Simple
        </Button>
        <Button
          size="small"
          :type="exportType === 'raw' ? 'primary' : 'default'"
          @click="exportType = 'raw'"
        >
          Raw
        </Button>
      </div>
    </Modal>
    <TranslationKeyDrawer
      v-model:open="translationDrawerOpen"
      :source-row="editingTranslation"
      :default-locale-code="entryLocaleCode ?? 'zh-CN'"
      initial-key=""
      @success="
        () => {
          translationGridApi.reload();
          translationKeyGridApi.reload();
        }
      "
    />
  </Page>
</template>
