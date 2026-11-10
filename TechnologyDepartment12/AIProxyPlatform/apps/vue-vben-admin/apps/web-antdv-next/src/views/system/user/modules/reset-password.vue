<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Form, FormItem, Input, message } from 'antdv-next';

import { resetUserPasswordApi } from '#/api/system/user';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const targetId = ref(0);
const targetUsername = ref('');
const saving = ref(false);

const model = reactive({
  password: '',
  confirmPassword: '',
});

function resetModel() {
  Object.assign(model, { password: '', confirmPassword: '' });
}

const [Drawer, drawerApi] = useVbenDrawer({
  cancelText: '取消',
  confirmText: '重置',
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    await save();
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    resetModel();
    const data = drawerApi.getData<{ id: number; username: string }>();
    targetId.value = data?.id ?? 0;
    targetUsername.value = data?.username ?? '';
  },
});

async function save() {
  if (!model.password) {
    message.warning('请输入新密码');
    return;
  }
  if (model.password !== model.confirmPassword) {
    message.warning('两次密码不一致');
    return;
  }
  saving.value = true;
  try {
    await resetUserPasswordApi({
      id: targetId.value,
      password: model.password,
    });
    message.success('密码已重置');
    emits('success');
    drawerApi.close();
  } catch (error) {
    message.error((error as Error).message ?? '重置失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Drawer
    :title="`重置密码 — ${targetUsername}`"
    :confirm-loading="saving"
    :width="480"
  >
    <Form layout="vertical">
      <FormItem label="新密码" required>
        <Input.Password
          v-model:value="model.password"
          :maxlength="128"
          placeholder="请输入新密码"
        />
      </FormItem>
      <FormItem label="确认新密码" required>
        <Input.Password
          v-model:value="model.confirmPassword"
          :maxlength="128"
          placeholder="请再次输入新密码"
        />
      </FormItem>
    </Form>
  </Drawer>
</template>
