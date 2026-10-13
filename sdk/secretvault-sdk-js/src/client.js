import { SecretCache } from './cache.js';
import { SecretVaultError, SecretNotFoundError, AuthenticationError } from './errors.js';

export class SecretVaultClient {
  constructor(options = {}) {
    this.endpoint = (options.endpoint || process.env.SECRETVault_ENDPOINT || 'http://localhost:8080').replace(/\/+$/, '');
    this.workspace = options.workspace || process.env.SECRETVault_WORKSPACE || 'default';
    this.project = options.project || process.env.SECRETVault_PROJECT || '';
    this.environment = options.environment || process.env.SECRETVault_ENVIRONMENT || 'production';
    this.token = options.token || process.env.SECRETVault_TOKEN || '';
    this.timeoutMs = options.timeoutMs || 5000;
    this.failOpenStale = options.failOpenStale !== false;

    this.cache = new SecretCache(options.cache || {});
    this.idCache = new Map();
    this.activeWatchers = new Map();

    // Bound Secrets API
    this.secrets = {
      get: this.getSecret.bind(this),
      getMany: this.getManySecrets.bind(this),
      list: this.listSecrets.bind(this),
      create: this.createSecret.bind(this),
      update: this.updateSecret.bind(this),
      delete: this.deleteSecret.bind(this),
      listVersions: this.listVersions.bind(this),
      rollback: this.rollbackSecret.bind(this),
      watch: this.watchSecret.bind(this)
    };
  }

  static create(options) {
    return new SecretVaultClient(options);
  }

  // --- HTTP Request Core ---
  async request(method, path, body = null, isReveal = false) {
    const url = `${this.endpoint}${path}`;
    const headers = {
      'Accept': 'application/json',
      'User-Agent': 'SecretVault-SDK-JS/1.0.0'
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    if (body && typeof body === 'object') {
      headers['Content-Type'] = 'application/json';
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal
      });

      if (response.status === 401 || response.status === 403) {
        throw new AuthenticationError();
      }

      const text = await response.text();
      let json = null;
      if (text) {
        try {
          json = JSON.parse(text);
        } catch {
          json = text;
        }
      }

      if (!response.ok) {
        const errorMsg = json?.message || json?.error || `HTTP ${response.status}: ${response.statusText}`;
        throw new SecretVaultError(errorMsg, response.status, json);
      }

      // Unwrap ApiResponse { success: true, data: ... } if present
      if (json && typeof json === 'object' && 'data' in json && 'success' in json) {
        return json.data;
      }
      return json;
    } catch (err) {
      if (err.name === 'AbortError') {
        throw new SecretVaultError(`SecretVault request timed out after ${this.timeoutMs}ms`, 408);
      }
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }

