export class SecretVaultError extends Error {
  constructor(message, status = 500, details = null) {
    super(message);
    this.name = 'SecretVaultError';
    this.status = status;
    this.details = details;
  }
}

export class SecretNotFoundError extends SecretVaultError {
  constructor(secretName) {
    super(`Secret '${secretName}' not found in target environment`, 404);
    this.name = 'SecretNotFoundError';
    this.secretName = secretName;
  }
}

export class AuthenticationError extends SecretVaultError {
  constructor(message = 'Unauthorized: invalid or expired SecretVault token') {
    super(message, 401);
    this.name = 'AuthenticationError';
  }
}
