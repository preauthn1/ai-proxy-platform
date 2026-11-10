import { initPreferences, updatePreferences } from '@vben/preferences';
import { unmountGlobalLoading } from '@vben/utils';

import { overridesPreferences } from './preferences';

// 注册 ALTCHA PoW widget 自定义元素（<altcha-widget>），登录页人机校验
import 'altcha';

/** 部署级菜单模式：以 VITE_ACCESS_MODE 为准，覆盖 localStorage 历史值 */
function applyEnvAccessMode() {
  const mode = import.meta.env.VITE_ACCESS_MODE;
  if (mode === 'backend' || mode === 'frontend' || mode === 'mixed') {
    updatePreferences({ app: { accessMode: mode } });
  }
}

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });
  // 缓存合并后强制写回 env 的 accessMode，避免历史 frontend 导致永不请求 /menu/all
  applyEnvAccessMode();

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
