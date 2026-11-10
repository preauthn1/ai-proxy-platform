<script lang="ts" setup>
import type {
  CreateI18nLocaleRequest,
  I18nLocale,
  UpdateI18nLocaleRequest,
} from '#/api/system/i18n';

import { computed, nextTick, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Input,
  InputNumber,
  message,
  Space,
  Switch,
  theme,
  Tooltip,
  TypographyParagraph,
} from 'antdv-next';

import { createI18nLocaleApi, updateI18nLocaleApi } from '#/api/system/i18n';
import { I18N_LOCALE_CODE_PATTERN } from '#/views/system/i18n/data';

interface Props {
  kind: 'locale';
}

const props = defineProps<Props>();

const emits = defineEmits<{
  (e: 'success'): void;
}>();

interface FormState {
  code: string;
  name: string;
  sort: number;
  remark: string;
  isDefault: boolean;
  isEnabled: boolean;
}

const { token } = theme.useToken();

const formState = reactive<FormState>({
  code: '',
  name: '',
  sort: 0,
  remark: '',
  isDefault: false,
  isEnabled: true,
});

const isEdit = ref(false);
const editId = ref<number>();
const codeDisabled = computed(() => isEdit.value);

function validate(): null | string {
  const { code, name } = formState;
  if (!name || !name.trim()) return '请输入语言名称';
  if (name.length > 64) return '语言名称最长 64 字符';
  if (!code || !code.trim()) return '请输入语言代码';
  if (!I18N_LOCALE_CODE_PATTERN.test(code))
    return '形如 zh-CN / en-US / ja-JP（BCP-47 风格）';
  return null;
}

const [Drawer, drawerApi] = useVbenDrawer({
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    if (props.kind !== 'locale') return;

    const err = validate();
    if (err) {
      message.error(err);
      return;
    }

    const values = formState;
    const body = {
      code: values.code.trim(),
      name: values.name.trim(),
      sort: values.sort ?? 0,
      remark: values.remark ?? '',
      isDefault: values.isDefault ? (1 as const) : (0 as const),
      isEnabled: values.isEnabled ? (1 as const) : (0 as const),
    };

    drawerApi.lock();
    const req = editId.value
      ? updateI18nLocaleApi(
          editId.value,
          body as Partial<UpdateI18nLocaleRequest>,
        )
      : createI18nLocaleApi(body as CreateI18nLocaleRequest);

    req
      .then(() => {
        message.success(isEdit.value ? '保存成功' : '创建成功');
        emits('success');
        drawerApi.close();
      })
      .catch((error: Error) => {
        message.error(
          `${isEdit.value ? '保存' : '创建'}失败：${error.message ?? '未知错误'}`,
        );
        drawerApi.unlock();
      });
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData() as
      | (I18nLocale & { id?: number })
      | (Record<string, unknown> & { id?: number })
      | undefined;

    const editing = !!data?.id;
    isEdit.value = editing;

    if (editing) {
      editId.value = data?.id as number;
      const t = data as unknown as I18nLocale;
      await nextTick();
      formState.code = t.code;
      formState.name = t.name;
      formState.sort = t.sort ?? 0;
      formState.remark = t.remark ?? '';
      formState.isDefault = t.isDefault === 1;
      formState.isEnabled = t.isEnabled === 1;
    } else {
      editId.value = undefined;
      formState.code = '';
      formState.name = '';
      formState.sort = 0;
      formState.remark = '';
      formState.isDefault = false;
      formState.isEnabled = true;
    }
  },
});

const drawerTitle = computed(() => (isEdit.value ? '编辑语言' : '新建语言'));
</script>

