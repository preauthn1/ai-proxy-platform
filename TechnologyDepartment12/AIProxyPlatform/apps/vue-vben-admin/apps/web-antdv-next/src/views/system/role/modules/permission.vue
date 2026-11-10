<script lang="ts" setup>
import type { SysMenu } from '#/api/system/menu';
import type { RoleApiBindItem, RoleMenuBindItem } from '#/api/system/role';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Button,
  Checkbox,
  Collapse,
  CollapsePanel,
  Input,
  message,
  Space,
  TabPane,
  Tabs,
  Tag,
} from 'antdv-next';

import { fetchApisByMenusApi } from '#/api/system/menu';
import {
  fetchRoleApisApi,
  fetchRoleMenusApi,
  setRoleApisApi,
  setRoleMenusApi,
} from '#/api/system/role';
import { refreshAccess } from '#/router/refresh-access';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const router = useRouter();

const roleId = ref(0);
const roleName = ref('');
const activeTab = ref<'api' | 'data' | 'menu'>('menu');
const saving = ref(false);
const pulling = ref(false);

// 菜单权限
const allMenus = ref<RoleMenuBindItem[]>([]);
const checkedMenuIds = ref<Set<number>>(new Set());

// 接口权限
const allApis = ref<RoleApiBindItem[]>([]);
const checkedApiIds = ref<Set<number>>(new Set());
const apiSearch = ref('');

const [Drawer, drawerApi] = useVbenDrawer({
  cancelText: '取消',
  confirmText: '保存菜单授权',
  onCancel() {
    drawerApi.close();
  },
  async onConfirm() {
    await save();
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    activeTab.value = 'menu';
    apiSearch.value = '';
    checkedMenuIds.value = new Set();
    checkedApiIds.value = new Set();

    const data = drawerApi.getData<{ id: number; name: string }>();
    roleId.value = data?.id ?? 0;
    roleName.value = data?.name ?? '';

    if (!roleId.value) return;
    const [menus, apis] = await Promise.all([
      fetchRoleMenusApi(roleId.value),
      fetchRoleApisApi(roleId.value),
    ]);
    allMenus.value = menus;
    allApis.value = apis;
    checkedMenuIds.value = new Set(
      menus.filter((m) => m.bound).map((m) => m.id),
    );
    checkedApiIds.value = new Set(apis.filter((a) => a.bound).map((a) => a.id));
  },
});

watch(activeTab, (tab) => {
  const text =
    tab === 'menu' ? '保存菜单授权' : tab === 'api' ? '保存接口授权' : '保存';
  drawerApi.setState({ confirmText: text });
});

