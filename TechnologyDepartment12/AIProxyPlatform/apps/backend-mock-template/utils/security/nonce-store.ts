/**
 * 内存 Nonce 存储：TTL 内同一 X-Request-ID 第二次 tryAcquire 返回 false。
 */

export interface NonceStore {
  tryAcquire(nonce: string, ttlMs: number): boolean;
  clear(): void;
}

export class MemoryNonceStore implements NonceStore {
  private readonly map = new Map<string, number>();

  tryAcquire(nonce: string, ttlMs: number): boolean {
    this.evictExpired();
    const now = Date.now();
    const existing = this.map.get(nonce);
    if (existing !== undefined && existing > now) {
      return false;
    }
    this.map.set(nonce, now + Math.max(1, ttlMs));
    return true;
  }

  clear(): void {
    this.map.clear();
  }

  private evictExpired(): void {
    const now = Date.now();
    for (const [k, exp] of this.map) {
      if (exp <= now) this.map.delete(k);
    }
  }
}

/** mock 进程内单例。 */
export const globalNonceStore = new MemoryNonceStore();
