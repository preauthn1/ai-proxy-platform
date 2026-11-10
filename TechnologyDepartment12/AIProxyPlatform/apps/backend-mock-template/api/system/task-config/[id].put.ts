import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureTemporalTaskSeeds,
  getMockTemporalTaskConfigList,
  isoNow,
  requireAllowedTaskQueue,
  requireAllowedWorkflowType,
} from "~/utils/mock-data";
import { pickTaskCamelKeys, taskCamelToSnakeKey, toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED_KEYS = [
  "code",
  "name",
  "workflowType",
  "taskQueue",
  "cronExpr",
  "retryPolicy",
  "timeoutSeconds",
  "remark",
  "isEnabled",
] as const;

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const list = getMockTemporalTaskConfigList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `task-config ${id} not found`);
  }

  const patch = pickTaskCamelKeys<Record<string, unknown>>(raw, ALLOWED_KEYS);

  if ("code" in patch) {
    const code = String(patch.code ?? "").trim();
    if (!code) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "code cannot be empty");
    }
    if (!CODE_PATTERN.test(code)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "code must match ^[a-z][a-z0-9_]{0,63}$");
    }
    const conflict = list.find((x) => x.id !== id && x.deleted_at === 0 && x.code === code);
    if (conflict) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `code ${code} already exists`);
    }
    patch.code = code;
  }

  if ("name" in patch) {
    const name = String(patch.name ?? "").trim();
    if (!name) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name cannot be empty");
    }
    if (name.length > 128) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name must be ≤ 128 chars");
    }
    patch.name = name;
  }

  if ("workflowType" in patch) {
    const v = String(patch.workflowType ?? "").trim();
    if (!v) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "workflowType cannot be empty");
    }
    const normalized = requireAllowedWorkflowType(v);
    if (!normalized) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `unknown workflowType: ${v}`);
    }
    patch.workflowType = normalized;
  }

  if ("taskQueue" in patch) {
    const v = String(patch.taskQueue ?? "").trim();
    if (!v) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "taskQueue cannot be empty");
    }
    const normalized = requireAllowedTaskQueue(v);
    if (!normalized) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `unknown taskQueue: ${v}`);
    }
    patch.taskQueue = normalized;
  }

  if ("cronExpr" in patch) {
    if (patch.cronExpr === null || patch.cronExpr === "") {
      patch.cronExpr = null;
    } else {
      const c = String(patch.cronExpr).trim();
      if (c.length > 64) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "cronExpr must be ≤ 64 chars");
      }
      patch.cronExpr = c;
    }
  }

  if ("timeoutSeconds" in patch) {
    if (patch.timeoutSeconds === null || patch.timeoutSeconds === "") {
      patch.timeoutSeconds = null;
    } else {
      const t = Number(patch.timeoutSeconds);
      if (!Number.isFinite(t) || t < 0 || !Number.isInteger(t)) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "timeoutSeconds must be a non-negative integer");
      }
      patch.timeoutSeconds = t;
    }
  }

  if ("isEnabled" in patch) {
    const v = Number(patch.isEnabled);
    if (v !== 0 && v !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    patch.isEnabled = v as 0 | 1;
  }

  if ("retryPolicy" in patch) {
    const rp = patch.retryPolicy;
    if (rp === null || rp === undefined || rp === "") {
      patch.retryPolicy = null;
    } else if (typeof rp === "string") {
      try {
        const parsed = JSON.parse(rp) as unknown;
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
          patch.retryPolicy = parsed;
        } else {
          setResponseStatus(event, 400);
          return useResponseError("BadRequest", "retryPolicy must be a JSON object");
        }
      } catch {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "retryPolicy must be valid JSON");
      }
    } else if (typeof rp === "object" && !Array.isArray(rp)) {
      // ok
    } else {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "retryPolicy must be a JSON object");
    }
  }

  if ("remark" in patch) {
    patch.remark = patch.remark != null ? String(patch.remark) : "";
  }

  const snakePatch: Record<string, unknown> = {};
  for (const k of ALLOWED_KEYS) {
    if (k in patch) snakePatch[taskCamelToSnakeKey(k)] = patch[k];
  }

  list[idx] = {
    ...list[idx],
    ...snakePatch,
    updated_at: isoNow(),
    updated_by: 0,
  };
  return useResponseSuccess(toTaskCamelRow(list[idx]));
});
