<script lang="ts" setup>
import type { ImportFormat, PreviewRow, StagedFile } from './import-utils';

import type {
  VxeTableGridColumns,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Steps,
  Tag,
  Tooltip,
  TypographyParagraph,
  Upload,
} from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  fetchAllI18nLocalesApi,
  importI18nBatchApi,
  previewI18nImportApi,
} from '#/api/system/i18n';

import {
  buildPreviewRows,
  filterChangedOnly,
  mergeAllFiles,
  normalizePrefix,
  previewStats,
} from './import-utils';

defineOptions({ name: 'I18nImportModal' });

const props = defineProps<{
  open: boolean;
}>();

const emits = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'success'): void;
}>();

const step = ref(0);
const format = ref<ImportFormat>('simple');
const staged = ref<StagedFile[]>([]);
const showChangedOnly = ref(false);
const submitting = ref(false);

const localeOptions = ref<Array<{ label: string; value: string }>>([]);

const previewState = ref<{
  currentRows: Array<{
    isEnabled: 0 | 1;
    localeCode: string;
    translationKey: string;
    value: string;
  }>;
  loading: boolean;
}>({ loading: false, currentRows: [] });

const mergedRows = computed(() => mergeAllFiles(staged.value));
const previewKeysByLocale = computed(() => {
  const m = new Map<string, string[]>();
  for (const r of mergedRows.value) {
    const arr = m.get(r.localeCode) ?? [];
    arr.push(r.key);
    m.set(r.localeCode, arr);
  }
  return [...m.entries()].map(([localeCode, keys]) => ({
    localeCode,
    keys: [...new Set(keys)],
  }));
});

const previewRows = computed(() =>
  buildPreviewRows(mergedRows.value, previewState.value.currentRows),
);
const stats = computed(() => previewStats(previewRows.value));
const visiblePreviewRows = computed(() =>
  showChangedOnly.value
    ? filterChangedOnly(previewRows.value)
    : previewRows.value,
);

// 加载状态同步到 PreviewGrid（vxe-grid 用法）
watch(
  () => previewState.value.loading,
  (val) => {
    previewGridApi.setLoading?.(val);
  },
);

const canGoStep2 = computed(
  () => staged.value.length > 0 && staged.value.some((s) => s.parseOk),
);
const canGoStep3 = computed(() =>
  staged.value
    .filter((s) => s.parseOk)
    .every((s) => s.localeCode && s.localeCode.length > 0),
);

async function loadLocaleOptions() {
  try {
    const locales = await fetchAllI18nLocalesApi({ status: 1 });
    localeOptions.value = locales.map((l) => ({
      label: `${l.name}（${l.code}）`,
      value: l.code,
    }));
  } catch {
    // 静默
  }
}

function reset() {
  step.value = 0;
  format.value = 'simple';
  staged.value = [];
  showChangedOnly.value = false;
  submitting.value = false;
  previewState.value = { loading: false, currentRows: [] };
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      reset();
      void loadLocaleOptions();
    }
  },
);

function onFormatChange(next: ImportFormat) {
  format.value = next;
  // 切换格式时清空，避免 mixed format
  staged.value = [];
}

