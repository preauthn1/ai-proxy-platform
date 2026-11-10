<script lang="ts" setup>
import type {
  CreateTaskConfigRequest,
  TaskConfig,
  TaskSelectOption,
} from '#/api/system/task-config';

import { computed, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  TextArea,
} from 'antdv-next';

import {
  createTaskConfigApi,
  fetchTaskQueuesApi,
  fetchTaskWorkflowTypesApi,
  updateTaskConfigApi,
} from '#/api/system/task-config';
import { $t } from '#/locales';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

function mergeCurrentOption(
  options: TaskSelectOption[],
  current: null | string | undefined,
): TaskSelectOption[] {
  const v = (current ?? '').trim();
  if (!v) return options;
  if (options.some((o) => o.value === v)) return options;
  return [{ label: v, value: v }, ...options];
}

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const saving = ref(false);
const workflowTypeOptions = ref<TaskSelectOption[]>([]);
const taskQueueOptions = ref<TaskSelectOption[]>([]);
const optionsLoading = ref(false);

interface FormModel {
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  cronExpr: string;
  timeoutSeconds: null | number;
  retryPolicyText: string;
  remark: string;
  isEnabled: 0 | 1;
}

const model = reactive<FormModel>({
  code: '',
  name: '',
  workflowType: '',
  taskQueue: '',
  cronExpr: '',
  timeoutSeconds: null,
  retryPolicyText: '',
  remark: '',
  isEnabled: 1,
});

const workflowSelectOptions = computed(() =>
  mergeCurrentOption(workflowTypeOptions.value, model.workflowType),
);
const queueSelectOptions = computed(() =>
  mergeCurrentOption(taskQueueOptions.value, model.taskQueue),
);

async function loadSelectOptions() {
  optionsLoading.value = true;
  try {
    const [wf, queues] = await Promise.all([
      fetchTaskWorkflowTypesApi(),
      fetchTaskQueuesApi(),
    ]);
    workflowTypeOptions.value = wf ?? [];
    taskQueueOptions.value = queues ?? [];
  } catch {
    workflowTypeOptions.value = [];
    taskQueueOptions.value = [];
  } finally {
    optionsLoading.value = false;
  }
}

function resetModel() {
  Object.assign(model, {
    code: '',
    name: '',
    workflowType: '',
    taskQueue: '',
    cronExpr: '',
    timeoutSeconds: null,
    retryPolicyText: '',
    remark: '',
    isEnabled: 1,
  });
}

function stringifyRetryPolicy(
  policy: null | Record<string, unknown> | undefined,
): string {
  if (!policy || Object.keys(policy).length === 0) return '';
  try {
    return JSON.stringify(policy, null, 2);
  } catch {
    return '';
  }
}

function fillModelFromRow(row: TaskConfig) {
  Object.assign(model, {
    code: row.code,
    name: row.name,
    workflowType: row.workflowType,
    taskQueue: row.taskQueue,
    cronExpr: row.cronExpr ?? '',
    timeoutSeconds: row.timeoutSeconds,
    retryPolicyText: stringifyRetryPolicy(row.retryPolicy),
    remark: row.remark ?? '',
    isEnabled: row.isEnabled === 1 ? 1 : 0,
  });
}

function parseRetryPolicy(
  text: string,
): null | Record<string, unknown> | undefined {
  const trimmed = text.trim();
  if (!trimmed) return null;
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  cancelText: '取消',
  confirmText: '保存',
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    await save();
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    resetModel();
    await loadSelectOptions();

    const data = drawerApi.getData<{
      mode: 'create' | 'edit';
      row?: TaskConfig;
    }>();

    if (data?.mode === 'edit' && data.row) {
      id.value = data.row.id;
      fillModelFromRow(data.row);
    } else {
      id.value = undefined;
    }
  },
});