async function save() {
  if (!roleId.value) return;
  if (activeTab.value === 'data') {
    message.info('数据权限暂未实现');
    return;
  }
  saving.value = true;
  try {
    if (activeTab.value === 'menu') {
      await setRoleMenusApi(roleId.value, [...checkedMenuIds.value]);
      message.success('菜单授权已保存；可切换到「接口权限」从已选菜单带出接口');
      await refreshAccess(router);
      emits('success');
      // 保持抽屉打开，便于继续配接口
    } else if (activeTab.value === 'api') {
      await setRoleApisApi(roleId.value, [...checkedApiIds.value]);
      message.success('接口授权已保存');
      emits('success');
      drawerApi.close();
    }
  } catch (error) {
    message.error((error as Error).message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}

/* ============================================================
 * 菜单树：本地组树 → 扁平深度列表（任意深度）
 * ============================================================ */
interface MenuNode extends SysMenu {
  children?: MenuNode[];
}

const menuTree = computed<MenuNode[]>(() => {
  const byId = new Map<number, MenuNode>();
  allMenus.value.forEach((m) => byId.set(m.id, { ...m }));
  const roots: MenuNode[] = [];
  for (const node of byId.values()) {
    if (node.parentId === null || node.parentId === undefined) {
      roots.push(node);
    } else {
      const parent = byId.get(node.parentId);
      if (parent) {
        parent.children = parent.children ?? [];
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
  }
  const sortRec = (arr: MenuNode[]) => {
    arr.sort((a, b) => a.sort - b.sort || a.id - b.id);
    arr.forEach((n) => n.children && sortRec(n.children));
  };
  sortRec(roots);
  return roots;
});

/** 深度优先展平，模板只 v-for 一层即可覆盖 DIR/MENU/BUTTON 任意层级 */
const flatMenuRows = computed(() => {
  const rows: Array<{ depth: number; node: MenuNode }> = [];
  const walk = (nodes: MenuNode[], depth: number) => {
    for (const node of nodes) {
      rows.push({ node, depth });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  };
  walk(menuTree.value, 0);
  return rows;
});

function toggleMenuNode(node: MenuNode, checked: boolean) {
  const next = new Set(checkedMenuIds.value);
  const walk = (n: MenuNode, on: boolean) => {
    if (on) next.add(n.id);
    else next.delete(n.id);
    n.children?.forEach((c) => walk(c, on));
  };
  walk(node, checked);
  checkedMenuIds.value = next;
}

function typeTagColor(type: string) {
  if (type === 'DIR') return 'blue';
  if (type === 'MENU') return 'green';
  return 'orange';
}

function typeTagLabel(type: string) {
  if (type === 'DIR') return 'DIR';
  if (type === 'MENU') return 'MENU';
  return 'BTN';
}

/* ============================================================
 * 接口权限：按 apiGroup 分组 Collapse
 * ============================================================ */
const groupedApis = computed(() => {
  const groups = new Map<string, RoleApiBindItem[]>();
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
  const next = new Set(checkedApiIds.value);
  if (checked) next.add(apiId);
  else next.delete(apiId);
  checkedApiIds.value = next;
}

function toggleApiGroup(apis: RoleApiBindItem[], checked: boolean) {
  const next = new Set(checkedApiIds.value);
  apis.forEach((a) => {
    if (checked) next.add(a.id);
    else next.delete(a.id);
  });
  checkedApiIds.value = next;
}

/** 从当前勾选菜单的 menu_api 绑定并入接口勾选（不覆盖已选手选） */
async function pullApisFromMenus() {
  if (checkedMenuIds.value.size === 0) {
    message.warning('请先在「菜单权限」中勾选菜单');
    return;
  }
  pulling.value = true;
  try {
    const res = await fetchApisByMenusApi([...checkedMenuIds.value]);
    const prev = checkedApiIds.value;
    const added = res.apiIds.filter((id) => !prev.has(id)).length;
    const next = new Set(prev);
    res.apiIds.forEach((id) => next.add(id));
    checkedApiIds.value = next;
    if (res.apiIds.length === 0) {
      message.info('已选菜单尚未绑定接口（请先在菜单管理中绑定）');
    } else {
      message.success(
        `已从菜单带出 ${res.apiIds.length} 个接口（新增 ${added}）`,
      );
    }
  } catch (error) {
    message.error((error as Error).message ?? '带出接口失败');
  } finally {
    pulling.value = false;
  }
}
</script>

<template>
  <Drawer
    :title="`分配权限 — ${roleName}`"
    :confirm-loading="saving"
    :width="720"
  >
    <Tabs v-model:active-key="activeTab">
      <TabPane key="menu" :tab="`菜单权限（${checkedMenuIds.size}）`" />
      <TabPane key="api" :tab="`接口权限（${checkedApiIds.size}）`" />
      <TabPane key="data" tab="数据权限" disabled />
    </Tabs>

    <!-- 菜单权限：扁平深度列表覆盖任意层级 -->
    <div v-show="activeTab === 'menu'">
      <div style="margin-bottom: 8px; color: #666">
        已选
        <strong>{{ checkedMenuIds.size }}</strong>
        个菜单。保存后全量替换菜单授权；
        接口需在「接口权限」单独保存，可用「从已选菜单带出接口」。
      </div>
      <div class="menu-tree">
        <div
          v-for="{ node, depth } in flatMenuRows"
          :key="node.id"
          class="menu-node"
          :style="{ marginLeft: `${depth * 24}px` }"
        >
          <Checkbox
            :checked="checkedMenuIds.has(node.id)"
            @change="(e: any) => toggleMenuNode(node, e.target.checked)"
          >
            <Tag :color="typeTagColor(node.type)" style="margin-right: 4px">
              {{ typeTagLabel(node.type) }}
            </Tag>
            {{ node.name }}
          </Checkbox>
        </div>
        <div v-if="!flatMenuRows.length" style="color: #999">暂无菜单</div>
      </div>
    </div>

    <!-- 接口权限 -->
    <div v-show="activeTab === 'api'">
      <Space style="margin-bottom: 12px" wrap>
        <Input
          v-model:value="apiSearch"
          placeholder="按路径或名称搜索接口..."
          allow-clear
          style="width: 280px"
        />
        <Button
          :loading="pulling"
          :disabled="checkedMenuIds.size === 0"
          @click="pullApisFromMenus"
        >
          从已选菜单带出接口
        </Button>
      </Space>
      <div style="margin-bottom: 8px; color: #666">
        已选 <strong>{{ checkedApiIds.size }}</strong> 个接口
        <span style="margin-left: 8px; font-size: 12px">
          （带出 = 并入 menu_api 绑定，不取消已选手选）
        </span>
      </div>
      <Collapse :default-active-key="groupedApis.map((g) => g[0])">
        <CollapsePanel v-for="[group, apis] in groupedApis" :key="group">
          <template #header>
            <div class="group-header">
              <span>{{ group }} · {{ apis.length }} 个</span>
              <Checkbox
                :checked="
                  apis.length > 0 && apis.every((a) => checkedApiIds.has(a.id))
                "
                @change="(e: any) => toggleApiGroup(apis, e.target.checked)"
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
              :checked="checkedApiIds.has(a.id)"
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
.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 16px;
}

.menu-tree {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-node {
  padding: 2px 0;
}
</style>
