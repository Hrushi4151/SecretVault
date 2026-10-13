"""SecretVault Python Client."""
import os
import json
import re
import time
import threading
import urllib.request
import urllib.error
from typing import Optional, List, Dict, Any, Callable

from .models import SecretValue, SecretMetadata
from .cache import SecretCache
from .errors import SecretVaultError, SecretNotFoundError, AuthenticationError

UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.I)

class _SecretsApi:
    """Namespace for secret operations."""
    def __init__(self, client: "SecretVaultClient"):
        self._c = client

    def get(self, name: str, version: Optional[int] = None, **kwargs) -> SecretValue:
        return self._c.get_secret(name, version, **kwargs)

    def get_many(self, names: List[str], **kwargs) -> Dict[str, Optional[SecretValue]]:
        return self._c.get_many_secrets(names, **kwargs)

    def list(self, search: Optional[str] = None, **kwargs) -> List[SecretMetadata]:
        return self._c.list_secrets(search=search, **kwargs)

    def create(self, name: str, value: str, description: str = "", **kwargs) -> Dict[str, Any]:
        return self._c.create_secret(name, value, description, **kwargs)

    def update(self, name: str, value: str, description: Optional[str] = None, reason: str = "SDK update", **kwargs) -> Dict[str, Any]:
        return self._c.update_secret(name, value, description, reason, **kwargs)

    def delete(self, name: str, **kwargs) -> bool:
        return self._c.delete_secret(name, **kwargs)

    def watch(self, name: str, callback: Callable[[SecretValue], None], poll_interval_sec: float = 15.0) -> Callable[[], None]:
        return self._c.watch_secret(name, callback, poll_interval_sec)


