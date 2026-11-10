<script lang="ts" setup>
import type {
  I18nLocale,
  I18nTranslation,
  I18nTranslationBatchUpsertByKeyItem,
} from '#/api/system/i18n';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Alert,
  Button,
  Input,
  message,
  Space,
  Switch,
  Table,
  Tag,
  theme,
  Tooltip,
  TypographyParagraph,
} from 'antdv-next';

import {
  batchUpsertI18nTranslationByKeyApi,
  fetchAllI18nLocalesApi,
  fetchI18nTranslationByKeyApi,
  fetchI18nTranslationByLocaleCodeApi,
} from '#/api/system/i18n';

interface Props {
  /** 是否打开（由父组件通过 v-model:open 控制） */
  open?: boolean;
  /** 编辑入口：传入原 row 时进入编辑模式；新建时传 null */
  sourceRow?: I18nTranslation | null;
  /** 默认语言编码（来自双表选中态或 i18n default）；用于拉取 keys 与标星 */
  defaultLocaleCode?: string;
  /** 新建场景预填的 translation_key */
  initialKey?: string;
}

const props = withDefaults(defineProps<Props>(), {
  open: false,
  sourceRow: null,
  defaultLocaleCode: 'zh-CN',
  initialKey: '',
});

const emits = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'success'): void;
}>();

const { token } = theme.useToken();

interface RowState {
  localeId: number;
  localeCode?: string;
  localeName: string;
  isDefault: boolean;
  value: string;
  remark: string;
  enabled: boolean;
  deleted: boolean;
  existingId?: number;
}

const KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9._-]{0,254}$/;

const translationKey = ref('');
const rows = reactive<RowState[]>([]);
const globalEnabled = ref(true);
const submitting = ref(false);
const errorMsg = ref('');

const existingKeys = ref<Set<string>>(new Set());

const isEdit = computed(() => !!props.sourceRow);
const editingKey = computed(() =>
  isEdit.value ? (props.sourceRow?.translationKey ?? '') : '',
);

// 翻译键始终可编辑（包括编辑模式）：修改 key 会通过后端 batch-upsert
// 的 rename 阶段同步影响所有语言版本的同一 key 行。
// 「启用 X 种」基于可见行 (排除被删除), 与总开关 + 各行联动一致。
const visibleRows = computed(() => rows.filter((r) => !r.deleted));
const enabledCount = computed(
  () => visibleRows.value.filter((r) => r.enabled).length,
);
const totalLocales = computed(() => visibleRows.value.length);
const keyDuplicate = computed(
  () =>
    !isEdit.value &&
    translationKey.value.length > 0 &&
    existingKeys.value.has(translationKey.value),
);

const columns = [
  { key: 'locale', title: '语言', width: 150 },
  { key: 'value', title: '翻译值', minWidth: 160 },
  { key: 'remark', title: '备注', width: 140 },
  { key: 'enabled', title: '启用', width: 72, align: 'center' as const },
  { key: 'action', title: '操作', width: 52, align: 'center' as const },
];

const [InnerDrawer, drawerApi] = useVbenDrawer({
  // 关闭逻辑：直接关闭抽屉
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    await handleOk();
  },
  async onOpenChange(isOpen) {
    errorMsg.value = '';
    if (!isOpen) {
      // 关闭时同步 v-model:open
      if (props.open) emits('update:open', false);
      return;
    }
    submitting.value = false;

    // 拉取全语种
    let locales: I18nLocale[];
    try {
      locales = await fetchAllI18nLocalesApi({ status: 1 });
    } catch {
      message.error('加载语言列表失败');
      return;
    }

    // 拉取 values（编辑模式） / keys（新建模式）
    let values: Array<{
      id: number;
      isEnabled: 0 | 1;
      localeCode?: string;
      localeId: number;
      remark: string;
      value: string;
    }> = [];
    if (isEdit.value) {
      try {
        const res = await fetchI18nTranslationByKeyApi(editingKey.value);
        values = res.values;
      } catch {
        message.error('加载翻译详情失败');
        return;
      }
    } else {
      try {
        const list = await fetchI18nTranslationByLocaleCodeApi(
          props.defaultLocaleCode,
        );
        existingKeys.value = new Set(list.map((r) => r.translationKey));
      } catch {
        // 静默失败：不阻塞
      }
    }

    // 构造 rows
    rows.splice(
      0,
      rows.length,
      ...locales.map((l) => {
        const found = values.find((v) => v.localeId === l.id);
        return {
          localeId: l.id,
          localeCode: l.code,
          localeName: l.name,
          isDefault: l.isDefault === 1,
          value: found?.value ?? '',
          remark: found?.remark ?? '',
          enabled: found?.isEnabled === 1,
          deleted: false,
          existingId: found?.id,
        };
      }),
    );

    translationKey.value =
      props.sourceRow?.translationKey ?? props.initialKey ?? '';
    globalEnabled.value = rows.some((r) => r.enabled && !r.deleted);
  },
});