<template>
  <Drawer :title="drawerTitle" :width="560" :destroy-on-close="true">
    <div v-if="props.kind === 'locale'" class="flex flex-col">
      <!-- 语种名称 -->
      <div class="mb-4">
        <div class="mb-2 text-sm">
          <span :style="{ color: token.colorError }">*</span>
          <span>&nbsp;语种名称</span>
        </div>
        <Input
          v-model:value="formState.name"
          placeholder="例如 简体中文"
          :maxlength="64"
          style="border-radius: 4px"
        >
          <template #prefix>
            <IconifyIcon
              icon="ant-design:field-string-outlined"
              :style="{ color: token.colorTextTertiary }"
            />
          </template>
        </Input>
      </div>

      <!-- Locale code -->
      <div class="mb-4">
        <div class="mb-2 text-sm">
          <Space :size="4" align="center">
            <span :style="{ color: token.colorError }">*</span>
            <span>语言代码</span>
            <Tooltip
              v-if="codeDisabled"
              title="语言代码在创建后不可修改（关联翻译键和用户偏好）"
            >
              <IconifyIcon
                icon="ant-design:lock-outlined"
                :style="{
                  color: token.colorTextTertiary,
                  fontSize: '12px',
                  cursor: 'help',
                }"
              />
            </Tooltip>
          </Space>
        </div>
        <Input
          v-model:value="formState.code"
          placeholder="例如 zh-CN"
          :disabled="codeDisabled"
          style="border-radius: 4px"
        >
          <template #prefix>
            <IconifyIcon
              icon="ant-design:code-outlined"
              :style="{ color: token.colorTextTertiary }"
            />
          </template>
        </Input>
        <TypographyParagraph type="secondary" class="mt-1 mb-0 text-xs">
          BCP-47 风格（如 zh-CN / en-US / ja-JP），建议与 i18n 标准库一致
        </TypographyParagraph>
      </div>

      <!-- 排序 -->
      <div class="mb-4">
        <div class="mb-2 text-sm">排序</div>
        <InputNumber
          v-model:value="formState.sort"
          placeholder="升序排序"
          :min="0"
          :step="1"
          :precision="0"
          style="width: 100%; border-radius: 4px"
        >
          <template #prefix>
            <IconifyIcon
              icon="ant-design:field-number-outlined"
              :style="{ color: token.colorTextTertiary }"
            />
          </template>
        </InputNumber>
        <TypographyParagraph type="secondary" class="mt-1 mb-0 text-xs">
          数值越小越靠前；用于语言下拉与切换面板的展示顺序
        </TypographyParagraph>
      </div>

      <!-- 备注 -->
      <div class="mb-4">
        <div class="mb-2 text-sm">备注</div>
        <Input.TextArea
          v-model:value="formState.remark"
          :rows="3"
          :maxlength="200"
          placeholder="选填；可记录使用范围、地区变体等"
          style="border-radius: 4px"
        />
      </div>

      <!-- 设为默认语言 -->
      <div
        class="mt-2 flex flex-col gap-1.5 rounded-md border px-3 py-2.5"
        :style="{
          borderColor: token.colorBorderSecondary,
          background: token.colorFillQuaternary,
        }"
      >
        <div class="flex items-center justify-between">
          <Space :size="6" align="center">
            <IconifyIcon
              icon="ant-design:star-outlined"
              :style="{ color: token.colorPrimary }"
            />
            <span class="text-sm" :style="{ color: token.colorText }">
              设为默认语言
            </span>
          </Space>
          <Switch
            v-model:checked="formState.isDefault"
            checked-children="默认"
            un-checked-children="否"
          />
        </div>
        <TypographyParagraph type="secondary" class="mb-0 text-xs">
          同一时刻仅一个语言可标记为默认；新用户偏好会沿用此值
        </TypographyParagraph>
      </div>

      <!-- 启用 -->
      <div
        class="mt-3 flex flex-col gap-1.5 rounded-md border px-3 py-2.5"
        :style="{
          borderColor: token.colorBorderSecondary,
          background: token.colorFillQuaternary,
        }"
      >
        <div class="flex items-center justify-between">
          <Space :size="6" align="center">
            <IconifyIcon
              icon="ant-design:check-circle-outlined"
              :style="{ color: token.colorPrimary }"
            />
            <span class="text-sm" :style="{ color: token.colorText }">
              启用
            </span>
          </Space>
          <Switch
            v-model:checked="formState.isEnabled"
            checked-children="启用"
            un-checked-children="禁用"
          />
        </div>
        <TypographyParagraph type="secondary" class="mb-0 text-xs">
          禁用后该语言不会出现在用户切换面板与翻译键可选范围
        </TypographyParagraph>
      </div>

      <!-- 底部说明卡片 -->
      <div
        class="mt-4 flex items-start gap-1.5 rounded px-3 py-2.5 text-xs"
        :style="{
          color: token.colorTextSecondary,
          background: token.colorPrimaryBg,
          borderLeft: `3px solid ${token.colorPrimary}`,
        }"
      >
        <IconifyIcon
          icon="ant-design:info-circle-outlined"
          :style="{
            color: token.colorPrimary,
            fontSize: '14px',
            marginTop: '1px',
          }"
        />
        <span>
          默认语言禁止删除；存在翻译时禁止删除该语言。语言代码一旦创建不可修改。
        </span>
      </div>
    </div>
  </Drawer>
</template>
