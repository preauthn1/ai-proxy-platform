<script lang="ts" setup>
import type {
  CreateDictDataRequest,
  CreateDictTypeRequest,
  DictData,
  DictTagType,
  DictType,
  UpdateDictDataRequest,
  UpdateDictTypeRequest,
} from '#/api/system/dict';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Card, Input, InputNumber, Select, Switch, Tag } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createDictDataApi,
  createDictTypeApi,
  fetchAllDictTypesApi,
  updateDictDataApi,
  updateDictTypeApi,
} from '#/api/system/dict';
import {
  ANTD_TAG_COLOR_SET,
  DEFAULT_PLATFORM,
  PLATFORM_OPTIONS,
  PLATFORM_TAG_TYPE_OPTIONS,
  useTypeFormSchema,
} from '#/views/system/dict/data';

interface Props {
  /** 表单类型：'type' = 字典类型 / 'data' = 字典项 */
  kind: 'data' | 'type';
  /** 双表选中态：dict-data 新建时若指定了 typeId，则锁定 typeId 字段不可改 */
  defaultTypeId?: number;
  /** 字典类型下拉选项（kind='data' 时需要） */
  typeOptions?: Array<{ label: string; value: number }>;
}

const props = withDefaults(defineProps<Props>(), {
  defaultTypeId: undefined,
  typeOptions: () => [],
});

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const formData = ref<DictData | DictType | undefined>();
const id = ref<number>();
const isEdit = computed(() => !!formData.value?.id);

// 兜底：父组件 typeOptions 还没就绪时自己拉一次，
// 否则 Select 拿不到匹配项，setFieldValue 设的 typeId 显示不出来
const fallbackTypeOptions = ref<Array<{ label: string; value: number }>>([]);

const effectiveTypeOptions = computed(() =>
  props.typeOptions.length > 0 ? props.typeOptions : fallbackTypeOptions.value,
);

async function ensureTypeOptions() {
  if (effectiveTypeOptions.value.length > 0) return;
  const list = await fetchAllDictTypesApi({ status: 1 });
  fallbackTypeOptions.value = list.map((t) => ({
    label: `${t.name}（${t.code}）`,
    value: t.id,
  }));
}

/* ============================================================
 * 字典类型表单（kind='type'）：走 vben Form + schema（字段少、平铺）
 * ============================================================ */
const [TypeForm, typeFormApi] = useVbenForm({
  schema: useTypeFormSchema() ?? [],
  showDefaultActions: false,
});

/* ============================================================
 * 字典项表单（kind='data'）：手写三段 Card
 *
 * 不走 vben Form，因为 vben Form 的字段是顺序渲染到单个 wrapperClass
 * 网格里，无法拆到不同 Card 容器。手写 Card + reactive 让三段卡片
 * 各自独立分组：「基础信息 / 样式设置 / 其他属性」。
 *
 * reactive 字段名与后端 snake_case 字段解耦：前端字段 camelCase，
 * 提交时映射成后端 snake_case（与现有 vben 提交逻辑一致）。
 * ============================================================ */
interface DataFormModel {
  typeId: null | number;
  value: string;
  label: string;
  sort: number;
  isDefault: boolean;
  platform: string;
  usePresetStyle: boolean;
  /** 预设样式标识；与 antdv-next `<Tag color>` 签名一致 */
  tagType: DictTagType;
  isEnabled: boolean;
  remark: string;
}

function buildEmptyDataModel(): DataFormModel {
  return {
    typeId: null,
    value: '',
    label: '',
    sort: 0,
    isDefault: false,
    platform: DEFAULT_PLATFORM,
    usePresetStyle: true,
    tagType: 'primary',
    isEnabled: true,
    remark: '',
  };
}

const dataModel = reactive<DataFormModel>(buildEmptyDataModel());

