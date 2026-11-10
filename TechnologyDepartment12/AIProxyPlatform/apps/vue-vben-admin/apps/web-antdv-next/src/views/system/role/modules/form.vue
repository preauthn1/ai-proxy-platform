<script lang="ts" setup>
import type { RoleListItem, SysRole } from '#/api/system/role';

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
  createRoleApi,
  fetchAllRolesApi,
  updateRoleApi,
} from '#/api/system/role';
import { buildRoleParentOptions } from '#/views/system/role/data';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const saving = ref(false);

const parentOptions = ref<Array<{ label: string; value: number }>>([]);

interface FormModel {
  code: string;
  name: string;
  parentId: null | number;
  sort: number;
  isEnabled: 0 | 1;
  remark: string;
}

const model = reactive<FormModel>({
  code: '',
  name: '',
  parentId: null,
  sort: 0,
  isEnabled: 1,
  remark: '',
});

function resetModel() {
  Object.assign(model, {
    code: '',
    name: '',
    parentId: null,
    sort: 0,
    isEnabled: 1,
    remark: '',
  });
}

function fillModelFromRow(row: RoleListItem | SysRole) {
  Object.assign(model, {
    code: row.code,
    name: row.name,
    parentId: row.parentId,
    sort: row.sort,
    isEnabled: row.isEnabled === 1 ? 1 : 0,
    remark: row.remark ?? '',
  });
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

    const roles = await fetchAllRolesApi();
    const data = drawerApi.getData<{
      mode: 'create' | 'edit';
      row?: RoleListItem;
    }>();
    const editing = data?.mode === 'edit';
    parentOptions.value = buildRoleParentOptions(
      roles as unknown as RoleListItem[],
      editing ? data?.row?.id : undefined,
    );

    if (editing && data?.row) {
      id.value = data.row.id;
      fillModelFromRow(data.row);
    } else {
      id.value = undefined;
    }
  },
});

async function save() {
  if (!isEdit.value && !model.code.trim()) {
    message.warning('请输入角色编码');
    return;
  }
  if (!model.name.trim()) {
    message.warning('请输入角色名');
    return;
  }

  saving.value = true;
  try {
    if (isEdit.value && id.value) {
      await updateRoleApi({
        id: id.value,
        data: {
          name: model.name,
          parentId: model.parentId,
          sort: model.sort,
          isEnabled: model.isEnabled,
          remark: model.remark,
        },
      });
      message.success('保存成功');
    } else {
      await createRoleApi({
        code: model.code,
        name: model.name,
        parentId: model.parentId,
        sort: model.sort,
        isEnabled: model.isEnabled,
        remark: model.remark,
      });
      message.success('创建成功');
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
    :title="isEdit ? '编辑角色' : '新增角色'"
    :confirm-loading="saving"
    :width="560"
  >
    <Form layout="vertical">
      <div class="form-grid">
        <div>
          <FormItem label="编码" :required="!isEdit">
            <Input
              v-model:value="model.code"
              :maxlength="64"
              :disabled="isEdit"
              placeholder="如 operator"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="角色名" required>
            <Input
              v-model:value="model.name"
              :maxlength="64"
              placeholder="如 超级管理员"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="父角色">
            <Select
              v-model:value="model.parentId"
              :options="parentOptions"
              allow-clear
              show-search
              placeholder="— 顶级 —"
              style="width: 100%"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="排序">
            <InputNumber
              v-model:value="model.sort"
              :min="0"
              :precision="0"
              style="width: 100%"
            />
          </FormItem>
        </div>
        <div class="col-span-2">
          <FormItem label="状态">
            <Switch
              v-model:checked="model.isEnabled"
              :checked-value="1"
              :un-checked-value="0"
              checked-children="启用"
              un-checked-children="禁用"
            />
          </FormItem>
        </div>
        <div class="col-span-2">
          <FormItem label="备注">
            <TextArea
              v-model:value="model.remark"
              :auto-size="{ minRows: 3 }"
              placeholder="选填"
            />
          </FormItem>
        </div>
      </div>
    </Form>
  </Drawer>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.col-span-2 {
  grid-column: span 2;
}
</style>
