export interface SecretVaultClientOptions {
  endpoint?: string;
  workspace?: string;
  project?: string;
  environment?: string;
  token?: string;
  timeoutMs?: number;
  failOpenStale?: boolean;
  cache?: {
    enabled?: boolean;
    ttlMs?: number;
    ttlSec?: number;
    maxStaleMs?: number;
    maxEntries?: number;
  };
}

export interface SecretValue {
  name: string;
  value: string;
  versionNumber: number;
  environment: string;
  fetchedAt: string;
}

export interface SecretMetadata {
  id: string;
  name: string;
  currentVersion: number;
  status: string;
  description?: string;
  environmentId: string;
  createdAt: string;
  updatedAt: string;
}

export interface SecretVersion {
  id: string;
  versionNumber: number;
  createdAt: string;
  createdBy?: string;
  reason?: string;
}

export declare class SecretVaultClient {
  constructor(options?: SecretVaultClientOptions);
  static create(options?: SecretVaultClientOptions): SecretVaultClient;

  ping(): Promise<boolean>;

  secrets: {
    get(secretName: string, version?: number | null, context?: Record<string, any>): Promise<SecretValue>;
    getMany(secretNames: string[], context?: Record<string, any>): Promise<Record<string, SecretValue | null>>;
    list(context?: Record<string, any>): Promise<SecretMetadata[]>;
    create(name: string, value: string, description?: string, context?: Record<string, any>): Promise<SecretMetadata>;
    update(name: string, value: string, description?: string | null, reason?: string, context?: Record<string, any>): Promise<SecretMetadata>;
    delete(name: string, context?: Record<string, any>): Promise<boolean>;
    listVersions(name: string, context?: Record<string, any>): Promise<SecretVersion[]>;
    rollback(name: string, targetVersion: number, reason?: string, context?: Record<string, any>): Promise<SecretVersion>;
    watch(name: string, callback: (newSecret: SecretValue) => void, pollIntervalMs?: number, context?: Record<string, any>): () => void;
  };

  close(): void;
}

export default SecretVaultClient;
