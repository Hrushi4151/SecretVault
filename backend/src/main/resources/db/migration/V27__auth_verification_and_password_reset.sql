-- ==============================================================================
-- SecretVault — V27 Email Verification and Secure Password Reset
-- ==============================================================================

CREATE TABLE IF NOT EXISTS registration_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    organization_name VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reg_verif_email ON registration_verifications(email);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    reset_token VARCHAR(255) NOT NULL UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 0,
    otp_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_verified BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pwd_reset_email ON password_reset_tokens(email);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_token ON password_reset_tokens(reset_token);