async function save() {
  if (!model.code.trim()) {
    message.warning($t('task.config.codePlaceholder'));
    return;
  }
  if (!CODE_PATTERN.test(model.code.trim())) {
    message.warning($t('task.config.codeRule'));
    return;
  }
  if (!model.name.trim()) {
    message.warning($t('task.config.namePlaceholder'));
    return;
  }
  if (!model.workflowType.trim()) {
    message.warning($t('task.config.workflowTypePlaceholder'));
    return;
  }
  if (!model.taskQueue.trim()) {
    message.warning($t('task.config.taskQueuePlaceholder'));
    return;
  }

  const retryPolicy = parseRetryPolicy(model.retryPolicyText);
  if (retryPolicy === undefined) {
    message.error($t('task.config.retryPolicyInvalid'));
    return;
  }

  const body: CreateTaskConfigRequest = {
    code: model.code.trim(),
    name: model.name.trim(),
    workflowType: model.workflowType.trim(),
    taskQueue: model.taskQueue.trim(),
    cronExpr: model.cronExpr.trim() || null,
    timeoutSeconds:
      model.timeoutSeconds === null || model.timeoutSeconds === undefined
        ? null
        : Number(model.timeoutSeconds),
    retryPolicy,
    remark: model.remark ?? '',
    isEnabled: model.isEnabled,
  };

  saving.value = true;
  try {
    if (isEdit.value && id.value) {
      await updateTaskConfigApi({ id: id.value, data: body });
      message.success($t('task.config.updateOk'));
    } else {
      await createTaskConfigApi(body);
      message.success($t('task.config.createOk'));
    }
    emits('success');
    drawerApi.close();
  } catch (error) {
    message.error((error as Error).message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Drawer
    :title="isEdit ? $t('task.config.edit') : $t('task.config.create')"
    :confirm-loading="saving"
    :width="640"
  >
    <!-- 基础信息 -->
    <div class="section-title">基础信息</div>
    <Form layout="vertical">
      <div class="form-grid">
        <div>
          <FormItem :label="$t('task.config.code')" required>
            <Input
              v-model:value="model.code"
              :maxlength="64"
              :disabled="isEdit"
              :placeholder="$t('task.config.codePlaceholder')"
            />
          </FormItem>
        </div>
        <div>
          <FormItem :label="$t('task.config.name')" required>
            <Input
              v-model:value="model.name"
              :maxlength="128"
              :placeholder="$t('task.config.namePlaceholder')"
            />
          </FormItem>
        </div>
        <div>
          <FormItem :label="$t('task.config.workflowType')" required>
            <Select
              v-model:value="model.workflowType"
              show-search
              option-filter-prop="label"
              :loading="optionsLoading"
              :options="workflowSelectOptions"
              :placeholder="$t('task.config.workflowTypePlaceholder')"
              allow-clear
              style="width: 100%"
            />
          </FormItem>
        </div>
        <div>
          <FormItem :label="$t('task.config.taskQueue')" required>
            <Select
              v-model:value="model.taskQueue"
              show-search
              option-filter-prop="label"
              :loading="optionsLoading"
              :options="queueSelectOptions"
              :placeholder="$t('task.config.taskQueuePlaceholder')"
              allow-clear
              style="width: 100%"
            />
          </FormItem>
        </div>
        <div>
          <FormItem :label="$t('task.config.cronExpr')">
            <Input
              v-model:value="model.cronExpr"
              :maxlength="64"
              allow-clear
              :placeholder="$t('task.config.cronExprPlaceholder')"
            />
          </FormItem>
        </div>
        <div>
          <FormItem :label="$t('task.config.timeoutSeconds')">
            <InputNumber
              v-model:value="model.timeoutSeconds"
              :min="0"
              :precision="0"
              style="width: 100%"
              :placeholder="$t('task.config.timeoutPlaceholder')"
            />
          </FormItem>
        </div>
        <div class="col-span-2">
          <FormItem :label="$t('task.config.status')">
            <Switch
              v-model:checked="model.isEnabled"
              :checked-value="1"
              :un-checked-value="0"
              :checked-children="$t('task.config.enabled')"
              :un-checked-children="$t('task.config.disabled')"
            />
          </FormItem>
        </div>
      </div>
    </Form>

    <!-- 重试策略 -->
    <div class="section-title" style="margin-top: 24px">
      {{ $t('task.config.retryPolicy') }}
    </div>
    <Form layout="vertical">
      <FormItem>
        <TextArea
          v-model:value="model.retryPolicyText"
          :auto-size="{ minRows: 4, maxRows: 10 }"
          :placeholder="$t('task.config.retryPolicyPlaceholder')"
          class="font-mono text-xs"
        />
      </FormItem>
    </Form>

    <!-- 备注 -->
    <div class="section-title" style="margin-top: 8px">
      {{ $t('task.config.remark') }}
    </div>
    <TextArea
      v-model:value="model.remark"
      :auto-size="{ minRows: 3 }"
      :placeholder="$t('task.config.remarkPlaceholder')"
    />
  </Drawer>
</template>

<style scoped>
.section-title {
  margin-bottom: 16px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.col-span-2 {
  grid-column: span 2;
}
</style>
