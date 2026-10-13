"""SecretVault SDK Custom Exceptions."""

class SecretVaultError(Exception):
    """Base exception for all SecretVault SDK errors."""
    def __init__(self, message: str, status_code: int = 500, details=None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.details = details

class SecretNotFoundError(SecretVaultError):
    """Raised when a secret key does not exist in the environment."""
    def __init__(self, secret_name: str):
        super().__init__(f"Secret '{secret_name}' not found in target environment", 404)
        self.secret_name = secret_name

class AuthenticationError(SecretVaultError):
    """Raised on invalid, revoked, or missing Machine Identity token."""
    def __init__(self, message: str = "Unauthorized: invalid or expired SecretVault token"):
        super().__init__(message, 401)