// 「原样式在此平台不支持」提示：仅当编辑回显命中 legacy tag_type
// （在 antdv Tag color 集合外但非空 / 非 'default'，如历史写入的 magenta
// 等 antdv Tag 实际接受的颜色仍可展示；此处保留对未来扩展的兜底）。
const legacyPresetHit = ref(false);

function resetDataModel() {
  Object.assign(dataModel, buildEmptyDataModel());
  legacyPresetHit.value = false;
}

function fillDataModelFromRow(d: DictData) {
  // 编辑回显：usePresetStyle 由 row.tagType 决定。
  // - tagType 为空 / 'default' → hasPreset=false（与之前一致）
  // - tagType 在 antdv Tag color 集合内 → hasPreset=true，原值回填到下拉
  // - tagType 不在 color 集合（理论不应发生，预留兼容）→ hasPreset=false
  const tt = d.tagType ?? '';
  const isLegacy = !!tt && tt !== 'default' && !ANTD_TAG_COLOR_SET.has(tt);
  const hasPreset = !!tt && tt !== 'default' && ANTD_TAG_COLOR_SET.has(tt);
  Object.assign(dataModel, {
    typeId: d.typeId,
    value: d.value,
    label: d.label,
    sort: d.sort,
    isDefault: d.isDefault === 1,
    platform: d.platform,
    usePresetStyle: hasPreset,
    tagType: hasPreset ? tt : '',
    isEnabled: d.isEnabled === 1,
    remark: d.remark,
  });
  legacyPresetHit.value = isLegacy;
}

/** 预览颜色：usePresetStyle=false → 'default'（antdv 无色态）；
 *  开启时直接传 dataModel.tagType，antdv Tag 接受全 17 项 color 集合 */
const previewColor = computed<string>(() => {
  if (!dataModel.usePresetStyle) return 'default';
  return dataModel.tagType;
});

const previewShowText = computed(() => dataModel.label || '示例标签');

/** 按当前 platform 计算下拉候选：general → []；自己 → 6 项 */
const presetStyleOptions = computed(() =>
  PLATFORM_TAG_TYPE_OPTIONS(dataModel.platform),
);

/** 「开启预设样式」开关的禁用：通用平台强制锁死 */
const presetStyleDisabled = computed(() => dataModel.platform === 'general');

// 监听 platform 变化：
// - 切到通用 → 强制关闭预设样式 + 清掉 tagType
// - 切回自己 → 恢复默认开启 + 重置 tagType 为 'primary'，
//   避免「切到通用 → tagType 被清 → 切回自己时开关自动恢复 true 但下拉仍为空」的边缘 case。
watch(
  () => dataModel.platform,
  (newPlatform, oldPlatform) => {
    if (newPlatform === 'general' && oldPlatform !== 'general') {
      dataModel.usePresetStyle = false;
      dataModel.tagType = '';
    } else if (newPlatform !== 'general' && oldPlatform === 'general') {
      dataModel.usePresetStyle = true;
      dataModel.tagType = 'primary';
    }
  },
);

/* ============================================================
 * 抽屉：kind='type' 用 vben Form 提交；kind='data' 用手写提交
 * ============================================================ */
