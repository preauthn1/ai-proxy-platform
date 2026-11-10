<script lang="ts" setup>
import type {
  CreateMenuRequest,
  MenuBindApiItem,
  MenuType,
  SysMenu,
} from '#/api/system/menu';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Checkbox,
  Collapse,
  CollapsePanel,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  TabPane,
  Tabs,
  Tag,
  TextArea,
} from 'antdv-next';

import { fetchAllApisApi } from '#/api/system/api';
import {
  createMenuApi,
  fetchAllMenusApi,
  fetchMenuApisApi,
  setMenuApisApi,
  updateMenuApi,
} from '#/api/system/menu';
import { buildParentOptions } from '#/views/system/menu/data';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const activeTab = ref<'basic' | 'bind'>('basic');

// 父菜单选项 + 接口数据
const parentOptions = ref<Array<{ label: string; value: number }>>([]);
const allApis = ref<MenuBindApiItem[]>([]);
const apiSearch = ref('');
const boundIds = ref<Set<number>>(new Set());
const saving = ref(false);

interface BasicFormModel {
  parentId: null | number;
  name: string;
  type: MenuType;
  path: string;
  component: string;
  redirect: string;
  icon: string;
  permissionCode: string;
  sort: number;
  isHidden: 0 | 1;
  isEnabled: 0 | 1;
  remark: string;
  metadata: string;
}

const basicModel = reactive<BasicFormModel>({
  parentId: null,
  name: '',
  type: 'MENU',
  path: '',
  component: '',
  redirect: '',
  icon: '',
  permissionCode: '',
  sort: 0,
  isHidden: 0,
  isEnabled: 1,
  remark: '',
  metadata: '',
});

const menuTypeOptions = [
  { label: 'DIR — 目录', value: 'DIR' },
  { label: 'MENU — 菜单/路由', value: 'MENU' },
  { label: 'BUTTON — 按钮', value: 'BUTTON' },
];

const DEFAULT_METADATA_TEXT = JSON.stringify(
  {
    badge: '',
    hideInBreadcrumb: false,
    keepAlive: true,
    affix: false,
    activeMenu: '',
  },
  null,
  2,
);

function resetBasicModel() {
  Object.assign(basicModel, {
    parentId: null,
    name: '',
    type: 'MENU',
    path: '',
    component: '',
    redirect: '',
    icon: '',
    permissionCode: '',
    sort: 0,
    isHidden: 0,
    isEnabled: 1,
    remark: '',
    metadata: '',
  });
}

/** metadata 安全美化：非法 JSON 原样回显，避免抛错导致整表空白 */
function formatMetadataForForm(raw: null | string | undefined): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function fillBasicModelFromRow(row: SysMenu) {
  Object.assign(basicModel, {
    parentId: row.parentId,
    name: row.name,
    type: row.type,
    path: row.path ?? '',
    component: row.component ?? '',
    redirect: row.redirect ?? '',
    icon: row.icon ?? '',
    permissionCode: row.permissionCode ?? '',
    sort: row.sort,
    isHidden: row.isHidden === 1 ? 1 : 0,
    isEnabled: row.isEnabled === 1 ? 1 : 0,
    remark: row.remark ?? '',
    metadata: formatMetadataForForm(row.metadata),
  });
}

const currentType = computed(() => basicModel.type);

const [Drawer, drawerApi] = useVbenDrawer({
  cancelText: '取消',
  confirmText: activeTab.value === 'basic' ? '保存' : '保存绑定',
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    await (activeTab.value === 'basic' ? saveBasic() : saveBind());
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    activeTab.value = 'basic';
    apiSearch.value = '';
    boundIds.value = new Set();

    const data = drawerApi.getData<{
      mode: 'create' | 'edit';
      presetParentId?: null | number;
      row?: SysMenu;
    }>();
    const row = data?.mode === 'edit' ? data.row : undefined;
    const editing = !!row;

    // 先重置再回填，避免上次残留；await 选项加载后再 fill，保证 Select 能匹配 value
    resetBasicModel();

    // 拉父菜单与接口清单
    const [menus, apis] = await Promise.all([
      fetchAllMenusApi(),
      fetchAllApisApi(),
    ]);
    parentOptions.value = buildParentOptions(
      menus,
      editing ? row?.id : undefined,
    );

    // 选项写入 DOM 后再赋 model，避免父菜单/类型 Select 不回显
    await nextTick();

    if (editing && row) {
      id.value = row.id;
      fillBasicModelFromRow(row);
      // 拉已绑定接口
      const bound = await fetchMenuApisApi(row.id);
      allApis.value = bound;
      const init = new Set<number>();
      bound.forEach((a) => {
        if (a.bound) init.add(a.id);
      });
      boundIds.value = init;
    } else {
      id.value = undefined;
      const preset = data?.presetParentId ?? null;
      Object.assign(basicModel, {
        parentId: preset,
        type: 'MENU',
      });
      allApis.value = apis.map((a) => ({ ...a, bound: false }));
    }
  },
});

