import type { EventHandlerRequest, H3Event } from "h3";

import { setResponseStatus } from "h3";

/**
 * 统一成功响应：与 java-admin {@code Result} 对齐 — {@code code/msg/data}。
 */
export function useResponseSuccess<T = any>(data: T) {
  return {
    code: 0,
    msg: "ok",
    data,
  };
}

export function usePageResponseSuccess<T = any>(
  page: number | string,
  pageSize: number | string,
  list: T[],
  { message = "ok" } = {},
) {
  const pageData = pagination(Number.parseInt(`${page}`), Number.parseInt(`${pageSize}`), list);

  return {
    code: 0,
    msg: message,
    data: {
      items: pageData,
      total: list.length,
    },
  };
}

/**
 * 统一错误响应：{@code { code, msg, data: null }}。
 *
 * <p>兼容两种调用：
 * <ul>
 *   <li>{@code useResponseError("详情")} 或 {@code useResponseError("详情", 2002)}</li>
 *   <li>旧式 {@code useResponseError("BadRequest", "详情")} — 取第二参字符串为 msg</li>
 * </ul>
 */
export function useResponseError(msgOrType: string, detailOrCode: string | number | null = null) {
  if (typeof detailOrCode === "number") {
    return {
      code: detailOrCode,
      msg: msgOrType,
      data: null,
    };
  }
  if (typeof detailOrCode === "string" && detailOrCode) {
    return {
      code: -1,
      msg: detailOrCode,
      data: null,
    };
  }
  return {
    code: -1,
    msg: msgOrType,
    data: null,
  };
}

export function forbiddenResponse(
  event: H3Event<EventHandlerRequest>,
  message = "Forbidden Exception",
) {
  setResponseStatus(event, 403);
  return useResponseError(message, 2004);
}

export function unAuthorizedResponse(event: H3Event<EventHandlerRequest>) {
  setResponseStatus(event, 401);
  return useResponseError("Unauthorized Exception", 2001);
}

export function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function pagination<T = any>(pageNo: number, pageSize: number, array: T[]): T[] {
  const offset = (pageNo - 1) * Number(pageSize);
  return offset + Number(pageSize) >= array.length
    ? array.slice(offset)
    : array.slice(offset, offset + Number(pageSize));
}
