<script lang="ts" setup>
/**
 * ALTCHA PoW widget 的 Vue 薄封装。
 *
 * `<altcha-widget>` 是 altcha 包注册的 Web Component。为避免 Vue 模板编译器
 * 对自定义元素产生告警（共享 vite-config 未暴露 isCustomElement 注入点），
 * 这里在 onMounted 用 document.createElement 挂载，纯 DOM 交互。
 *
 * 用法（VbenForm schema 自定义控件）：
 *   component: markRaw(AltchaWidget),
 *   componentProps: { challenge: 可选，默认拼接 apiURL + /altcha/challenge },
 *   rules: z.string().min(1, ...)
 *
 * 登录失败后可调用 expose 的 reset()，清空勾选并重新拉取 challenge。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';

interface Props {
  challenge?: string;
  language?: string;
  /** 受控值（Base64 payload），由 VbenForm model 注入 */
  modelValue?: string;
}

const props = withDefaults(defineProps<Props>(), {
  challenge: '',
  language: 'zh',
  modelValue: '',
});

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
function resolveChallengeUrl() {
  if (props.challenge) return props.challenge;
  return `${String(apiURL || '/api').replace(/\/$/, '')}/altcha/challenge`;
}
const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const host = ref<HTMLDivElement>();
let widget:
  | (HTMLElement & {
      reset?: (newState?: string, err?: null | string) => void;
    })
  | null = null;

type AltchaState =
  | 'code'
  | 'error'
  | 'expired'
  | 'unverified'
  | 'verified'
  | 'verifying';

function onStateChange(ev: Event) {
  const detail = (ev as CustomEvent<{ payload?: string; state: AltchaState }>)
    .detail;
  // widget 在 verified 时会把 Base64 payload 写入内部隐藏 input；从 DOM 取最稳
  const payload =
    detail?.payload ??
    (widget?.querySelector('input[type="hidden"]') as HTMLInputElement | null)
      ?.value ??
    '';
  if (detail?.state === 'verified' && payload) {
    emit('update:modelValue', payload);
  } else if (detail?.state !== 'verified' && props.modelValue !== '') {
    // 重新校验或失败时清空，防止提交旧 payload
    emit('update:modelValue', '');
  }
}

/** 重置为未验证：清空表单字段并让 widget 重新拉 challenge */
function reset() {
  emit('update:modelValue', '');
  widget?.reset?.('unverified');
}

defineExpose({ reset });

onMounted(() => {
  const el = host.value;
  if (!el) return;
  widget = document.createElement('altcha-widget') as NonNullable<
    typeof widget
  >;
  widget.setAttribute('language', props.language);
  widget.setAttribute('challenge', resolveChallengeUrl());
  // hideLogo/hideFooter 不是 HTML 属性，需走 configuration JSON
  // @see altcha create_custom_element props: configuration only
  widget.setAttribute(
    'configuration',
    JSON.stringify({ hideLogo: true, hideFooter: true }),
  );
  widget.addEventListener('statechange', onStateChange);
  el.append(widget);
});

onBeforeUnmount(() => {
  if (widget) {
    widget.removeEventListener('statechange', onStateChange);
    widget.remove();
    widget = null;
  }
});
</script>

<template>
  <div ref="host" class="altcha-widget-host"></div>
</template>

<style scoped>
/* 与上方密码/账号输入框同宽对齐；覆盖 ALTCHA 默认 max-width: 320px */
.altcha-widget-host {
  --altcha-max-width: 100%;

  display: block;
  width: 100%;
}

.altcha-widget-host :deep(altcha-widget) {
  display: block;
  width: 100%;
}

.altcha-widget-host :deep(.altcha),
.altcha-widget-host :deep(.altcha-main) {
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
}
</style>