// tab 切换时更新确认按钮文案
watch(activeTab, (tab) => {
  drawerApi.setState({ confirmText: tab === 'basic' ? '保存' : '保存绑定' });
});

async function saveBasic() {
  // 基础校验
  if (!basicModel.name.trim()) {
    message.warning('请输入菜单名');
    return;
  }
  if (basicModel.type === 'MENU' && !basicModel.path.trim()) {
    message.warning('MENU 必须填写路由路径');
    return;
  }
  if (basicModel.type === 'BUTTON' && !basicModel.permissionCode.trim()) {
    message.warning('BUTTON 必须填写权限码');
    return;
  }

  let metadata: null | string = null;
  const rawMetadata = basicModel.metadata.trim();
  if (rawMetadata) {
    try {
      metadata = JSON.stringify(JSON.parse(rawMetadata));
    } catch {
      message.error('前端扩展 (metadata) 不是合法 JSON');
      return;
    }
  }

  const body: CreateMenuRequest = {
    parentId: basicModel.parentId,
    name: basicModel.name,
    type: basicModel.type,
    path: basicModel.type === 'MENU' ? basicModel.path || null : null,
    component: basicModel.type === 'MENU' ? basicModel.component || null : null,
    icon: basicModel.icon,
    redirect: basicModel.redirect,
    permissionCode:
      basicModel.type === 'BUTTON' || basicModel.type === 'MENU'
        ? basicModel.permissionCode || null
        : null,
    metadata,
    sort: basicModel.sort,
    isHidden: basicModel.isHidden ? 1 : 0,
    isEnabled: basicModel.isEnabled ? 1 : 0,
    remark: basicModel.remark,
  };

  saving.value = true;
  try {
    if (isEdit.value && id.value) {
      await updateMenuApi({ id: id.value, data: body });
      message.success('保存成功');
    } else {
      await createMenuApi(body);
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

async function saveBind() {
  if (!isEdit.value || !id.value) {
    message.warning('请先保存菜单基础信息');
    return;
  }
  saving.value = true;
  try {
    await setMenuApisApi(id.value, [...boundIds.value]);
    message.success('接口绑定已保存');
  } catch (error) {
    message.error((error as Error).message ?? '保存绑定失败');
  } finally {
    saving.value = false;
  }
}

const groupedApis = computed(() => {
  const groups = new Map<string, MenuBindApiItem[]>();
  const kw = apiSearch.value.trim().toLowerCase();
  allApis.value.forEach((a) => {
    if (kw) {
      const hit =
        a.name.toLowerCase().includes(kw) ||
        a.path.toLowerCase().includes(kw) ||
        a.permissionCode.toLowerCase().includes(kw);
      if (!hit) return;
    }
    const arr = groups.get(a.apiGroup) ?? [];
    arr.push(a);
    groups.set(a.apiGroup, arr);
  });
  return [...groups.entries()].toSorted((a, b) => a[0].localeCompare(b[0]));
});

const METHOD_COLOR: Record<string, string> = {
  GET: 'blue',
  POST: 'green',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  OPTIONS: 'default',
  HEAD: 'default',
};

function toggleApi(apiId: number, checked: boolean) {
  const next = new Set(boundIds.value);
  if (checked) next.add(apiId);
  else next.delete(apiId);
  boundIds.value = next;
}

function toggleGroup(apis: MenuBindApiItem[], checked: boolean) {
  const next = new Set(boundIds.value);
  apis.forEach((a) => {
    if (checked) next.add(a.id);
    else next.delete(a.id);
  });
  boundIds.value = next;
}
</script>

<template>
  <Drawer
    :title="isEdit ? '编辑菜单' : '新增菜单'"
    :confirm-loading="saving"
    :width="640"
  >
    <Tabs v-model:active-key="activeTab">
      <TabPane key="basic" tab="基础信息" />
      <TabPane
        key="bind"
        :tab="`绑定接口（${boundIds.size}）`"
        :disabled="!isEdit"
      />
    </Tabs>

    <div v-show="activeTab === 'basic'" class="menu-basic-form">
      <div class="section-title">
        基础（{{
          currentType === 'DIR'
            ? '目录'
            : currentType === 'MENU'
              ? '菜单'
              : '按钮'
        }}）
      </div>

      <Form layout="vertical">
        <div class="form-grid">
          <div>
            <FormItem label="父菜单">
              <Select
                v-model:value="basicModel.parentId"
                :options="parentOptions"
                :filter-option="true"
                allow-clear
                placeholder="— 顶级 —"
                show-search
                style="width: 100%"
              />
            </FormItem>
          </div>
          <div>
            <FormItem label="类型" required>
              <Select
                v-model:value="basicModel.type"
                :options="menuTypeOptions"
                style="width: 100%"
              />
            </FormItem>
          </div>
          <div>
            <FormItem label="菜单名" required>
              <Input
                v-model:value="basicModel.name"
                :maxlength="64"
                placeholder="如 用户管理"
              />
            </FormItem>
          </div>
          <div>
            <FormItem label="排序">
              <InputNumber
                v-model:value="basicModel.sort"
                :min="0"
                :precision="0"
                style="width: 100%"
              />
            </FormItem>
          </div>

          <template v-if="currentType === 'MENU'">
            <div>
              <FormItem label="图标">
                <Input v-model:value="basicModel.icon" placeholder="如 user" />
              </FormItem>
            </div>
            <div>
              <FormItem label="路由路径" required>
                <Input
                  v-model:value="basicModel.path"
                  placeholder="如 /admin/users"
                />
              </FormItem>
            </div>
            <div>
              <FormItem label="前端组件">
                <Input
                  v-model:value="basicModel.component"
                  placeholder="如 views/admin/users/index"
                />
              </FormItem>
            </div>
            <div class="col-span-2">
              <FormItem label="重定向">
                <Input
                  v-model:value="basicModel.redirect"
                  placeholder="如 /admin/users/list"
                />
              </FormItem>
            </div>
            <div class="col-span-2">
              <FormItem label="权限码">
                <Input
                  v-model:value="basicModel.permissionCode"
                  placeholder="如 admin:user:list"
                />
              </FormItem>
            </div>
          </template>

          <div v-if="currentType === 'BUTTON'" class="col-span-2">
            <FormItem label="权限码" required>
              <Input
                v-model:value="basicModel.permissionCode"
                placeholder="如 admin:user:create"
              />
            </FormItem>
          </div>

          <div class="col-span-2">
            <FormItem label="前端隐藏">
              <Switch
                v-model:checked="basicModel.isHidden"
                :checked-value="1"
                :un-checked-value="0"
                checked-children="隐藏"
                un-checked-children="显示"
              />
            </FormItem>
          </div>

          <div class="col-span-2">
            <FormItem label="状态">
              <Switch
                v-model:checked="basicModel.isEnabled"
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
                v-model:value="basicModel.remark"
                :auto-size="{ minRows: 3 }"
                placeholder="选填"
              />
            </FormItem>
          </div>
        </div>
      </Form>

      <div class="section-title" style="margin-top: 24px">
        前端扩展（METADATA）
      </div>
      <TextArea
        v-model:value="basicModel.metadata"
        :auto-size="{ minRows: 8 }"
        :placeholder="DEFAULT_METADATA_TEXT"
        class="metadata-editor"
      />
      <div class="metadata-hint">JSON 格式，用于前端框架的路由元信息</div>
    </div>

    <div v-show="activeTab === 'bind'">
      <Input
        v-model:value="apiSearch"
        placeholder="按路径或名称搜索接口..."
        allow-clear
        style="margin-bottom: 12px"
      />
      <div style="margin-bottom: 8px; color: #666">
        已选 <strong>{{ boundIds.size }}</strong> 个接口
      </div>
      <Collapse :default-active-key="groupedApis.map((g) => g[0])">
        <CollapsePanel v-for="[group, apis] in groupedApis" :key="group">
          <template #header>
            <div class="group-header">
              <span>{{ group }} · {{ apis.length }} 个</span>
              <Checkbox
                :checked="
                  apis.length > 0 && apis.every((a) => boundIds.has(a.id))
                "
                @change="(e: any) => toggleGroup(apis, e.target.checked)"
                @click.stop
              >
                全选
              </Checkbox>
            </div>
          </template>
          <div style="display: flex; flex-direction: column; gap: 6px">
            <Checkbox
              v-for="a in apis"
              :key="a.id"
              :checked="boundIds.has(a.id)"
              @change="(e: any) => toggleApi(a.id, e.target.checked)"
            >
              <span style="font-family: monospace; font-size: 12px">
                <Tag :color="METHOD_COLOR[a.method]" style="margin-right: 6px">
                  {{ a.method }}
                </Tag>
                {{ a.path }}
              </span>
              <span style="margin-left: 8px; font-size: 12px; color: #999">
                {{ a.name }}
              </span>
            </Checkbox>
          </div>
        </CollapsePanel>
      </Collapse>
    </div>
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

.metadata-editor {
  font-family: ui-monospace, 'JetBrains Mono', 'Cascadia Code', monospace;
  font-size: 12px;
}

.metadata-hint {
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 16px;
}
</style>
