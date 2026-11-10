/**
 * Web Crypto 加解密工具：RSA-OAEP-SHA256 + AES-256-GCM
 * 协议对齐 Java CryptoService / mock security crypto。
 */

export function arrayBufferToBase64(buffer: ArrayBuffer | Uint8Array) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCodePoint(bytes[i] ?? 0);
  }
  return globalThis.btoa(binary);
}

export function base64ToArrayBuffer(base64: string) {
  const binaryString = globalThis.atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.codePointAt(i) ?? 0;
  }
  return bytes.buffer;
}

export async function generateAesKey() {
  const key = await globalThis.crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt'],
  );
  const keyRaw = await globalThis.crypto.subtle.exportKey('raw', key);
  return {
    key,
    keyBase64: arrayBufferToBase64(keyRaw),
  };
}

export async function aesEncrypt(key: CryptoKey, aad: string, data?: unknown) {
  const encoder = new TextEncoder();
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));

  const plainBytes = data
    ? encoder.encode(JSON.stringify(data))
    : new Uint8Array();

  const encryptedBuffer = await globalThis.crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
      additionalData: encoder.encode(aad),
      tagLength: 128,
    },
    key,
    plainBytes,
  );

  const encrypted = new Uint8Array(encryptedBuffer);
  const tagLength = 16;

  if (encrypted.length < tagLength) {
    throw new Error('encrypted data too short');
  }

  const ciphertext = encrypted.slice(0, encrypted.length - tagLength);
  const tag = encrypted.slice(encrypted.length - tagLength);
  const tagIv = new Uint8Array(tag.length + iv.length);
  tagIv.set(tag, 0);
  tagIv.set(iv, tag.length);

  const toBase64 = (buf: Uint8Array) =>
    globalThis.btoa(String.fromCodePoint(...buf));

  return {
    CiphertextRaw: ciphertext,
    Ciphertext: toBase64(ciphertext),
    TagIvRaw: tagIv,
    TagIv: toBase64(tagIv),
  };
}

/**
 * 解密响应体（combined = ciphertext + tag + iv 的 base64）。
 * 响应 AAD 为空串，对齐 Java EncryptFilter。
 */
export async function aesDecrypt(
  combinedBase64: string,
  key: CryptoKey,
  aad: string,
) {
  const encoder = new TextEncoder();
  const data = new Uint8Array(base64ToArrayBuffer(combinedBase64));
  const ivLength = 12;
  const tagLength = 16;

  if (data.length < ivLength + tagLength) {
    throw new Error('invalid data length');
  }

  const iv = data.slice(data.length - ivLength);
  const sealed = data.slice(0, data.length - ivLength);

  const decrypted = await globalThis.crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv,
      additionalData: encoder.encode(aad),
      tagLength: 128,
    },
    key,
    sealed,
  );

  return new TextDecoder().decode(decrypted);
}

export async function rsaEncrypt(data: string, key: CryptoKey) {
  const encoder = new TextEncoder();
  const encrypted = await globalThis.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    key,
    encoder.encode(data),
  );
  return arrayBufferToBase64(encrypted);
}

/** 按 key 字典序拼接 `k=v&...`，跳过空值。 */
export function uriSort(
  obj: Record<string, unknown>,
  filterFn?: (key: string) => boolean,
): string {
  const fn = filterFn ?? (() => true);

  const keys = Object.keys(obj)
    .filter((key) => {
      const value = obj[key];
      return fn(key) && value !== '' && value !== undefined && value !== null;
    })
    .toSorted();

  return keys.map((key) => `${key}=${String(obj[key])}`).join('&');
}

/** 将 SPKI base64 公钥导入为 RSA-OAEP CryptoKey。 */
export async function importRsaPublicKey(
  publicKeyBase64: string,
): Promise<CryptoKey> {
  const binaryString = globalThis.atob(publicKeyBase64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.codePointAt(i) ?? 0;
  }

  return globalThis.crypto.subtle.importKey(
    'spki',
    bytes.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  );
}
