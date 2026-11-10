import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone.js';
import utc from 'dayjs/plugin/utc.js';

dayjs.extend(utc);
dayjs.extend(timezone);

/** 平台墙钟；无 offset 的 API 字符串按此时区解析。 */
export const PLATFORM_TIMEZONE = 'Asia/Shanghai';

type FormatDate = Date | dayjs.Dayjs | number | string;

type Format =
  | 'HH'
  | 'HH:mm'
  | 'HH:mm:ss'
  | 'YYYY'
  | 'YYYY-MM'
  | 'YYYY-MM-DD'
  | 'YYYY-MM-DD HH'
  | 'YYYY-MM-DD HH:mm'
  | 'YYYY-MM-DD HH:mm:ss'
  | (string & {});

export function formatDate(time?: FormatDate, format: Format = 'YYYY-MM-DD') {
  if (time === undefined || time === null || time === '') {
    return '';
  }
  try {
    const date = toDisplayDayjs(time);
    if (!date.isValid()) {
      throw new Error('Invalid date');
    }
    return date.format(format);
  } catch (error) {
    console.error(`Error formatting date: ${error}`);
    return String(time ?? '');
  }
}

export function formatDateTime(time?: FormatDate) {
  return formatDate(time, 'YYYY-MM-DD HH:mm:ss');
}

function hasExplicitOffset(raw: string) {
  const value = raw.trim();
  return /Z$/i.test(value) || /[+-]\d{2}:\d{2}$/.test(value);
}

/**
 * 有 offset / Instant 按物理时刻解析；无 offset 字符串当上海墙钟，再转到当前展示时区。
 */
function toDisplayDayjs(time: FormatDate) {
  if (dayjs.isDayjs(time)) {
    return time.tz();
  }
  if (typeof time === 'number' || time instanceof Date) {
    return dayjs(time).tz();
  }
  const raw = String(time);
  if (hasExplicitOffset(raw)) {
    return dayjs(raw).tz();
  }
  return dayjs.tz(raw, PLATFORM_TIMEZONE).tz();
}

export function isDate(value: any): value is Date {
  return value instanceof Date;
}

export function isDayjsObject(value: any): value is dayjs.Dayjs {
  return dayjs.isDayjs(value);
}

/**
 * 获取当前时区
 * @returns 当前时区
 */
export const getSystemTimezone = () => {
  return dayjs.tz.guess();
};

/**
 * 自定义设置的时区
 */
let currentTimezone = getSystemTimezone();

/**
 * 设置默认时区
 * @param timezone
 */
export const setCurrentTimezone = (timezone?: string) => {
  currentTimezone = timezone || getSystemTimezone();
  dayjs.tz.setDefault(currentTimezone);
};

/**
 * 获取设置的时区
 * @returns 设置的时区
 */
export const getCurrentTimezone = () => {
  return currentTimezone;
};
