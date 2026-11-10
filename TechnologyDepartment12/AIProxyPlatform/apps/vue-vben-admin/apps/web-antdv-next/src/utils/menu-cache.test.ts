import { beforeEach, describe, expect, it } from 'vitest';

import {
  clearAccessMenusCache,
  loadAccessMenusCache,
  saveAccessMenusCache,
} from './menu-cache';

describe('access menu cache', () => {
  beforeEach(() => {
    clearAccessMenusCache();
  });

  it('saves and loads menus for the same token', () => {
    const token = 'tok-aaaaaaaa-bbbbbbbb';
    const menus = [{ name: 'Dashboard', path: '/dashboard' }];
    saveAccessMenusCache(token, menus);
    expect(loadAccessMenusCache(token)).toEqual(menus);
  });

  it('misses when token fingerprint differs (new login)', () => {
    saveAccessMenusCache('tok-old-old-old-old', [{ name: 'A', path: '/a' }]);
    expect(loadAccessMenusCache('tok-new-new-new-new')).toBeNull();
  });

  it('clears on logout', () => {
    const token = 'tok-cccccccc-dddddddd';
    saveAccessMenusCache(token, [{ name: 'X', path: '/x' }]);
    clearAccessMenusCache();
    expect(loadAccessMenusCache(token)).toBeNull();
  });
});
