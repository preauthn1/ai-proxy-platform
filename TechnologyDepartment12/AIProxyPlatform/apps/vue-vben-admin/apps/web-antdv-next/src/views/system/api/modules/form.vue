<script lang="ts" setup>
import type { CreateApiRequest, SysApi } from '#/api/system/api';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createApiApi,
  fetchApiGroupsApi,
  updateApiApi,
} from '#/api/system/api';
import { useApiFormSchema } from '#/views/system/api/data';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const saving = ref(false);

const [Form, formApi] = useVbenForm({
  schema: useApiFormSchema() ?? [],
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: async () => {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    const body: CreateApiRequest = {
      name: values.name as string,
      method: values.method as SysApi['method'],
      path: values.path as string,
      permissionCode: values.permissionCode as string,
      apiGroup: (values.apiGroup as string) ?? '',
      remark: (values.remark as string) ?? '',
      // Switch 存 boolean，提交时映射 0|1
      isEnabled: values.isEnabled ? 1 : 0,
    };
    saving.value = true;
    try {
      if (isEdit.value && id.value) {
        await updateApiApi({ id: id.value, data: body });
        message.success('保存成功');
      } else {
        await createApiApi(body);
        message.success('创建成功');
      }
      emits('success');
      drawerApi.close();
    } catch (error) {
      message.error((error as Error).message ?? '保存失败');
    } finally {
      saving.value = false;
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData<{ row?: SysApi }>();
    const row = data?.row;
    const editing = !!row;

    // 拉分组（apiGroup 仍为 Input 自由填写）
    await fetchApiGroupsApi().catch(() => [] as string[]);

    formApi.resetForm();
    // 等 schema 字段挂到 form.values 后再 setValues，避免 filterFields 合并成空对象
    await nextTick();

    if (editing && row) {
      id.value = row.id;
      // filterFields=false：直接写入，不依赖 form.values 已有 key
      // isEnabled 用 boolean，与 dict / Switch checked 对齐
      await formApi.setValues(
        {
          name: row.name,
          method: row.method,
          apiGroup: row.apiGroup,
          path: row.path,
          permissionCode: row.permissionCode,
          remark: row.remark,
          isEnabled: row.isEnabled === 1,
        },
        false,
      );
    } else {
      id.value = undefined;
      await formApi.setValues(
        {
          name: '',
          method: 'GET',
          apiGroup: '',
          path: '',
          permissionCode: '',
          remark: '',
          isEnabled: true,
        },
        false,
      );
    }
  },
});
</script>

<template>
  <Drawer :title="isEdit ? '编辑接口' : '新增接口'" :confirm-loading="saving">
    <Form />
  </Drawer>
</template>