const [Drawer, drawerApi] = useVbenDrawer({
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    let req: Promise<unknown>;
    if (props.kind === 'type') {
      const { valid } = await typeFormApi.validate();
      if (!valid) return;
      const values = await typeFormApi.getValues();
      const body = { ...values, isEnabled: values.isEnabled ? 1 : 0 };
      req = id.value
        ? updateDictTypeApi(id.value, body as unknown as UpdateDictTypeRequest)
        : createDictTypeApi(body as unknown as CreateDictTypeRequest);
    } else {
      // 字典项：手写校验（避免引入 NForm 校验上下文）
      if (!dataModel.typeId) {
        // Select 的 typeId 必填，提示
        return;
      }
      if (!dataModel.value.trim()) {
        return;
      }
      if (!dataModel.label.trim()) {
        return;
      }
      // 提交归一化（design §6 / implement §3）：永远下发 antdv 17 项 color 集合内的值。
      // usePresetStyle=false → 'default'（避免后端 PUT 对空串 400）；
      // usePresetStyle=true 时若当前 tagType 不在 antdv 17 项 color 集合（理论上不应发生，
      // 但防御性归一化），强制回退到 'primary'。
      let tagType: DictTagType;
      if (!dataModel.usePresetStyle) {
        tagType = 'default';
      } else if (ANTD_TAG_COLOR_SET.has(dataModel.tagType)) {
        tagType = dataModel.tagType;
      } else {
        tagType = 'primary';
      }
      const body: CreateDictDataRequest = {
        typeId: dataModel.typeId,
        value: dataModel.value,
        label: dataModel.label,
        sort: dataModel.sort,
        isDefault: dataModel.isDefault,
        platform: dataModel.platform,
        tagType,
        isEnabled: dataModel.isEnabled ? 1 : 0,
        remark: dataModel.remark,
      };
      req = id.value
        ? updateDictDataApi(id.value, {
            ...body,
            id: id.value,
            isDefault: body.isDefault ? 1 : 0,
          } as unknown as UpdateDictDataRequest)
        : createDictDataApi(body);
    }

    drawerApi.lock();
    req
      .then(() => {
        emits('success');
        drawerApi.close();
      })
      .catch(() => {
        drawerApi.unlock();
      });
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data =
      props.kind === 'type'
        ? drawerApi.getData<DictType>()
        : drawerApi.getData<DictData>();
    // Vben Drawer 的 getData() 默认返回 {}，用 data?.id 判别编辑/新建
    const editing = !!data?.id;

    if (props.kind === 'data' && !editing) {
      await ensureTypeOptions();
    }

    // 重置表单（避免上次打开的回显残留）
    typeFormApi.resetForm();
    resetDataModel();

    if (editing) {
      formData.value = data;
      id.value = data.id;
      if (props.kind === 'type') {
        const t = data as DictType;
        await nextTick();
        typeFormApi.setValues({ ...t, isEnabled: t.isEnabled === 1 });
      } else {
        fillDataModelFromRow(data as DictData);
      }
    } else {
      formData.value = undefined;
      id.value = undefined;
      if (props.kind === 'data') {
        // 新建字典项：默认沿用左侧选中的类型，未选则空
        dataModel.typeId = props.defaultTypeId ?? null;
      }
    }
  },
});

const drawerTitle = computed(() => {
  if (props.kind === 'type') {
    return isEdit.value ? '编辑字典类型' : '新建字典类型';
  }
  return isEdit.value ? '编辑字典项' : '新建字典项';
});
</script>

