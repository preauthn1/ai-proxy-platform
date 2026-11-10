<script lang="ts" setup>
import type { VxeGridListeners } from '#/adapter/vxe-table';
import type { DictData, DictType } from '#/api/system/dict';

import { onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import {
  Button,
  Card,
  Checkbox,
  Col,
  message,
  Popconfirm,
  Row,
  Select,
  Space,
  Tag,
} from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  batchDictDataApi,
  batchDictTypeApi,
  deleteDictDataApi,
  fetchAllDictTypesApi,
  fetchDictDataListApi,
  fetchDictTypeListApi,
} from '#/api/system/dict';
import { useDictLookups } from '#/api/system/dict/hooks';
import {
  DEFAULT_PLATFORM,
  SEARCH_PLATFORM_OPTIONS,
  useDataColumns,
  useDataSearchSchema,
  useTypeColumns,
  useTypeSearchSchema,
} from '#/views/system/dict/data';
import Form from '#/views/system/dict/modules/form.vue';

defineOptions({ name: 'SystemDict' });

// 与 React 版 getCurrentPlatform() 一致：取当前环境的 platform，
// 找不到时兜底为 'general'（与 VITE_APP_PLATFORM 缺失行为一致）。
function getCurrentPlatform(): string {
  return (import.meta.env.VITE_APP_PLATFORM as string | undefined) ?? 'general';
}

type BulkAction = 'delete' | 'disable' | 'enable';

// 批量操作成功提示文案，避免嵌套三元
const BULK_SUCCESS_TEXT: Record<BulkAction, string> = {
  delete: '批量删除成功',
  disable: '批量禁用成功',
  enable: '批量启用成功',
};

// ---------- 共享状态 ----------
const selectedTypeId = ref<null | number>(null);
const selectedType = ref<DictType | null>(null);
const typeOptions = ref<Array<{ label: string; value: number }>>([]);

// 右表当前 typeCode：由左表点击行 / 关闭按钮写入，ajax.query 直接读这个 ref。
// typeCode 不再走表单：搜索栏也不显示该字段，逻辑完全在代码里完成。
const entryTypeCode = ref<string | undefined>(undefined);

// 多选状态（左右两表各自独立）
const typeSelectedIds = ref<Set<number>>(new Set());
const entrySelectedIds = ref<Set<number>>(new Set());
const bulkLoading = ref({ type: false, data: false });

