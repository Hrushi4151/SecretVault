"""SecretVault Python SDK."""
from .client import SecretVaultClient
from .models import SecretValue, SecretMetadata
from .errors import SecretVaultError, SecretNotFoundError, AuthenticationError

__version__ = "1.0.0"
__all__ = [
    "SecretVaultClient",
    "SecretValue",
    "SecretMetadata",
    "SecretVaultError",
    "SecretNotFoundError",
    "AuthenticationError",
]