function downloadTemplate() {
  const template: any =
    format.value === 'raw'
      ? {
          '@type': 'raw',
          locale: {
            code: 'zh-CN',
            name: '简体中文',
            isDefault: 1,
            sort: 0,
            isEnabled: 1,
          },
          translations: [
            {
              translationKey: 'page.title',
              value: '页面标题',
              isEnabled: 1,
            },
            {
              translationKey: 'page.desc',
              value: '页面描述',
              isEnabled: 1,
            },
          ],
        }
      : {
          '@type': 'simple',
          page: {
            title: '页面标题',
            desc: '页面描述',
          },
          common: {
            save: '保存',
            cancel: '取消',
          },
        };

  const blob = new Blob([JSON.stringify(template, null, 2)], {
    type: 'application/json',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `i18n-${format.value}-template.json`;
  a.click();
  URL.revokeObjectURL(url);
}

// antd Upload 的 beforeUpload: () => false 阻止自动上传，自己处理 file
async function handleFile(file: File) {
  try {
    const text = await file.text();
    let payload: any;
    try {
      payload = JSON.parse(text);
    } catch {
      staged.value.push({
        name: file.name,
        file,
        format: 'simple',
        payload: null,
        parseOk: false,
        errorMessage: 'JSON 解析失败',
        localeCode: '',
        prefix: '',
      });
      return false;
    }
    const detectedFormat =
      payload && typeof payload === 'object' && payload['@type'] === 'raw'
        ? 'raw'
        : 'simple';
    if (detectedFormat !== format.value) {
      message.warning(
        `文件 ${file.name} 检测为 ${detectedFormat} 格式，与当前选定的 ${format.value} 不一致`,
      );
    }
    const payloadLocale = detectedFormat === 'raw' ? payload.locale : undefined;
    const localeCode =
      detectedFormat === 'raw' && payloadLocale?.code ? payloadLocale.code : '';
    staged.value.push({
      name: file.name,
      file,
      format: detectedFormat,
      payload,
      parseOk: true,
      localeCode,
      prefix: '',
      payloadLocale,
    });
  } catch (error: any) {
    message.error(`读取文件失败：${error.message ?? '未知错误'}`);
  }
  return false;
}

function removeStaged(idx: number) {
  staged.value.splice(idx, 1);
}

function updateStaged(
  idx: number,
  patch: Partial<
    Pick<StagedFile, 'format' | 'localeCode' | 'payloadLocale' | 'prefix'>
  >,
) {
  const current = staged.value[idx];
  if (!current) return;
  const next = [...staged.value];
  next[idx] = { ...current, ...patch } as StagedFile;
  staged.value = next;
}

function detectFormat(s: StagedFile): ImportFormat {
  if (!s.parseOk) return 'simple';
  const obj = s.payload as null | Record<string, unknown>;
  return obj && obj['@type'] === 'raw' ? 'raw' : 'simple';
}

// Step 2 文件配置列（vxe-table 列定义 + 插槽名称）
const fileConfigColumns: VxeTableGridColumns<StagedFile> = [
  {
    field: 'name',
    title: '文件名',
    minWidth: 200,
    slots: { default: 'cell-name' },
  },
  {
    field: 'format',
    title: '格式',
    width: 80,
    slots: { default: 'cell-format' },
  },
  {
    field: 'localeCode',
    title: '语言代码',
    width: 240,
    slots: { default: 'cell-locale' },
  },
  {
    field: 'prefix',
    title: '前缀',
    width: 180,
    slots: { default: 'cell-prefix' },
  },
  {
    field: 'parseOk',
    title: '状态',
    width: 90,
    slots: { default: 'cell-parse' },
  },
  {
    field: 'action',
    title: '操作',
    width: 80,
    slots: { default: 'cell-action' },
  },
];

// Step 3 预览列
// ponytail: 用 minWidth 而非 width，让 vxe-grid 在 autoResize 下按 modal 内宽自动列分配，避免右侧空白。
const previewColumns: VxeTableGridColumns<PreviewRow> = [
  {
    field: 'op',
    title: '变更类型',
    width: 80,
    slots: { default: 'cell-op' },
  },
  { field: 'localeCode', title: '语言代码', minWidth: 100 },
  {
    field: 'key',
    title: '翻译键',
    align: 'left',
    width: 100,
    slots: { default: 'cell-key' },
  },
  {
    field: 'oldValue',
    title: '旧值 / 新值',
    minWidth: 320,
    slots: { default: 'cell-diff' },
  },
  {
    field: 'remark',
    title: '备注',
    minWidth: 120,
    slots: { default: 'cell-remark' },
  },
  { field: 'sourceFile', title: '来源文件', minWidth: 140 },
];

// Step 2 Grid（仅展示 staged，columns 用 slots 渲染交互）
// ponytail: tableData 字段类型是 any[]，但 use-vxe-grid.vue 内部实际接受了 Ref<any[]>
// （mergedOptions.data = tableData.value,见 use-vxe-grid.vue:256），这里 cast 一次让 TS 闭嘴。
const [Step2Grid] = useVbenVxeGrid<StagedFile>({
  tableData: staged as unknown as StagedFile[],
  gridOptions: {
    columns: fileConfigColumns,
    rowConfig: { keyField: 'name' },
    size: 'small',
    stripe: true,
    showOverflow: true,
    // ponytail: 全局 vxe-table.minHeight=180(见 web-antdv-next/src/adapter/vxe-table.ts:75) 会让 body-wrapper
    // 至少保留 180px，少数据(如只 1 行)时表现为表格下方大段空白。这里强制 1px 走 toNumber 分支,
    // 实际高度由行数撑开。autoResize 同 PreviewGrid 开启,顺便对齐 modal 内宽自适应。
    autoResize: true,
    minHeight: 1,
    toolbarConfig: { enabled: false },
    pagerConfig: { enabled: false },
  } as VxeTableGridOptions<StagedFile>,
});

// visiblePreviewRows 是 previewRows(已含 _rowKey) 的子集，_rowKey 在 merged 全局唯一，
// 直接复用即可，无需再按 visible 索引重拼。
const previewRowsWithKey = computed(() => visiblePreviewRows.value);

// Step 3 Preview Grid
// ponytail: 同上,tableData 类型容不下 ComputedRef,cast 一次
const [PreviewGrid, previewGridApi] = useVbenVxeGrid<PreviewRow>({
  tableData: previewRowsWithKey as unknown as PreviewRow[],
  gridOptions: {
    columns: previewColumns,
    rowConfig: { keyField: '_rowKey' },
    size: 'small',
    stripe: true,
    showOverflow: true,
    autoResize: true,
    toolbarConfig: { enabled: false },
    pagerConfig: {
      enabled: true,
      pageSize: 20,
      pageSizes: [20, 50, 100, 200],
      layouts: [
        'Total',
        'Sizes',
        'Home',
        'PrevJump',
        'PrevPage',
        'Number',
        'NextPage',
        'NextJump',
        'End',
      ],
    },
  } as VxeTableGridOptions<PreviewRow>,
});

async function goToStep3() {
  step.value = 2;
  previewState.value = { loading: true, currentRows: [] };
  previewGridApi.setLoading?.(true);
  try {
    const res = await previewI18nImportApi({
      items: previewKeysByLocale.value,
    });
    previewState.value = {
      loading: false,
      currentRows: (res.currentRows ?? []).map((r) => ({
        localeCode: r.localeCode ?? '',
        translationKey: r.translationKey,
        value: r.value,
        isEnabled: r.isEnabled,
      })),
    };
    previewGridApi.setLoading?.(false);
  } catch (error: any) {
    previewState.value = { loading: false, currentRows: [] };
    previewGridApi.setLoading?.(false);
    message.error(`预览失败：${error.message ?? '未知错误'}`);
  }
}

async function submitImport() {
  submitting.value = true;
  try {
    const items = staged.value
      .filter((s) => s.parseOk)
      .map((s) => ({
        name: s.name,
        prefix: s.prefix || undefined,
        localeCode: s.localeCode,
        format: detectFormat(s),
        payload: s.payload,
      }));
    const res = await importI18nBatchApi({ items });
    const perFile = res.affected.perFile ?? [];
    const failed = perFile.filter((p) => !p.ok);
    if (failed.length > 0) {
      Modal.warning({
        title: '导入完成（部分失败）',
        content: `成功 ${perFile.length - failed.length} 个文件，失败 ${failed.length} 个文件`,
      });
    } else {
      message.success(
        `导入成功：新增 ${res.affected.createdTranslations} 条翻译`,
      );
    }
    emits('success');
    emits('update:open', false);
  } catch (error: any) {
    message.error(`导入失败：${error.message ?? '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

function goConfirm() {
  Modal.confirm({
    title: '确认导入',
    content: `即将导入 ${stats.value.total} 条翻译：新增 ${stats.value.create} 条 / 修改 ${stats.value.update} 条 / 重复 ${stats.value.duplicate} 条（后者覆盖）${
      stats.value.unchanged > 0
        ? ` / 与现状一致 ${stats.value.unchanged} 条`
        : ''
    }${
      stats.value.oldDisabled > 0
        ? ` / 现状禁用 ${stats.value.oldDisabled} 条`
        : ''
    }`,
    okText: '确认导入',
    cancelText: '取消',
    onOk: submitImport,
  });
}

function handleClose() {
  emits('update:open', false);
}
</script>

<template>
  <Modal
    title="导入 JSON"
    :open="props.open"
    :width="1200"
    :destroy-on-close="true"
    :footer="null"
    :mask-closable="false"
    @cancel="handleClose"
  >
    <Steps
      :current="step"
      :items="[
        { title: '选择格式与文件' },
        { title: '配置选项' },
        { title: '预览与导入' },
      ]"
      style="margin-bottom: 24px"
    />

    <!-- Step 1 -->
    <div v-if="step === 0">
      <Space direction="vertical" :size="16" style="width: 100%">
        <div>
          <div class="mb-2 text-sm font-medium">导入格式</div>
          <Space :size="8">
            <Button
              :type="format === 'simple' ? 'primary' : 'default'"
              @click="onFormatChange('simple')"
            >
              Simple（嵌套字典）
            </Button>
            <Button
              :type="format === 'raw' ? 'primary' : 'default'"
              @click="onFormatChange('raw')"
            >
              Raw（含语言元数据）
            </Button>
          </Space>
          <TypographyParagraph type="secondary" class="mt-1 mb-0 text-xs">
            {{
              format === 'simple'
                ? 'Simple 适合批量导入纯翻译文案；嵌套字典会自动展开为扁平键。'
                : 'Raw 包含语言元数据（isDefault、name 等），可同时新增/更新语言。'
            }}
          </TypographyParagraph>
        </div>

        <Button @click="downloadTemplate">
          <Space :size="4" align="center">
            <IconifyIcon icon="ant-design:download-outlined" />
            <span>下载 {{ format === 'simple' ? 'Simple' : 'Raw' }} 模板</span>
          </Space>
        </Button>

        <div>
          <div class="mb-2 text-sm font-medium">选择文件（可多选）</div>
          <Upload.Dragger
            :file-list="[]"
            :before-upload="handleFile"
            :show-upload-list="false"
            multiple
            accept=".json"
          >
            <p class="text-2xl" style="margin-bottom: 8px">
              <IconifyIcon icon="ant-design:inbox-outlined" />
            </p>
            <p>
              点击或拖拽 {{ format === 'simple' ? 'Simple' : 'Raw' }} JSON
              文件到此区域
            </p>
            <p class="text-xs" style="color: var(--ant-color-text-secondary)">
              仅支持 .json 格式
            </p>
          </Upload.Dragger>
        </div>

        <div
          v-if="staged.length > 0"
          class="text-xs"
          style="color: var(--ant-color-text-secondary)"
        >
          已选 {{ staged.length }} 个文件，其中
          {{ staged.filter((s) => s.parseOk).length }} 个解析成功
        </div>

        <div style="text-align: right">
          <Space>
            <Button @click="handleClose">取消</Button>
            <Button type="primary" :disabled="!canGoStep2" @click="step = 1">
              下一步
            </Button>
          </Space>
        </div>
      </Space>
    </div>

    <!-- Step 2 -->
    <div v-else-if="step === 1">
      <Space direction="vertical" :size="16" style="width: 100%">
        <Alert
          type="info"
          show-icon
          message="为 simple 文件配置语言代码与前缀；raw 文件自动从文件内读取，不可修改。"
        />
        <div style="width: 100%">
          <Step2Grid>
            <template #cell-name="{ row }">
              <span>{{ row.name }}</span>
            </template>

            <template #cell-format="{ row }">
              <Tag :color="detectFormat(row) === 'raw' ? 'blue' : 'green'">
                {{ detectFormat(row) }}
              </Tag>
            </template>

            <template #cell-locale="{ row, rowIndex }">
              <Space
                v-if="detectFormat(row) === 'raw' || !row.parseOk"
                :size="4"
              >
                <Tag color="blue" style="margin: 0">
                  {{ row.payloadLocale?.code || row.localeCode || '—' }}
                </Tag>
                <span
                  class="text-xs"
                  style="color: var(--ant-color-text-secondary)"
                  >来自文件</span>
              </Space>
              <Select
                v-else
                :value="row.localeCode"
                :options="localeOptions"
                placeholder="选择语言"
                show-search
                style="width: 220px"
                @update:value="
                  (v: unknown) =>
                    updateStaged(rowIndex, { localeCode: String(v ?? '') })
                "
              />
            </template>

            <template #cell-prefix="{ row, rowIndex }">
              <span
                v-if="detectFormat(row) === 'raw' || !row.parseOk"
                class="text-xs"
                style="color: var(--ant-color-text-secondary)"
                >—</span>
              <input
                v-else
                type="text"
                :value="row.prefix"
                placeholder="可选，如 app."
                style="
                  width: 100%;
                  padding: 4px 8px;
                  border: 1px solid #d9d9d9;
                  border-radius: 4px;
                "
                @input="
                  (e: Event) =>
                    updateStaged(rowIndex, {
                      prefix: normalizePrefix(
                        (e.target as HTMLInputElement).value,
                      ),
                    })
                "
              />
            </template>

            <template #cell-parse="{ row }">
              <Tag v-if="row.parseOk" color="success">解析成功</Tag>
              <Tag v-else color="error">{{ row.errorMessage ?? '失败' }}</Tag>
            </template>

            <template #cell-action="{ rowIndex }">
              <Button type="text" danger @click="removeStaged(rowIndex)">
                移除
              </Button>
            </template>
          </Step2Grid>
        </div>

        <div style="text-align: right">
          <Space>
            <Button @click="step = 0">上一步</Button>
            <Button type="primary" :disabled="!canGoStep3" @click="goToStep3">
              下一步
            </Button>
          </Space>
        </div>
      </Space>
    </div>

    <!-- Step 3 -->
    <div v-else-if="step === 2">
      <Space direction="vertical" :size="16" style="width: 100%">
        <div
          v-if="previewState.loading"
          style="padding: 24px 0; text-align: center"
        >
          <Progress :percent="50" status="active" :show-info="false" />
          <TypographyParagraph type="secondary">
            正在加载现状快照…
          </TypographyParagraph>
        </div>
        <template v-else>
          <Space :size="8" wrap>
            <Tag color="green">新增 {{ stats.create }}</Tag>
            <Tag color="blue">修改 {{ stats.update }}</Tag>
            <Tag color="red">重复 {{ stats.duplicate }}</Tag>
            <Tag>未变更 {{ stats.unchanged }}</Tag>
            <Tag v-if="stats.oldDisabled > 0" color="warning">
              现状禁用 {{ stats.oldDisabled }}
            </Tag>
            <Popconfirm
              :title="showChangedOnly ? '显示全部行？' : '仅显示有变更的行？'"
              ok-text="切换"
              cancel-text="取消"
              @confirm="showChangedOnly = !showChangedOnly"
            >
              <Button size="small">
                {{ showChangedOnly ? '显示全部行' : '仅显示变更行' }}
              </Button>
            </Popconfirm>
          </Space>

          <div style="width: 100%">
            <PreviewGrid>
              <template #cell-op="{ row }">
                <Tag v-if="row.op === 'create'" color="green">新增</Tag>
                <Tag v-else-if="row.op === 'update'" color="blue">修改</Tag>
                <Tag v-else>未变更</Tag>
              </template>

              <template #cell-key="{ row }">
                <Space :size="4">
                  <Tag v-if="row.duplicate" color="red" style="margin: 0">
                    重复
                  </Tag>
                  <span style="font-family: monospace">{{ row.key }}</span>
                  <Tooltip
                    v-if="row.oldIsEnabled !== undefined"
                    :title="row.oldIsEnabled === 0 ? '现状禁用' : '现状启用'"
                  >
                    <IconifyIcon
                      :icon="
                        row.oldIsEnabled === 0
                          ? 'ant-design:minus-circle-outlined'
                          : 'ant-design:check-circle-outlined'
                      "
                      :style="{
                        color: row.oldIsEnabled === 0 ? '#bfbfbf' : '#52c41a',
                      }"
                    />
                  </Tooltip>
                </Space>
              </template>

              <template #cell-diff="{ row }">
                <div style="line-height: 1.5">
                  <div style="color: #ff4d4f">{{ row.oldValue ?? '—' }}</div>
                  <div style="color: #52c41a">{{ row.newValue }}</div>
                </div>
              </template>

              <template #cell-remark="{ row }">
                <span v-if="row.remark">{{ row.remark }}</span>
                <span v-else style="color: #999">-</span>
              </template>
            </PreviewGrid>
          </div>
        </template>

        <div style="text-align: right">
          <Space>
            <Button @click="step = 1">上一步</Button>
            <Button
              type="primary"
              :loading="submitting"
              :disabled="
                previewState.loading || previewRows.length === 0 || !canGoStep3
              "
              @click="goConfirm"
            >
              下一步
            </Button>
          </Space>
        </div>
      </Space>
    </div>
  </Modal>
</template>
