import { defineEventHandler, getQuery } from "h3";
import { ensureDictSeeds, getMockDictTypeList, type DictType } from "~/utils/mock-data";
import { toDictCamelRow } from "~/utils/dict-camel";
import { usePageResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const query = getQuery(event);
  // axios 1.x 默认把数组序列化为 ?code[]=foo&code[]=bar，h3 的 getQuery 会保留 [] 后缀
  // 兼容两种格式：?code=foo&code=bar 与 ?code[]=foo&code[]=bar
  const code = (query.code ?? query["code[]"]) as string | string[] | undefined;
  const { page = 1, pageSize = 20, name, status } = query;
  const shared = getMockDictTypeList();

  let filtered: DictType[] = shared.filter((x) => x.deleted_at === 0);
  if (Array.isArray(code)) {
    // 多选：精确匹配 code 命中任一
    const codes = new Set((code as unknown[]).map((v) => String(v)));
    if (codes.size > 0) {
      filtered = filtered.filter((x) => codes.has(x.code));
    }
  } else if (code) {
    const q = String(code as string).toLowerCase();
    filtered = filtered.filter((x) => x.code.toLowerCase().includes(q));
  }
  if (name) {
    const q = String(name as string);
    filtered = filtered.filter((x) => x.name.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((x) => x.is_enabled === Number(status));
  }
  // 按 id 升序，便于观察
  filtered.sort((a, b) => a.id - b.id);

  return usePageResponseSuccess(page as string, pageSize as string, filtered.map(toDictCamelRow));
});