class SecretVaultClient:
    """Official SecretVault Client for Python."""

    def __init__(
        self,
        endpoint: Optional[str] = None,
        workspace: Optional[str] = None,
        project: Optional[str] = None,
        environment: Optional[str] = None,
        token: Optional[str] = None,
        cache_ttl_sec: float = 60.0,
        fail_open_stale: bool = True,
        timeout_sec: float = 5.0,
    ):
        self.endpoint = (endpoint or os.getenv("SECRETVault_ENDPOINT", "http://localhost:8080")).rstrip("/")
        self.workspace = workspace or os.getenv("SECRETVault_WORKSPACE", "default")
        self.project = project or os.getenv("SECRETVault_PROJECT", "")
        self.environment = environment or os.getenv("SECRETVault_ENVIRONMENT", "production")
        self.token = token or os.getenv("SECRETVault_TOKEN", "")
        self.timeout_sec = timeout_sec
        self.fail_open_stale = fail_open_stale

        self.cache = SecretCache(ttl_sec=cache_ttl_sec)
        self._id_cache: Dict[str, str] = {}
        self.secrets = _SecretsApi(self)

    @classmethod
    def create(cls, **kwargs):
        return cls(**kwargs)

    def _is_uuid(self, val: str) -> bool:
        return bool(UUID_PATTERN.match(val))

    def _request(self, method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Any:
        url = f"{self.endpoint}{path}"
        headers = {
            "Accept": "application/json",
            "User-Agent": "SecretVault-SDK-Python/1.0.0"
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_sec) as resp:
                raw_body = resp.read().decode("utf-8")
                if not raw_body:
                    return None
                try:
                    payload = json.loads(raw_body)
                except Exception:
                    return raw_body

                if isinstance(payload, dict) and "data" in payload and "success" in payload:
                    return payload["data"]
                return payload
        except urllib.error.HTTPError as e:
            raw_err = e.read().decode("utf-8")
            err_json = None
            try:
                err_json = json.loads(raw_err)
            except Exception:
                pass

            msg = err_json.get("message") if isinstance(err_json, dict) else str(e)
            if e.code in (401, 403):
                raise AuthenticationError(msg or "Unauthorized token")
            if e.code == 404:
                raise SecretNotFoundError(path)
            raise SecretVaultError(msg or str(e), status_code=e.code, details=err_json)
        except urllib.error.URLError as e:
            raise SecretVaultError(f"Network error connecting to SecretVault: {e.reason}", 503)

    # --- Slug to UUID Resolvers ---
    def resolve_workspace_id(self, slug_or_id: Optional[str] = None) -> str:
        target = slug_or_id or self.workspace
        if self._is_uuid(target):
            return target
        cache_key = f"ws::{target}"
        if cache_key in self._id_cache:
            return self._id_cache[cache_key]

        data = self._request("GET", "/api/v1/workspaces")
        items = data if isinstance(data, list) else (data.get("content", []) if isinstance(data, dict) else [])
        for ws in items:
            if ws.get("slug") == target or ws.get("name") == target or ws.get("id") == target:
                ws_id = ws["id"]
                self._id_cache[cache_key] = ws_id
                return ws_id
        raise SecretVaultError(f"Workspace '{target}' not found", 404)

    def resolve_project_id(self, workspace_id: str, slug_or_id: Optional[str] = None) -> str:
        target = slug_or_id or self.project
        if self._is_uuid(target):
            return target
        cache_key = f"proj::{workspace_id}::{target}"
        if cache_key in self._id_cache:
            return self._id_cache[cache_key]

        data = self._request("GET", f"/api/v1/workspaces/{workspace_id}/projects")
        items = data if isinstance(data, list) else (data.get("content", []) if isinstance(data, dict) else [])
        for p in items:
            if p.get("slug") == target or p.get("name") == target or p.get("id") == target:
                p_id = p["id"]
                self._id_cache[cache_key] = p_id
                return p_id
        raise SecretVaultError(f"Project '{target}' not found", 404)

    def resolve_environment_id(self, workspace_id: str, project_id: str, slug_or_id: Optional[str] = None) -> str:
        target = slug_or_id or self.environment
        if self._is_uuid(target):
            return target
        cache_key = f"env::{workspace_id}::{project_id}::{target}"
        if cache_key in self._id_cache:
            return self._id_cache[cache_key]

        data = self._request("GET", f"/api/v1/workspaces/{workspace_id}/projects/{project_id}/environments")
        items = data if isinstance(data, list) else (data.get("content", []) if isinstance(data, dict) else [])
        for env in items:
            if env.get("slug") == target or env.get("name") == target or env.get("id") == target or env.get("environmentType") == target:
                env_id = env["id"]
                self._id_cache[cache_key] = env_id
                return env_id
        raise SecretVaultError(f"Environment '{target}' not found", 404)

    def resolve_secret_id(self, workspace_id: str, project_id: str, env_id: str, name_or_id: str) -> str:
        if self._is_uuid(name_or_id):
            return name_or_id
        secrets = self.list_secrets(workspace_id=workspace_id, project_id=project_id, env_id=env_id, search=name_or_id)
        for s in secrets:
            if s.name.lower() == name_or_id.lower() or s.id == name_or_id:
                return s.id
        raise SecretNotFoundError(name_or_id)

    def _resolve_context(self, kwargs: Dict[str, Any]):
        ws_id = self.resolve_workspace_id(kwargs.get("workspace"))
        proj_id = self.resolve_project_id(ws_id, kwargs.get("project"))
        env_id = self.resolve_environment_id(ws_id, proj_id, kwargs.get("environment"))
        return ws_id, proj_id, env_id

    # --- Health ---
    def ping(self) -> bool:
        try:
            res = self._request("GET", "/api/v1/auth/whoami")
            return res is not None
        except Exception:
            return False

    # --- Secrets Operations ---
    def get_secret(self, name: str, version: Optional[int] = None, **kwargs) -> SecretValue:
        cache_key = f"{kwargs.get('project', self.project)}::{kwargs.get('environment', self.environment)}::{name}::{version or 'latest'}"
        cached = self.cache.get(cache_key)
        if cached and not cached["is_stale"]:
            return cached["value"]

        try:
            ws_id, proj_id, env_id = self._resolve_context(kwargs)
            secret_id = self.resolve_secret_id(ws_id, proj_id, env_id, name)
            qs = f"?version={version}" if version else ""
            data = self._request("POST", f"/api/v1/workspaces/{ws_id}/projects/{proj_id}/environments/{env_id}/secrets/{secret_id}/reveal{qs}")

            secret_val = SecretValue(
                name=data.get("name", name),
                value=data.get("value", ""),
                version_number=data.get("versionNumber", version or 1),
                environment=data.get("environmentSlug", self.environment)
            )
            self.cache.set(cache_key, secret_val)
            return secret_val
        except Exception as e:
            if cached and self.fail_open_stale:
                return cached["value"]
            raise e

    def get_many_secrets(self, names: List[str], **kwargs) -> Dict[str, Optional[SecretValue]]:
        res = {}
        for n in names:
            try:
                res[n] = self.get_secret(n, **kwargs)
            except Exception:
                res[n] = None
        return res

    def list_secrets(self, search: Optional[str] = None, **kwargs) -> List[SecretMetadata]:
        ws_id = kwargs.get("workspace_id") or self.resolve_workspace_id(kwargs.get("workspace"))
        proj_id = kwargs.get("project_id") or self.resolve_project_id(ws_id, kwargs.get("project"))
        env_id = kwargs.get("env_id") or self.resolve_environment_id(ws_id, proj_id, kwargs.get("environment"))

        path = f"/api/v1/workspaces/{ws_id}/projects/{proj_id}/environments/{env_id}/secrets"
        if search:
            path += f"?search={search}"

        data = self._request("GET", path)
        items = data if isinstance(data, list) else (data.get("content", []) if isinstance(data, dict) else [])
        results = []
        for s in items:
            results.append(SecretMetadata(
                id=s["id"],
                name=s["name"],
                current_version=s.get("currentVersion", 1),
                status=s.get("status", "ACTIVE"),
                description=s.get("description"),
                environment_id=s.get("environmentId", env_id)
            ))
        return results

    def create_secret(self, name: str, value: str, description: str = "", **kwargs) -> Dict[str, Any]:
        ws_id, proj_id, env_id = self._resolve_context(kwargs)
        body = {"name": name, "value": value, "description": description}
        return self._request("POST", f"/api/v1/workspaces/{ws_id}/projects/{proj_id}/environments/{env_id}/secrets", body)

    def update_secret(self, name: str, value: str, description: Optional[str] = None, reason: str = "SDK update", **kwargs) -> Dict[str, Any]:
        ws_id, proj_id, env_id = self._resolve_context(kwargs)
        secret_id = self.resolve_secret_id(ws_id, proj_id, env_id, name)
        body = {"value": value, "reason": reason}
        if description is not None:
            body["description"] = description
        res = self._request("PATCH", f"/api/v1/workspaces/{ws_id}/projects/{proj_id}/environments/{env_id}/secrets/{secret_id}", body)
        self.cache.invalidate(f"{kwargs.get('project', self.project)}::{kwargs.get('environment', self.environment)}::{name}::latest")
        return res

    def delete_secret(self, name: str, **kwargs) -> bool:
        ws_id, proj_id, env_id = self._resolve_context(kwargs)
        secret_id = self.resolve_secret_id(ws_id, proj_id, env_id, name)
        self._request("DELETE", f"/api/v1/workspaces/{ws_id}/projects/{proj_id}/environments/{env_id}/secrets/{secret_id}")
        self.cache.invalidate(f"{kwargs.get('project', self.project)}::{kwargs.get('environment', self.environment)}::{name}::latest")
        return True

    def watch_secret(self, name: str, callback: Callable[[SecretValue], None], poll_interval_sec: float = 15.0) -> Callable[[], None]:
        stop_event = threading.Event()
        last_version = [None]

        def _worker():
            while not stop_event.is_set():
                try:
                    curr = self.get_secret(name)
                    if last_version[0] is not None and curr.version_number != last_version[0]:
                        callback(curr)
                    last_version[0] = curr.version_number
                except Exception:
                    pass
                stop_event.wait(poll_interval_sec)

        t = threading.Thread(target=_worker, daemon=True, name=f"sv-watcher-{name}")
        t.start()

        def _unsubscribe():
            stop_event.set()

        return _unsubscribe
