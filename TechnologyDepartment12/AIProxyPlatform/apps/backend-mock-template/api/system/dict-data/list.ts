import { defineEventHandler, getQuery } from "h3";
import {
  ensureDictSeeds,
  getMockDictDataList,
  getMockDictTypeList,
  type DictData,
} from "~/utils/mock-data";
import { toDictCamelRow } from "~/utils/dict-camel";
import { usePageResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const rawQuery = getQuery(event);
  // 兼容 ?typeCode=foo&typeCode=bar 与 ?typeCode[]=foo&typeCode[]=bar
  const typeCode = (rawQuery.typeCode ?? rawQuery["typeCode[]"]) as string | string[] | undefined;
  const { page = 1, pageSize = 20, typeId, label, value, status, platform } = rawQuery;
  // includeGeneral：boolean；兼容 "1"/"true"/true，传错（如 "yes"）按 false 处理
  const includeGeneralRaw = rawQuery.includeGeneral;
  const includeGeneral =
    includeGeneralRaw === true || includeGeneralRaw === "1" || includeGeneralRaw === "true";
  let filtered: DictData[] = getMockDictDataList().filter((x) => x.deleted_at === 0);

  if (typeId !== undefined && typeId !== "") {
    const t = Number(typeId);
    if (Number.isFinite(t)) {
      filtered = filtered.filter((x) => x.type_id === t);
    }
  }
  if (Array.isArray(typeCode)) {
    // 多选：空数组=无命中；非空数组=精确匹配任一
    if (typeCode.length === 0) {
      filtered = [];
    } else {
      const codes = new Set((typeCode as unknown[]).map((v) => String(v)));
      const typeIds = new Set(
        getMockDictTypeList()
          .filter((x) => x.deleted_at === 0 && codes.has(x.code))
          .map((x) => x.id),
      );
      filtered = filtered.filter((x) => typeIds.has(x.type_id));
    }
  } else if (typeCode) {
    const q = String(typeCode as string);
    const typeIds = new Set(
      getMockDictTypeList()
        .filter((x) => x.deleted_at === 0 && x.code.includes(q))
        .map((x) => x.id),
    );
    filtered = filtered.filter((x) => typeIds.has(x.type_id));
  }
  if (label) {
    const q = String(label as string);
    filtered = filtered.filter((x) => x.label.includes(q));
  }
  if (value) {
    const q = String(value as string);
    filtered = filtered.filter((x) => x.value.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((x) => x.is_enabled === Number(status));
  }
  // platform 过滤：精确匹配；不带参数时返回全部（向后兼容旧调用）
  // includeGeneral=true 且 platform 非 general：把 general 并入过滤集合
  if (typeof platform === "string" && platform !== "") {
    if (platform === "general" || !includeGeneral) {
      filtered = filtered.filter((x) => x.platform === platform);
    } else {
      filtered = filtered.filter((x) => x.platform === platform || x.platform === "general");
    }
  }
  filtered.sort((a, b) => a.sort - b.sort || a.id - b.id);

  // join typeCode（仅 list 返回）
  const typeMap = new Map(getMockDictTypeList().map((t) => [t.id, t.code]));
  const items = filtered.map((x) =>
    toDictCamelRow({ ...x, typeCode: typeMap.get(x.type_id) ?? "" }),
  );

  return usePageResponseSuccess(page as string, pageSize as string, items);
});
