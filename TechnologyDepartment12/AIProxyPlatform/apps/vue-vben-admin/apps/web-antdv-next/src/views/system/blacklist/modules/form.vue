<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type {
  BlacklistScope,
  BlacklistTargetType,
  CreateBlacklistRequest,
  SysBlacklist,
} from '#/api/system/blacklist';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'antdv-next';
import dayjs from 'dayjs';

import { useVbenForm } from '#/adapter/form';
import { createBlacklistApi, updateBlacklistApi } from '#/api/system/blacklist';
import { $t } from '#/locales';
import { useBlacklistFormSchema } from '#/views/system/blacklist/data';

const emits = defineEmits<{
  (e: 'success'): void;
}>();

const id = ref<number | undefined>();
const isEdit = computed(() => !!id.value);
const saving = ref(false);
/** 编辑前 expiresAt 是否非空，用于判断是否需要 clearExpiresAt */
const hadExpiresAt = ref(false);

/** 与 React blacklist / Java LocalDateTime 一致：本地墙钟，无时区后缀 */
const DATETIME_PAYLOAD = 'YYYY-MM-DDTHH:mm:ss';

function toDayjs(value: null | string | undefined): Dayjs | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const d = dayjs(value);
  return d.isValid() ? d : undefined;
}

function toDateTimePayload(value: unknown): null | string {
  if (value === null || value === undefined || value === '') return null;
  if (dayjs.isDayjs(value)) {
    return value.isValid() ? value.format(DATETIME_PAYLOAD) : null;
  }
  if (typeof value === 'string') {
    const d = dayjs(value);
    return d.isValid() ? d.format(DATETIME_PAYLOAD) : null;
  }
  const d = dayjs(value as Date | number | string);
  return d.isValid() ? d.format(DATETIME_PAYLOAD) : null;
}

const [Form, formApi] = useVbenForm({
  schema: useBlacklistFormSchema() ?? [],
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: async () => {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();

    const startsAt = toDateTimePayload(values.startsAt);
    if (!startsAt) {
      message.warning($t('system.blacklist.startsAtRequired'));
      return;
    }
    const expiresAt = toDateTimePayload(values.expiresAt);
    if (expiresAt !== null && !dayjs(expiresAt).isAfter(dayjs(startsAt))) {
      message.warning($t('system.blacklist.expiresAfterStarts'));
      return;
    }

    const body: CreateBlacklistRequest = {
      targetType: values.targetType as BlacklistTargetType,
      targetValue: String(values.targetValue ?? '').trim(),
      scope: (values.scope as BlacklistScope) ?? 'ALL',
      reason: (values.reason as string) ?? '',
      startsAt,
      expiresAt,
      remark: (values.remark as string) ?? '',
      isEnabled: values.isEnabled ? 1 : 0,
    };

    if (!body.targetValue) {
      message.warning($t('system.blacklist.targetValueRequired'));
      return;
    }

    saving.value = true;
    try {
      if (isEdit.value && id.value) {
        await updateBlacklistApi({
          id: id.value,
          data: {
            targetType: body.targetType,
            targetValue: body.targetValue,
            scope: body.scope,
            reason: body.reason,
            startsAt: body.startsAt,
            expiresAt: body.expiresAt ?? undefined,
            // 原先有过期时间、现在清空 → 永久
            clearExpiresAt:
              body.expiresAt === null && hadExpiresAt.value ? true : undefined,
            remark: body.remark,
            isEnabled: body.isEnabled,
          },
        });
        message.success($t('system.blacklist.updateOk'));
      } else {
        await createBlacklistApi(body);
        message.success($t('system.blacklist.createOk'));
      }
      emits('success');
      drawerApi.close();
    } catch (error) {
      message.error(
        (error as Error).message ?? $t('system.blacklist.saveFailed'),
      );
    } finally {
      saving.value = false;
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData<{ row?: SysBlacklist }>();
    const row = data?.row;
    const editing = !!row;

    formApi.resetForm();
    await nextTick();

    if (editing && row) {
      id.value = row.id;
      hadExpiresAt.value =
        row.expiresAt !== null &&
        row.expiresAt !== undefined &&
        row.expiresAt !== '';
      await formApi.setValues(
        {
          targetType: row.targetType,
          targetValue: row.targetValue,
          scope: row.scope,
          startsAt: toDayjs(row.startsAt),
          expiresAt: toDayjs(row.expiresAt),
          reason: row.reason ?? '',
          remark: row.remark ?? '',
          isEnabled: row.isEnabled === 1,
        },
        false,
      );
    } else {
      id.value = undefined;
      hadExpiresAt.value = false;
      await formApi.setValues(
        {
          targetType: 'IP',
          targetValue: '',
          scope: 'ALL',
          startsAt: dayjs(),
          expiresAt: undefined,
          reason: '',
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
  <Drawer
    :title="
      isEdit ? $t('system.blacklist.edit') : $t('system.blacklist.create')
    "
    :confirm-loading="saving"
  >
    <Form />
  </Drawer>
</template>