  // --- Slug Resolution ---
  isUuid(str) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str);
  }

  async resolveWorkspaceId(slugOrId = this.workspace) {
    if (this.isUuid(slugOrId)) return slugOrId;
    const cacheKey = `ws::${slugOrId}`;
    if (this.idCache.has(cacheKey)) return this.idCache.get(cacheKey);

    const list = await this.request('GET', '/api/v1/workspaces');
    const items = Array.isArray(list) ? list : (list?.content || list?.items || []);
    for (const ws of items) {
      if (ws.slug === slugOrId || ws.name === slugOrId || ws.id === slugOrId) {
        this.idCache.set(cacheKey, ws.id);
        return ws.id;
      }
    }
    throw new SecretVaultError(`Workspace '${slugOrId}' not found`, 404);
  }

  async resolveProjectId(workspaceId, slugOrId = this.project) {
    if (this.isUuid(slugOrId)) return slugOrId;
    const cacheKey = `proj::${workspaceId}::${slugOrId}`;
    if (this.idCache.has(cacheKey)) return this.idCache.get(cacheKey);

    const list = await this.request('GET', `/api/v1/workspaces/${workspaceId}/projects`);
    const items = Array.isArray(list) ? list : (list?.content || list?.items || []);
    for (const p of items) {
      if (p.slug === slugOrId || p.name === slugOrId || p.id === slugOrId) {
        this.idCache.set(cacheKey, p.id);
        return p.id;
      }
    }
    throw new SecretVaultError(`Project '${slugOrId}' not found in workspace`, 404);
  }

  async resolveEnvironmentId(workspaceId, projectId, slugOrId = this.environment) {
    if (this.isUuid(slugOrId)) return slugOrId;
    const cacheKey = `env::${workspaceId}::${projectId}::${slugOrId}`;
    if (this.idCache.has(cacheKey)) return this.idCache.get(cacheKey);

    const list = await this.request('GET', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments`);
    const items = Array.isArray(list) ? list : (list?.content || list?.items || []);
    for (const env of items) {
      if (env.slug === slugOrId || env.name === slugOrId || env.id === slugOrId || env.environmentType === slugOrId) {
        this.idCache.set(cacheKey, env.id);
        return env.id;
      }
    }
    throw new SecretVaultError(`Environment '${slugOrId}' not found in project`, 404);
  }

  async resolveSecretId(workspaceId, projectId, envId, nameOrId) {
    if (this.isUuid(nameOrId)) return nameOrId;
    const list = await this.listSecrets({ workspaceId, projectId, envId, search: nameOrId });
    for (const s of list) {
      if (s.name.toLowerCase() === nameOrId.toLowerCase() || s.id === nameOrId) {
        return s.id;
      }
    }
    throw new SecretNotFoundError(nameOrId);
  }

  async resolveContext(customContext = {}) {
    const wsId = await this.resolveWorkspaceId(customContext.workspace || this.workspace);
    const projId = await this.resolveProjectId(wsId, customContext.project || this.project);
    const envId = await this.resolveEnvironmentId(wsId, projId, customContext.environment || this.environment);
    return { wsId, projId, envId };
  }

  // --- Health Check ---
  async ping() {
    try {
      const res = await this.request('GET', '/api/v1/auth/whoami');
      return !!res;
    } catch {
      return false;
    }
  }

  // --- Secrets Operations ---
  async getSecret(secretName, version = null, context = {}) {
    const cacheKey = `${context.project || this.project}::${context.environment || this.environment}::${secretName}::${version || 'latest'}`;

    // 1. Check in-memory cache
    const cached = this.cache.get(cacheKey);
    if (cached && !cached.isStale) {
      return cached.value;
    }

    try {
      const { wsId, projId, envId } = await this.resolveContext(context);
      const secretId = await this.resolveSecretId(wsId, projId, envId, secretName);

      const qs = version ? `?version=${version}` : '';
      const data = await this.request('POST', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets/${secretId}/reveal${qs}`, null, true);

      const secretValue = {
        name: data.name || secretName,
        value: data.value,
        versionNumber: data.versionNumber || (version || 1),
        environment: data.environmentSlug || this.environment,
        fetchedAt: new Date().toISOString()
      };

      this.cache.set(cacheKey, secretValue);
      return secretValue;
    } catch (err) {
      // 2. Resilience: If network error and stale cache exists, serve stale
      if (cached && this.failOpenStale) {
        console.warn(`[SecretVault] Failed to refresh secret '${secretName}'. Serving cached stale value.`);
        return cached.value;
      }
      throw err;
    }
  }

  async getManySecrets(secretNames, context = {}) {
    const results = {};
    for (const name of secretNames) {
      try {
        results[name] = await this.getSecret(name, null, context);
      } catch (err) {
        results[name] = null;
      }
    }
    return results;
  }

  async listSecrets(context = {}) {
    const wsId = context.wsId || await this.resolveWorkspaceId(context.workspace || this.workspace);
    const projId = context.projId || await this.resolveProjectId(wsId, context.project || this.project);
    const envId = context.envId || await this.resolveEnvironmentId(wsId, projId, context.environment || this.environment);

    const query = new URLSearchParams();
    if (context.search) query.append('search', context.search);
    const qs = query.toString() ? `?${query.toString()}` : '';

    const res = await this.request('GET', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets${qs}`);
    return Array.isArray(res) ? res : (res?.content || res?.items || []);
  }

  async createSecret(name, value, description = '', context = {}) {
    const { wsId, projId, envId } = await this.resolveContext(context);
    const res = await this.request('POST', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets`, {
      name,
      value,
      description
    });
    return res;
  }

  async updateSecret(name, value, description = null, reason = 'SDK update', context = {}) {
    const { wsId, projId, envId } = await this.resolveContext(context);
    const secretId = await this.resolveSecretId(wsId, projId, envId, name);

    const body = { value, reason };
    if (description !== null) body.description = description;

    const res = await this.request('PATCH', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets/${secretId}`, body);
    this.cache.invalidate(`${context.project || this.project}::${context.environment || this.environment}::${name}::latest`);
    return res;
  }

  async deleteSecret(name, context = {}) {
    const { wsId, projId, envId } = await this.resolveContext(context);
    const secretId = await this.resolveSecretId(wsId, projId, envId, name);
    await this.request('DELETE', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets/${secretId}`);
    this.cache.invalidate(`${context.project || this.project}::${context.environment || this.environment}::${name}::latest`);
    return true;
  }

  async listVersions(name, context = {}) {
    const { wsId, projId, envId } = await this.resolveContext(context);
    const secretId = await this.resolveSecretId(wsId, projId, envId, name);
    const res = await this.request('GET', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets/${secretId}/versions`);
    return Array.isArray(res) ? res : (res?.content || res?.items || []);
  }

  async rollbackSecret(name, targetVersion, reason = 'SDK rollback', context = {}) {
    const { wsId, projId, envId } = await this.resolveContext(context);
    const secretId = await this.resolveSecretId(wsId, projId, envId, name);
    const res = await this.request('POST', `/api/v1/workspaces/${wsId}/projects/${projId}/environments/${envId}/secrets/${secretId}/rollback`, {
      targetVersion,
      reason
    });
    this.cache.invalidate(`${context.project || this.project}::${context.environment || this.environment}::${name}::latest`);
    return res;
  }

  // --- Dynamic Rotation Watcher ---
  watchSecret(name, callback, pollIntervalMs = 15000, context = {}) {
    let lastVersion = null;
    let isRunning = true;

    const check = async () => {
      if (!isRunning) return;
      try {
        const current = await this.getSecret(name, null, context);
        if (lastVersion !== null && current.versionNumber !== lastVersion) {
          callback(current);
        }
        lastVersion = current.versionNumber;
      } catch (err) {
        // Suppress background poll errors
      }
    };

    // Initial check
    check();
    const timer = setInterval(check, pollIntervalMs);

    // Return unwatch function
    return () => {
      isRunning = false;
      clearInterval(timer);
    };
  }

  close() {
    this.cache.clear();
    for (const [_, unwatch] of this.activeWatchers) {
      if (typeof unwatch === 'function') unwatch();
    }
    this.activeWatchers.clear();
  }
}
