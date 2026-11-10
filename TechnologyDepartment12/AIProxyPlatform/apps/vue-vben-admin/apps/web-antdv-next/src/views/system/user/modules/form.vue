<script lang="ts" setup>
import type { SysRole } from '#/api/system/role';
import type { UserListItem } from '#/api/system/user';

import { computed, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Form,
  FormItem,
  Input,
  message,
  Select,
  Switch,
  TextArea,
} from 'antdv-next';

import { fetchAllI18nLocalesApi } from '#/api/system/i18n';
import { fetchAllRolesApi } from '#/api/system/role';
import { createUserApi, updateUserApi } from '#/api/system/user';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const saving = ref(false);

// 下拉数据源
const localeOptions = ref<Array<{ label: string; value: string }>>([]);
const roleOptions = ref<Array<{ label: string; value: number }>>([]);

interface FormModel {
  username: string;
  nickname: string;
  email: string;
  phone: string;
  avatar: string;
  languageCode: string;
  isEnabled: 0 | 1;
  password: string;
  confirmPassword: string;
  roleIds: number[];
  remark: string;
}

const model = reactive<FormModel>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  languageCode: '',
  isEnabled: 1,
  password: '',
  confirmPassword: '',
  roleIds: [],
  remark: '',
});

function resetModel() {
  Object.assign(model, {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    avatar: '',
    languageCode: '',
    isEnabled: 1,
    password: '',
    confirmPassword: '',
    roleIds: [],
    remark: '',
  });
}

function fillModelFromRow(row: UserListItem) {
  Object.assign(model, {
    username: row.username,
    nickname: row.nickname,
    email: row.email ?? '',
    phone: row.phone ?? '',
    avatar: row.avatar ?? '',
    languageCode: row.languageCode ?? '',
    isEnabled: row.isEnabled === 1 ? 1 : 0,
    password: '',
    confirmPassword: '',
    roleIds: [...row.roleIds],
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

    // 拉语言与角色下拉（仅启用角色，避免绑到已禁用角色）
    const [locales, roles] = await Promise.all([
      fetchAllI18nLocalesApi(),
      fetchAllRolesApi({ status: 1 }),
    ]);
    localeOptions.value = locales.map((l) => ({
      label: `${l.name}（${l.code}）`,
      value: l.code,
    }));
    roleOptions.value = roles.map((r: SysRole) => ({
      label: r.name,
      value: r.id,
    }));

    const data = drawerApi.getData<{
      mode: 'create' | 'edit';
      row?: UserListItem;
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
  if (!model.nickname.trim()) {
    message.warning('请输入昵称');
    return;
  }
  if (!isEdit.value) {
    // 创建态校验
    if (!model.username.trim()) {
      message.warning('请输入用户名');
      return;
    }
    if (!model.password) {
      message.warning('请输入密码');
      return;
    }
    if (model.password !== model.confirmPassword) {
      message.warning('两次密码不一致');
      return;
    }
  }

  saving.value = true;
  try {
    if (isEdit.value && id.value) {
      await updateUserApi({
        id: id.value,
        data: {
          nickname: model.nickname,
          email: model.email || undefined,
          phone: model.phone || undefined,
          avatar: model.avatar || undefined,
          languageCode: model.languageCode || undefined,
          isEnabled: model.isEnabled,
          remark: model.remark,
          roleIds: model.roleIds,
        },
      });
      message.success('保存成功');
    } else {
      await createUserApi({
        username: model.username,
        password: model.password,
        nickname: model.nickname,
        email: model.email || undefined,
        phone: model.phone || undefined,
        avatar: model.avatar || undefined,
        languageCode: model.languageCode || undefined,
        isEnabled: model.isEnabled,
        roleIds: model.roleIds,
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
    :title="isEdit ? '编辑用户' : '新建用户'"
    :confirm-loading="saving"
    :width="640"
  >
    <!-- 基础信息段 -->
    <div class="section-title">基础信息</div>
    <Form layout="vertical">
      <div class="form-grid">
        <div>
          <FormItem label="用户名" :required="!isEdit">
            <Input
              v-model:value="model.username"
              :maxlength="64"
              :disabled="isEdit"
              placeholder="登录用户名"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="昵称" required>
            <Input
              v-model:value="model.nickname"
              :maxlength="64"
              placeholder="显示昵称"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="邮箱">
            <Input
              v-model:value="model.email"
              :maxlength="128"
              placeholder="如 admin@example.com"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="手机号">
            <Input
              v-model:value="model.phone"
              :maxlength="32"
              placeholder="如 13800138000"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="头像">
            <Input
              v-model:value="model.avatar"
              :maxlength="255"
              placeholder="头像 URL"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="默认语言">
            <Select
              v-model:value="model.languageCode"
              :options="localeOptions"
              allow-clear
              show-search
              placeholder="选择默认语言"
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
      </div>
    </Form>

    <!-- 安全段 -->
    <div class="section-title" style="margin-top: 24px">安全</div>
    <Form layout="vertical">
      <div class="form-grid">
        <div>
          <FormItem label="密码" :required="!isEdit">
            <Input.Password
              v-model:value="model.password"
              :maxlength="128"
              :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'"
            />
          </FormItem>
        </div>
        <div>
          <FormItem label="确认密码" :required="!isEdit">
            <Input.Password
              v-model:value="model.confirmPassword"
              :maxlength="128"
              :placeholder="isEdit ? '留空则不修改密码' : '请再次输入密码'"
            />
          </FormItem>
        </div>
      </div>
    </Form>

    <!-- 角色段 -->
    <div class="section-title" style="margin-top: 24px">角色</div>
    <Form layout="vertical">
      <FormItem label="角色">
        <Select
          v-model:value="model.roleIds"
          :options="roleOptions"
          mode="multiple"
          allow-clear
          show-search
          placeholder="选择关联角色"
          style="width: 100%"
        />
      </FormItem>
    </Form>

    <!-- 备注段 -->
    <div class="section-title" style="margin-top: 24px">备注</div>
    <TextArea
      v-model:value="model.remark"
      :auto-size="{ minRows: 3 }"
      placeholder="选填"
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
