export class SecretCache {
  constructor(options = {}) {
    this.enabled = options.enabled !== false;
    this.ttlMs = options.ttlMs || (options.ttlSec ? options.ttlSec * 1000 : 60000); // 60s default
    this.maxStaleMs = options.maxStaleMs || 600000; // 10 minutes default
    this.maxEntries = options.maxEntries || 500;
    this.store = new Map();
  }

  get(key) {
    if (!this.enabled) return null;
    const entry = this.store.get(key);
    if (!entry) return null;

    const now = Date.now();
    const isExpired = (now - entry.cachedAt) > this.ttlMs;

    if (!isExpired) {
      return { value: entry.value, isStale: false };
    }

    const isWithinMaxStale = (now - entry.cachedAt) <= (this.ttlMs + this.maxStaleMs);
    if (isWithinMaxStale) {
      return { value: entry.value, isStale: true };
    }

    this.store.delete(key);
    return null;
  }

  set(key, value) {
    if (!this.enabled) return;
    if (this.store.size >= this.maxEntries && !this.store.has(key)) {
      const oldestKey = this.store.keys().next().value;
      this.store.delete(oldestKey);
    }
    this.store.set(key, {
      value,
      cachedAt: Date.now()
    });
  }

  invalidate(key) {
    this.store.delete(key);
  }

  clear() {
    this.store.clear();
  }
}
