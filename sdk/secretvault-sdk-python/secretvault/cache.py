"""In-memory thread-safe TTL Cache for SecretVault Python SDK."""
import time
import threading
from typing import Optional, Dict, Any

class SecretCache:
    def __init__(self, enabled: bool = True, ttl_sec: float = 60.0, max_stale_sec: float = 600.0, max_entries: int = 500):
        self.enabled = enabled
        self.ttl_sec = ttl_sec
        self.max_stale_sec = max_stale_sec
        self.max_entries = max_entries
        self._store: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.Lock()

    def get(self, key: str) -> Optional[Dict[str, Any]]:
        if not self.enabled:
            return None
        with self._lock:
            entry = self._store.get(key)
            if not entry:
                return None
            now = time.time()
            elapsed = now - entry["cached_at"]
            if elapsed <= self.ttl_sec:
                return {"value": entry["value"], "is_stale": False}
            if elapsed <= (self.ttl_sec + self.max_stale_sec):
                return {"value": entry["value"], "is_stale": True}
            del self._store[key]
            return None

    def set(self, key: str, value: Any):
        if not self.enabled:
            return
        with self._lock:
            if len(self._store) >= self.max_entries and key not in self._store:
                oldest_key = next(iter(self._store))
                del self._store[oldest_key]
            self._store[key] = {
                "value": value,
                "cached_at": time.time()
            }

    def invalidate(self, key: str):
        with self._lock:
            self._store.pop(key, None)

    def clear(self):
        with self._lock:
            self._store.clear()
