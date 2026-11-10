<script setup lang="ts">
import type { ButtonVariants } from '../../ui';
import type { VbenButtonProps } from './button';

import { computed, useSlots } from 'vue';

import { cn } from '@vben-core/shared/utils';

import { VbenTooltip } from '../tooltip';
import VbenButton from './button.vue';

type IconButtonClickHandler = (event?: MouseEvent) => void;

interface Props extends VbenButtonProps {
  class?: any;
  disabled?: boolean;
  /**
   * 点击回调。DropdownMenuTrigger as-child 会把 reka-ui 的 onClick 合成到此 prop，
   * 必须把原生 MouseEvent 原样转发，否则 event.button 会读到 undefined 并抛错。
   */
  onClick?: IconButtonClickHandler | IconButtonClickHandler[];
  tooltip?: string;
  tooltipDelayDuration?: number;
  tooltipSide?: 'bottom' | 'left' | 'right' | 'top';
  variant?: ButtonVariants['variant'];
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  onClick: () => {},
  tooltipDelayDuration: 200,
  tooltipSide: 'bottom',
  variant: 'ghost',
});

const slots = useSlots();

const showTooltip = computed(() => !!slots.tooltip || !!props.tooltip);

function handleClick(event: MouseEvent) {
  if (Array.isArray(props.onClick)) {
    for (const fn of props.onClick) {
      fn?.(event);
    }
  } else {
    props.onClick?.(event);
  }
}
</script>

<template>
  <VbenButton
    v-if="!showTooltip"
    :class="cn('rounded-full', props.class)"
    :disabled="disabled"
    :variant="variant"
    size="icon"
    @click="handleClick"
  >
    <slot></slot>
  </VbenButton>

  <VbenTooltip
    v-else
    :delay-duration="tooltipDelayDuration"
    :side="tooltipSide"
  >
    <template #trigger>
      <VbenButton
        :class="cn('rounded-full', props.class)"
        :disabled="disabled"
        :variant="variant"
        size="icon"
        @click="handleClick"
      >
        <slot></slot>
      </VbenButton>
    </template>
    <slot v-if="slots.tooltip" name="tooltip"> </slot>
    <template v-else>
      {{ tooltip }}
    </template>
  </VbenTooltip>
</template>
