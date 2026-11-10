/**
 * 将 /menu/all 成功结果与当前 accessToken 绑定后写入 localStorage。
 * 密钥/解密失败时：仅允许「同 token 的缓存」降级；新登录无缓存则禁止进入。
 */

const STORAGE_KEY = 'access-menu-cache';

export interface AccessMenuCachePayload<T = unknown> {
  /** token 指纹（非全文，避免多余落盘） */
  tokenFp: string;
  menus: T[];
  updatedAt: number;
}

function tokenFingerprint(token: string): string {
  // 短指纹足够区分会话；避免完整 token 二次落盘
  if (token.length <= 16) return token;
  return `${token.slice(0, 8)}:${token.slice(-8)}:${token.length}`;
}

function canUseStorage(): boolean {
  return typeof window !== 'undefined' && !!window.localStorage;
}

export function saveAccessMenusCache<T>(token: string, menus: T[]): void {
  if (!canUseStorage() || !token) return;
  try {
    const payload: AccessMenuCachePayload<T> = {
      tokenFp: tokenFingerprint(token),
      menus,
      updatedAt: Date.now(),
    };
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch (error) {
    console.warn('[menu-cache] save failed', error);
  }
}

export function loadAccessMenusCache<T>(token: string): null | T[] {
  if (!canUseStorage() || !token) return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AccessMenuCachePayload<T>;
    if (!parsed || parsed.tokenFp !== tokenFingerprint(token)) {
      return null;
    }
    if (!Array.isArray(parsed.menus)) return null;
    return parsed.menus;
  } catch {
    return null;
  }
}

export function clearAccessMenusCache(): void {
  if (!canUseStorage()) return;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch (error) {
    console.warn('[menu-cache] clear failed', error);
  }
}
