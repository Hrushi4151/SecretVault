# AWS KMS Customer Managed Key (CMK) Module with Automated Key Rotation

variable "environment" {
  type        = string
  description = "Deployment environment"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_kms_key" "secretvault" {
  description             = "SecretVault Master Key Encryption Key (KEK) for ${var.environment}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Enable IAM User Permissions"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "Allow CloudWatch Logs and Flow Logs"
        Effect = "Allow"
        Principal = {
          Service = "logs.${data.aws_region.current.name}.amazonaws.com"
        }
        Action = [
          "kms:Encrypt*",
          "kms:Decrypt*",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:Describe*"
        ]
        Resource = "*"
      }
    ]
  })

  tags = {
    Name        = "secretvault-kek-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_kms_alias" "secretvault" {
  name          = "alias/secretvault-kek-${var.environment}"
  target_key_id = aws_kms_key.secretvault.key_id
}

output "key_arn" {
  value = aws_kms_key.secretvault.arn
}

output "key_id" {
  value = aws_kms_key.secretvault.key_id
}

output "alias_name" {
  value = aws_kms_alias.secretvault.name
}

output "alias_arn" {
  value = aws_kms_alias.secretvault.arn
}