// 同步 props.open → drawerApi.open/close（支持父组件 v-model:open）
watch(
  () => props.open,
  (val) => {
    if (val) drawerApi.open();
    else drawerApi.close();
  },
  { immediate: false },
);

const drawerTitle = computed(() => (isEdit.value ? '编辑翻译' : '新建翻译'));

function handleGlobalEnabledChange(next: boolean) {
  globalEnabled.value = next;
  for (const r of visibleRows.value) {
    r.enabled = next;
  }
}

function handleRowEnabledChange(row: RowState, next: boolean) {
  row.enabled = next;
  // 顶部总开关联动: 所有 visible 行 enabled 一致 → 同步;
  // 不一致 → 维持原值 (用户已感知「部分开关」)。
  const vis = visibleRows.value;
  const allOn = vis.every((r) => r.enabled);
  const allOff = vis.every((r) => !r.enabled);
  if (allOn) globalEnabled.value = true;
  else if (allOff) globalEnabled.value = false;
  // 部分开关混合态: 维持原值 (允许用户继续操作)
}

function handleRowDelete(row: RowState) {
  row.deleted = true;
  const vis = visibleRows.value;
  const allOn = vis.every((r) => r.enabled);
  const allOff = vis.every((r) => !r.enabled);
  if (vis.length === 0) {
    // 全部删完: 顶部开关失去意义, 保持原值
  } else if (allOn) {
    globalEnabled.value = true;
  } else if (allOff) {
    globalEnabled.value = false;
  }
}

const handleOk = async () => {
  const finalKey = translationKey.value.trim();
  if (!finalKey) {
    errorMsg.value = '请输入翻译键';
    return;
  }
  if (!KEY_PATTERN.test(finalKey)) {
    errorMsg.value = '翻译键需字母开头，仅含字母数字 . _ -';
    return;
  }
  // 默认语言必填
  const defaultRow = rows.find((r) => r.isDefault && !r.deleted);
  if (defaultRow && !defaultRow.value.trim()) {
    errorMsg.value = '默认语言（★）必须填写';
    return;
  }

  // 收集删除
  const deletedIds = rows
    .filter((r) => r.deleted && r.existingId)
    .map((r) => r.existingId as number);

  // 收集 upsert
  const items: I18nTranslationBatchUpsertByKeyItem[] = rows
    .filter((r) => !r.deleted && r.value.trim() !== '')
    .map((r) => ({
      localeId: r.localeId,
      value: r.value.trim(),
      remark: r.remark.trim() || undefined,
      isEnabled: r.enabled ? 1 : 0,
    }));

  if (items.length === 0 && deletedIds.length === 0 && isEdit.value) {
    message.warning('没有需要保存的修改');
    return;
  }

  // newKey：仅当 key 改了时
  const newTranslationKey =
    isEdit.value && finalKey !== props.sourceRow?.translationKey
      ? finalKey
      : undefined;

  submitting.value = true;
  drawerApi.lock();
  try {
    const res = await batchUpsertI18nTranslationByKeyApi({
      translationKey: props.sourceRow?.translationKey ?? finalKey,
      newTranslationKey,
      items,
      deletedIds,
    });
    if (!res.ok) {
      const msgs = (res.errors ?? []).map((e) => `[${e.code}] ${e.message}`);
      errorMsg.value = msgs.join('；') || '未知错误';
      message.error(`保存失败：${errorMsg.value}`);
      return;
    }
    const a = res.affected ?? {
      renamed: 0,
      created: 0,
      updated: 0,
      deleted: 0,
    };
    message.success(
      `已保存（新增 ${a.created} / 更新 ${a.updated} / 删除 ${a.deleted}${a.renamed ? ' / 改名' : ''}）`,
    );
    errorMsg.value = '';
    emits('success');
    emits('update:open', false);
  } catch (error) {
    errorMsg.value = (error as Error).message ?? '未知错误';
    message.error(`保存失败：${errorMsg.value}`);
  } finally {
    submitting.value = false;
    drawerApi.unlock();
  }
};