<template>
  <Drawer :title="drawerTitle">
    <!--
      字典类型（kind='type'）：维持简单平铺（字段少，无需分组）。
    -->
    <TypeForm v-if="props.kind === 'type'" />

    <!--
      字典项（kind='data'）：三段独立 Card。
      每张卡片内部用 grid-cols-2 网格，配对字段同行、跨列字段独立成行。
    -->
    <div v-else class="dict-data-form flex flex-col gap-4">
      <!-- ============ 基础信息 ============ -->
      <Card :bordered="true" size="small" title="基础信息">
        <div class="grid grid-cols-1 gap-x-4 gap-y-4 md:grid-cols-2">
          <div class="col-span-2">
            <div class="mb-2 text-sm">
              <span class="text-red-500">*</span> 所属类型
            </div>
            <Select
              v-model:value="dataModel.typeId as number"
              :options="effectiveTypeOptions"
              placeholder="请选择类型"
              :filter-option="true"
              show-search
              style="width: 100%"
            />
          </div>
          <div>
            <div class="mb-2 text-sm">
              <span class="text-red-500">*</span> 字典值
            </div>
            <Input
              v-model:value="dataModel.value"
              :maxlength="64"
              placeholder="例如 Y / N / 0 / 1"
            />
          </div>
          <div>
            <div class="mb-2 text-sm">
              <span class="text-red-500">*</span> 字典标签
            </div>
            <Input
              v-model:value="dataModel.label"
              :maxlength="128"
              placeholder="例如 是 / 否 / 启用"
            />
          </div>
        </div>
      </Card>

      <!-- ============ 样式设置 ============ -->
      <Card :bordered="true" size="small" title="样式设置">
        <!--
          样式设置段：「开启预设样式 / 预设样式 / 预览」三列同行。
          关闭预设样式 / 通用平台 时，预览退化为「纯文本 + 提示」占据整行右侧。
        -->
        <div
          class="grid grid-cols-1 items-start gap-x-4 gap-y-4 md:grid-cols-12"
        >
          <div class="md:col-span-3">
            <div class="mb-2 text-sm">开启预设样式</div>
            <Switch
              v-model:checked="dataModel.usePresetStyle"
              :disabled="presetStyleDisabled"
              checked-children="开"
              un-checked-children="关"
            />
          </div>
          <div v-if="dataModel.usePresetStyle" class="md:col-span-5">
            <div class="mb-2 text-sm">
              <span class="text-red-500">*</span> 预设样式
            </div>
            <Select
              v-model:value="dataModel.tagType"
              :options="presetStyleOptions"
              placeholder="请选择预设样式"
              style="width: 100%"
            />
          </div>
          <div
            class="md:col-span-4"
            :class="dataModel.usePresetStyle ? '' : 'md:col-span-9'"
          >
            <div class="mb-2 text-sm text-gray-500">
              <span class="font-medium">预览</span>
              <span v-if="!dataModel.usePresetStyle" class="ml-1 text-gray-400">
                （开启预设样式后展示颜色）
              </span>
            </div>
            <div class="flex items-center">
              <Tag v-if="dataModel.usePresetStyle" :color="previewColor">
                {{ previewShowText }}
              </Tag>
              <span v-else class="text-sm text-gray-500">{{
                previewShowText
              }}</span>
            </div>
            <!--
              legacy tag_type 提示：编辑历史中 antdv Tag 不识别的颜色预设
              （理论上 17 项 color 集合已覆盖所有 antdv 支持的预设）被设为 true，
              提示用户保存后视觉可能丢失。仅在命中 legacy 时显示，不抢视觉位。
            -->
            <div v-if="legacyPresetHit" class="mt-2 text-xs text-orange-500">
              原样式在此平台不支持，已自动关闭
            </div>
          </div>
        </div>
      </Card>

      <!-- ============ 其他属性 ============ -->
      <Card :bordered="true" size="small" title="其他属性">
        <div class="grid grid-cols-1 gap-x-4 gap-y-4 md:grid-cols-2">
          <div>
            <div class="mb-2 text-sm">排序</div>
            <InputNumber
              v-model:value="dataModel.sort"
              :precision="0"
              class="w-full"
              placeholder="升序排序，0 排在前"
            />
          </div>
          <div>
            <div class="mb-2 text-sm">归属平台</div>
            <Select
              v-model:value="dataModel.platform"
              :options="PLATFORM_OPTIONS"
              placeholder="请选择归属平台"
              style="width: 100%"
            />
          </div>
          <div>
            <div class="mb-2 text-sm">是否默认</div>
            <Switch
              v-model:checked="dataModel.isDefault"
              checked-children="是"
              un-checked-children="否"
            />
          </div>
          <div>
            <div class="mb-2 text-sm">启用</div>
            <Switch
              v-model:checked="dataModel.isEnabled"
              checked-children="启用"
              un-checked-children="禁用"
            />
          </div>
          <div class="col-span-2">
            <div class="mb-2 text-sm">备注</div>
            <Input.TextArea
              v-model:value="dataModel.remark"
              :auto-size="{ minRows: 3 }"
              placeholder="选填"
            />
          </div>
        </div>
      </Card>
    </div>
  </Drawer>
</template>