// ============================================================
// 抽屉（vben drawer + connectedComponent）
// ============================================================
const [TypeFormDrawer, typeFormDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [DataFormDrawer, dataFormDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

// ============================================================
// 字典查表由 useDictLookups hook 提供；所有 fallback label / tagType / 平台
// 命中策略都在 hook 内闭环,本页不再持有 isEnabledKey / 散落三元等。
// platformLabels 用 SEARCH_PLATFORM_OPTIONS 兜底：dict 还没拉回来时,
// 平台列 valueEnum 仍展示「通用 / React Admin / Vue Admin」三项友好文案。
// ============================================================
const platformLabels: Record<string, string> = {};
for (const { value, label } of SEARCH_PLATFORM_OPTIONS) {
  platformLabels[value] = label;
}
const dictLookups = useDictLookups({
  typeCodes: ['sys_switch_status', 'sys_platform'],
  includeGeneral: true,
  platformLabels,
});

// ============================================================
// 左表：字典类型
// ============================================================
const typeColumns: ReturnType<typeof useTypeColumns> = useTypeColumns();

const typeGridEvents: VxeGridListeners<DictType> = {
  currentRowChange: ({ row }) => {
    if (!row) return;
    // 选中左表行后，把右表的 typeCode（由 entryTypeCode 持有）同步为该类型编码。
    entryTypeCode.value = row.code;
    selectedTypeId.value = row.id;
    selectedType.value = row;
    entryGridApi.reload();
  },
  checkboxChange: ({ row, checked }) => {
    if (!row) return;
    const next = new Set(typeSelectedIds.value);
    if (checked) next.add(row.id);
    else next.delete(row.id);
    typeSelectedIds.value = next;
  },
  checkboxAll: ({ checked }) => {
    const records = (typeGridApi.grid?.getCheckboxRecords?.() ??
      []) as DictType[];
    typeSelectedIds.value = checked
      ? new Set(records.map((r) => r.id))
      : new Set();
  },
};

const [TypeGrid, typeGridApi] = useVbenVxeGrid<DictType>({
  formOptions: {
    collapsed: false,
    schema: useTypeSearchSchema(),
    showCollapseButton: false,
  },
  gridEvents: typeGridEvents,
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: typeColumns,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          return await fetchDictTypeListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            code: formValues.code || undefined,
            name: formValues.name || undefined,
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

// ============================================================
// 右表：字典项
// ============================================================
const dataColumns: ReturnType<typeof useDataColumns> = useDataColumns();

const entryGridEvents: VxeGridListeners<DictData> = {
  checkboxChange: ({ row, checked }) => {
    if (!row) return;
    const next = new Set(entrySelectedIds.value);
    if (checked) next.add(row.id);
    else next.delete(row.id);
    entrySelectedIds.value = next;
  },
  checkboxAll: ({ checked }) => {
    const records = (entryGridApi.grid?.getCheckboxRecords?.() ??
      []) as DictData[];
    entrySelectedIds.value = checked
      ? new Set(records.map((r) => r.id))
      : new Set();
  },
};

const [EntryGrid, entryGridApi] = useVbenVxeGrid<DictData>({
  formOptions: {
    collapsed: false,
    schema: useDataSearchSchema(),
    showCollapseButton: false,
    // 接管 form 重置：把右侧插槽里那个独立维护的 `includeGeneralChecked` ref
    // 拉回 true 并写回 form 字段。否则重置后 checkbox 视觉上仍然为空（ref 没
    // 跟着 form 一起 reset），跟用户预期「通用默认勾上」不一致。
    handleReset: async () => {
      includeGeneralChecked.value = true;
      await entryGridApi.formApi?.setFieldValue?.('includeGeneral', true);
      await entryGridApi.formApi?.resetForm?.();
      // 把 reset 后的 formValues 同步给 ajax.query：vxe-grid 的 ajax 通过
      // extendProxyOptions 桥接到 formApi.getLatestSubmissionValues()，而不是
      // 每次现读 getValues()。如果不写 latest snapshot，ajax.query 拿到的还是
      // reset 之前的「不含通用」的旧值，结果就是「reset 后 checkbox 勾上了但
      // 列表没刷新」。
      const formValues = (await entryGridApi.formApi?.getValues?.()) ?? {};
      entryGridApi.formApi?.setLatestSubmissionValues?.(formValues);
      await entryGridApi.reload();
    },
  },
  gridEvents: entryGridEvents,
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: dataColumns,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          // typeCode 不在搜索表单里，由 entryTypeCode 提供（点击行 / 关闭按钮）。
          // platform：表单未选时落到 DEFAULT_PLATFORM（=VITE_APP_PLATFORM）。
          // includeGeneral：复选框布尔值；后端 platform=general 时忽略。
          return await fetchDictDataListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            typeCode: entryTypeCode.value,
            value: formValues.value || undefined,
            platform: formValues.platform || DEFAULT_PLATFORM,
            includeGeneral: formValues.includeGeneral === true,
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

// ============================================================
// 行为
// ============================================================

function openTypeCreate() {
  typeFormDrawerApi.setData({}).open();
}

function openEntryCreate() {
  dataFormDrawerApi.setData({}).open();
}

/**
 * 清除右表的「点击行筛选」状态：
 * - 清空 typeCode ref
 * - 清空 selectedType / selectedTypeId（Tag 消失，标题变回「字典数据」）
 * - 重新拉一次右表数据，typeCode 变 undefined 后右表回到「全部」
 */
function clearEntrySelection() {
  entryTypeCode.value = undefined;
  selectedTypeId.value = null;
  selectedType.value = null;
  entryGridApi.reload();
}

/* ============================================================
 * 批量操作
 * - 选择变更后自动清空「待操作 ID 列表」，避免过期的勾选残留
 * - 操作完成后调用 reload() 重拉数据，并 clearCheckboxRow() 清掉勾选
 * ============================================================ */
async function bulkTypeAction(action: 'delete' | 'disable' | 'enable') {
  const ids = [...typeSelectedIds.value];
  if (ids.length === 0) {
    message.warning('请先勾选要操作的字典类型');
    return;
  }
  bulkLoading.value.type = true;
  try {
    await batchDictTypeApi({ action, ids });
    message.success(BULK_SUCCESS_TEXT[action]);
    typeSelectedIds.value = new Set();
    typeGridApi.grid?.clearCheckboxRow?.();
    typeGridApi.reload();
  } catch (error) {
    message.error(`批量操作失败：${(error as Error).message ?? '未知错误'}`);
  } finally {
    bulkLoading.value.type = false;
  }
}

async function bulkEntryAction(action: 'delete' | 'disable' | 'enable') {
  const ids = [...entrySelectedIds.value];
  if (ids.length === 0) {
    message.warning('请先勾选要操作的字典项');
    return;
  }
  bulkLoading.value.data = true;
  try {
    await batchDictDataApi({ action, ids });
    message.success(BULK_SUCCESS_TEXT[action]);
    entrySelectedIds.value = new Set();
    entryGridApi.grid?.clearCheckboxRow?.();
    entryGridApi.reload();
  } catch (error) {
    message.error(`批量操作失败：${(error as Error).message ?? '未知错误'}`);
  } finally {
    bulkLoading.value.data = false;
  }
}

// 预加载字典类型下拉选项
onMounted(async () => {
  try {
    const list = await fetchAllDictTypesApi({ status: 1 });
    typeOptions.value = list.map((t) => ({
      label: `${t.name}（${t.code}）`,
      value: t.id,
    }));
  } catch {
    // 静默失败：drawer 打开时再尝试
  }
});

// ------------------------------------------------------------
// #form-platform 插槽：Select + 包含通用 Checkbox 同行渲染
// - platform 字段由 schema 接管（v-model 等价物 = value / onUpdate:value）
// - includeGeneral 在 schema 里以「不渲染的隐藏字段」形式注册（useDataSearchSchema
//   里的 dependencies.if: () => false），所以 defaultValue: true 在 form init 时就
//   落进 form.values；不需要再在 onMounted 里 setFieldValue 兜底。
// - 插槽里的 Checkbox 仍通过 formApi.setFieldValue 写回，与 schema 里那个隐藏
//   field 共享同一个 form 字段；提交时 formValues.includeGeneral === true。
// - 当 platform === 'general' 时 Checkbox 强制 disabled（与 React 版一致）。
// ------------------------------------------------------------
const includeGeneralChecked = ref(true);

function setIncludeGeneralField(value: boolean) {
  void entryGridApi.formApi?.setFieldValue?.('includeGeneral', value);
}
</script>

<template>
  <Page auto-content-height>
    <Row :gutter="16">
      <Col :span="24" :md="12">
        <Card>
          <TypeGrid table-title="字典类型">
            <template #toolbar-tools>
              <Space :size="8" align="center">
                <span
                  v-if="typeSelectedIds.size > 0"
                  class="text-xs"
                  style="color: var(--ant-color-text-secondary)"
                >
                  已选
                  <strong style="color: var(--ant-color-text)">{{
                    typeSelectedIds.size
                  }}</strong>
                  条
                </span>
                <Button
                  v-if="typeSelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.type"
                  @click="bulkTypeAction('enable')"
                >
                  批量启用
                </Button>
                <Button
                  v-if="typeSelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.type"
                  @click="bulkTypeAction('disable')"
                >
                  批量禁用
                </Button>
                <Popconfirm
                  v-if="typeSelectedIds.size > 0"
                  title="确定要删除选中的字典类型？"
                  :description="`若仍有字典项将无法删除。当前共 ${typeSelectedIds.size} 项。`"
                  @confirm="bulkTypeAction('delete')"
                >
                  <Button size="small" danger ghost :loading="bulkLoading.type">
                    批量删除
                  </Button>
                </Popconfirm>
                <Button
                  v-if="typeSelectedIds.size === 0"
                  type="primary"
                  @click="openTypeCreate"
                >
                  新建类型
                </Button>
              </Space>
            </template>
            <template #action="{ row }">
              <Space>
                <Button
                  type="link"
                  size="small"
                  @click="typeFormDrawerApi.setData(row).open()"
                >
                  编辑
                </Button>
                <Popconfirm
                  title="确定要删除该字典类型？"
                  description="若仍有字典项将无法删除。"
                >
                  <Button type="link" size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            </template>

            <!--
              字典状态列：cellRender.pros 在不同 vxe-table 版本下函数求值
              行为不一致，这里改用模板插槽直接渲染 Tag。
              颜色与文本由 dictLookups 实时查表。
            -->
            <template #dict_status="{ row }">
              <Tag
                :color="dictLookups.lookupSwitchTagType(row.isEnabled as 0 | 1)"
                size="small"
              >
                {{ dictLookups.lookupSwitchLabel(row.isEnabled as 0 | 1) }}
              </Tag>
            </template>
          </TypeGrid>
        </Card>
      </Col>

      <Col :span="24" :md="12">
        <Card>
          <EntryGrid>
            <template #table-title>
              <Space align="center" :size="8">
                <span style="font-size: 16px">字典数据</span>
                <Tag
                  v-if="selectedType"
                  color="blue"
                  style="font-size: 14px"
                  closable
                  @close="clearEntrySelection"
                >
                  {{ selectedType.name }}（{{ selectedType.code }}）
                </Tag>
              </Space>
            </template>
            <template #dict_platform="{ row }">
              <Tag
                :color="
                  dictLookups.lookupPlatformTagType(row.platform) ?? 'default'
                "
                size="small"
              >
                {{ dictLookups.lookupPlatformLabel(row.platform) }}
              </Tag>
            </template>
            <template #dict_default="{ row }">
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
            <template #dict_status="{ row }">
              <Tag
                :color="dictLookups.lookupSwitchTagType(row.isEnabled as 0 | 1)"
                size="small"
              >
                {{ dictLookups.lookupSwitchLabel(row.isEnabled as 0 | 1) }}
              </Tag>
            </template>
            <template
              #form-platform="{
                value: platformValue,
                'onUpdate:value': onUpdatePlatform,
              }"
            >
              <div
                style="
                  display: flex;
                  gap: 12px;
                  align-items: center;
                  width: 100%;
                "
              >
                <Select
                  :value="platformValue ?? getCurrentPlatform()"
                  :options="SEARCH_PLATFORM_OPTIONS"
                  placeholder="请选择归属平台"
                  allow-clear
                  style="flex: 1; min-width: 0"
                  @update:value="
                    (v) => {
                      if (v == null) {
                        onUpdatePlatform?.(undefined);
                      } else if (
                        typeof v === 'string' ||
                        typeof v === 'number'
                      ) {
                        onUpdatePlatform?.(String(v));
                      }
                    }
                  "
                />
                <Checkbox
                  :checked="includeGeneralChecked"
                  :disabled="platformValue === 'general'"
                  style="white-space: nowrap"
                  @update:checked="
                    (v: boolean) => {
                      includeGeneralChecked = v;
                      setIncludeGeneralField(v);
                    }
                  "
                >
                  通用
                </Checkbox>
              </div>
            </template>
            <template #toolbar-tools>
              <Space :size="8" align="center">
                <span
                  v-if="entrySelectedIds.size > 0"
                  class="text-xs"
                  style="color: var(--ant-color-text-secondary)"
                >
                  已选
                  <strong style="color: var(--ant-color-text)">{{
                    entrySelectedIds.size
                  }}</strong>
                  条
                </span>
                <Button
                  v-if="entrySelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.data"
                  @click="bulkEntryAction('enable')"
                >
                  批量启用
                </Button>
                <Button
                  v-if="entrySelectedIds.size > 0"
                  size="small"
                  :loading="bulkLoading.data"
                  @click="bulkEntryAction('disable')"
                >
                  批量禁用
                </Button>
                <Popconfirm
                  v-if="entrySelectedIds.size > 0"
                  title="确定要删除选中的字典项？"
                  @confirm="bulkEntryAction('delete')"
                >
                  <Button size="small" danger ghost :loading="bulkLoading.data">
                    批量删除
                  </Button>
                </Popconfirm>
                <Button
                  v-if="entrySelectedIds.size === 0"
                  type="primary"
                  @click="openEntryCreate"
                >
                  新建条目
                </Button>
              </Space>
            </template>
            <template #action="{ row }">
              <Space>
                <Button
                  type="link"
                  size="small"
                  @click="dataFormDrawerApi.setData(row).open()"
                >
                  编辑
                </Button>
                <Button
                  type="link"
                  size="small"
                  danger
                  @click="
                    deleteDictDataApi(row.id)
                      .then(() => entryGridApi.reload())
                      .then(() => message.success('删除成功'))
                      .catch((err: Error) =>
                        message.error(`删除失败：${err.message}`),
                      )
                  "
                >
                  删除
                </Button>
              </Space>
            </template>
          </EntryGrid>
        </Card>
      </Col>
    </Row>

    <TypeFormDrawer kind="type" @success="typeGridApi.reload()" />
    <DataFormDrawer
      kind="data"
      :default-type-id="selectedTypeId ?? undefined"
      :type-options="typeOptions"
      @success="entryGridApi.reload()"
    />
  </Page>
</template>