defineExpose({ open: () => drawerApi.open(), close: () => drawerApi.close() });
</script>

<template>
  <InnerDrawer class="w-full max-w-200" :title="drawerTitle">
    <div class="i18n-translation-key-form flex flex-col gap-4">
      <Alert v-if="errorMsg" type="error" show-icon :message="errorMsg" />

      <div>
        <div class="mb-2 text-sm">
          <span :style="{ color: token.colorError }">*</span> 翻译键
          <Tag v-if="!isEdit && keyDuplicate" color="warning">
            该 key 在默认语言已存在
          </Tag>
        </div>
        <Input
          v-model:value="translationKey"
          placeholder="例如 menu.user.create"
          :maxlength="255"
        />
        <TypographyParagraph type="secondary" class="mt-1 mb-0 text-xs">
          建议使用点分命名空间（如 menu.user.create），修改 key 会
          同步影响所有语言版本的同一 key 行
        </TypographyParagraph>
      </div>

      <div>
        <div class="mb-2 text-sm">状态</div>
        <Space :size="16" align="center" wrap>
          <Switch
            v-model:checked="globalEnabled"
            checked-children="启用"
            un-checked-children="禁用"
            @change="handleGlobalEnabledChange"
          />
          <Tag
            :color="
              enabledCount === totalLocales && totalLocales > 0
                ? 'success'
                : 'default'
            "
          >
            {{ enabledCount }} / {{ totalLocales }} 启用
          </Tag>
        </Space>
        <TypographyParagraph type="secondary" class="mt-1 mb-0 text-xs">
          总开关会联动所有可见语言行；混合态保持原值
        </TypographyParagraph>
      </div>

      <div>
        <div class="mb-2 text-sm">各语言值</div>
        <Table
          :columns="columns"
          :data-source="visibleRows"
          :pagination="false"
          row-key="localeId"
          size="small"
          :scroll="{ y: 360 }"
        >
          <template #bodyCell="{ column, record: row }">
            <template v-if="column.key === 'locale'">
              <Space :size="6" align="center">
                <Tag
                  :color="row.isDefault ? 'blue' : 'default'"
                  :style="{
                    margin: 0,
                    fontFamily:
                      'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontSize: '12px',
                  }"
                >
                  {{ row.localeCode }}
                </Tag>
                <span
                  :style="{
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    maxWidth: '60px',
                  }"
                >
                  {{ row.localeName }}
                </span>
                <Tag v-if="row.isDefault" color="blue">★</Tag>
              </Space>
            </template>
            <template v-else-if="column.key === 'value'">
              <Input
                v-model:value="row.value"
                :placeholder="
                  row.isDefault ? '默认语言必填' : '翻译值（留空跳过）'
                "
              />
            </template>
            <template v-else-if="column.key === 'remark'">
              <Input v-model:value="row.remark" placeholder="选填" />
            </template>
            <template v-else-if="column.key === 'enabled'">
              <Switch
                v-model:checked="row.enabled"
                size="small"
                @change="(next: boolean) => handleRowEnabledChange(row, next)"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <Tooltip title="删除该语言行">
                <Button
                  danger
                  type="text"
                  shape="circle"
                  size="small"
                  @click="handleRowDelete(row)"
                >
                  ×
                </Button>
              </Tooltip>
            </template>
          </template>
        </Table>
      </div>

      <div
        :style="{
          padding: '10px 12px',
          marginTop: '16px',
          fontSize: '12px',
          color: token.colorTextSecondary,
          background: token.colorPrimaryBg,
          borderLeft: `3px solid ${token.colorPrimary}`,
          borderRadius: '4px',
        }"
      >
        填了的语言会写入对应行；留空则跳过。默认语言（★）必须。
      </div>
    </div>
  </InnerDrawer>
</template>
