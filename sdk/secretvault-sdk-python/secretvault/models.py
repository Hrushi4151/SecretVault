"""SecretVault Data Models."""
from dataclasses import dataclass, field
from typing import Optional, Dict, Any
from datetime import datetime, timezone

@dataclass
class SecretValue:
    """Represents a decrypted secret value and metadata."""
    name: str
    value: str
    version_number: int = 1
    environment: str = "default"
    fetched_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    def as_string(self) -> str:
        return self.value

    def as_int(self) -> int:
        return int(self.value)

    def as_bool(self) -> bool:
        return self.value.strip().lower() in ("true", "1", "yes", "t", "y")

    def __repr__(self) -> str:
        return f"<SecretValue name='{self.name}' version={self.version_number} value='[MASKED]'>"

@dataclass
class SecretMetadata:
    """Represents secret inventory metadata without decrypted plaintext."""
    id: str
    name: str
    current_version: int
    status: str = "ACTIVE"
    description: Optional[str] = None
    environment_id: Optional[str] = None
    created_at: Optional[str] = None
    updated_at: Optional[str] = None
