/**
 * S4：Mock 黑名单 CRUD 响应形状 + middleware 拦截路径。
 */

import { afterEach, beforeEach, describe, expect, it } from "vite-plus/test";

import { toBlacklistCamelRow } from "../utils/blacklist-camel";
import {
  ACCESS_BLOCKED_CODE,
  ACCESS_BLOCKED_MSG,
  accessBlockedBody,
  batchBlacklist,
  createBlacklist,
  evaluateRequestBlacklist,
  findBlockingHit,
  listBlacklist,
  resetBlacklistForTest,
  softDeleteBlacklist,
} from "../utils/mock/blacklist";
import { usePageResponseSuccess, useResponseSuccess } from "../utils/response";

describe("mock blacklist seam S4", () => {
  beforeEach(() => {
    resetBlacklistForTest();
  });

  afterEach(() => {
    resetBlacklistForTest();
  });

  it("CRUD list 形状为 { code, msg, data: { items, total } } 且 VO 为 camelCase", () => {
    const created = createBlacklist({
      targetType: "IP",
      targetValue: "10.0.0.8",
      scope: "API",
      reason: "scan",
      remark: "internal",
      isEnabled: 1,
    });
    expect(created.ok).toBe(true);
    if (!created.ok) return;

    const rows = listBlacklist({ targetType: "IP", status: 1 });
    expect(rows.length).toBeGreaterThanOrEqual(1);

    const page = usePageResponseSuccess(1, 20, rows.map(toBlacklistCamelRow));
    expect(page).toEqual({
      code: 0,
      msg: "ok",
      data: {
        items: expect.any(Array),
        total: rows.length,
      },
    });
    const item = page.data.items[0] as Record<string, unknown>;
    expect(item).toMatchObject({
      targetType: "IP",
      targetValue: "10.0.0.8",
      scope: "API",
      reason: "scan",
      isEnabled: 1,
    });
    expect(item).not.toHaveProperty("target_type");
    expect(item).not.toHaveProperty("is_enabled");

    const detail = useResponseSuccess(toBlacklistCamelRow(created.data));
    expect(detail.code).toBe(0);
    expect(detail.data).toMatchObject({ id: created.data.id, targetType: "IP" });
  });

  it("同窗重复创建被拒；重叠时间窗可建", () => {
    const startsAt = "2026-01-01T00:00:00.000Z";
    const expiresAt = "2026-12-31T00:00:00.000Z";
    const a = createBlacklist({
      targetType: "USER",
      targetValue: "42",
      scope: "ALL",
      startsAt,
      expiresAt,
    });
    expect(a.ok).toBe(true);

    const dup = createBlacklist({
      targetType: "USER",
      targetValue: "42",
      scope: "ALL",
      startsAt,
      expiresAt,
    });
    expect(dup.ok).toBe(false);
    if (!dup.ok) {
      expect(dup.msg).toMatch(/same target\/scope\/time-window/i);
    }

    const overlap = createBlacklist({
      targetType: "USER",
      targetValue: "42",
      scope: "ALL",
      startsAt: "2026-06-01T00:00:00.000Z",
      expiresAt: "2027-01-01T00:00:00.000Z",
    });
    expect(overlap.ok).toBe(true);
  });

  it("batch enable|disable|delete 返回 { action, affected, ids }", () => {
    const a = createBlacklist({ targetType: "DEVICE", targetValue: "dev-1", scope: "API" });
    const b = createBlacklist({ targetType: "DEVICE", targetValue: "dev-2", scope: "API" });
    expect(a.ok && b.ok).toBe(true);
    if (!a.ok || !b.ok) return;

    const disabled = batchBlacklist("disable", [a.data.id, b.data.id]);
    expect(disabled.ok).toBe(true);
    if (!disabled.ok) return;
    expect(disabled.data).toEqual({
      action: "disable",
      affected: 2,
      ids: expect.arrayContaining([a.data.id, b.data.id]),
    });

    const deleted = batchBlacklist("delete", [a.data.id]);
    expect(deleted.ok).toBe(true);
    if (!deleted.ok) return;
    expect(deleted.data.action).toBe("delete");
    expect(deleted.data.affected).toBe(1);

    const gone = softDeleteBlacklist(b.data.id);
    expect(gone.ok).toBe(true);
  });

  it("拦截路径：LOGIN IP 命中 → Access Blocked 固定文案且不带 reason", () => {
    const blockedIp = "198.51.100.9";
    createBlacklist({
      targetType: "IP",
      targetValue: blockedIp,
      scope: "LOGIN",
      reason: "secret-should-not-leak",
      isEnabled: 1,
      startsAt: "2020-01-01T00:00:00.000Z",
    });

    const hit = findBlockingHit("IP", blockedIp, "LOGIN");
    expect(hit).not.toBeNull();
    expect(hit?.reason).toBe("secret-should-not-leak");

    const evaluated = evaluateRequestBlacklist({
      path: "/api/auth/login",
      clientIp: blockedIp,
      userId: 1,
    });
    expect(evaluated).not.toBeNull();
    expect(evaluated?.targetType).toBe("IP");

    // LOGIN 场景 Filter 不查 USER（即使 userId 传入也不应因 USER 误拦登录入口 IP 路径语义）
    // 此处仅验证 IP 命中；响应体不含 reason
    const body = accessBlockedBody();
    expect(body).toEqual({
      code: ACCESS_BLOCKED_CODE,
      msg: ACCESS_BLOCKED_MSG,
      data: null,
    });
    expect(JSON.stringify(body)).not.toContain("secret-should-not-leak");
  });

  it("拦截路径：API session USER 命中；DEVICE 运行时不生效", () => {
    createBlacklist({
      targetType: "USER",
      targetValue: "99",
      scope: "ALL",
      reason: "ban-user",
      isEnabled: 1,
      startsAt: "2020-01-01T00:00:00.000Z",
    });
    createBlacklist({
      targetType: "DEVICE",
      targetValue: "device-x",
      scope: "ALL",
      reason: "device-only-config",
      isEnabled: 1,
      startsAt: "2020-01-01T00:00:00.000Z",
    });

    const apiHit = evaluateRequestBlacklist({
      path: "/api/system/user/list",
      clientIp: "127.0.0.1",
      userId: 99,
    });
    expect(apiHit).not.toBeNull();
    expect(apiHit?.targetType).toBe("USER");
    expect(apiHit?.targetValue).toBe("99");

    // DEVICE 永不命中
    expect(findBlockingHit("DEVICE", "device-x", "API")).toBeNull();

    // 禁用 / 软删后不命中
    const row = createBlacklist({
      targetType: "IP",
      targetValue: "10.10.10.10",
      scope: "API",
      isEnabled: 1,
      startsAt: "2020-01-01T00:00:00.000Z",
    });
    expect(row.ok).toBe(true);
    if (!row.ok) return;
    batchBlacklist("disable", [row.data.id]);
    expect(findBlockingHit("IP", "10.10.10.10", "API")).toBeNull();
  });
});
