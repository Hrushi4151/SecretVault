# Least-Privilege IAM Module with OIDC GitHub Actions Federation and Break-Glass Controls

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Key ARN for cryptographic operations"
}

variable "backup_bucket_arn" {
  type        = string
  description = "S3 Bucket ARN for backups"
}

variable "github_repository" {
  type        = string
  default     = "Hrushi4151/SecretVault"
  description = "GitHub repository for OIDC federation"
}

data "aws_caller_identity" "current" {}

# 1. ECS Task Execution Role (Infrastructure Container Agent)
resource "aws_iam_role" "ecs_execution" {
  name = "secretvault-ecs-execution-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "ecs_execution_standard" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# 2. ECS Task Role (Runtime Application Boundary)
resource "aws_iam_role" "ecs_task" {
  name = "secretvault-ecs-task-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
    }]
  })

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "ecs_task_kms_s3" {
  name = "secretvault-task-kms-s3-policy"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowKmsKeyWrapping"
        Effect = "Allow"
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:DescribeKey"
        ]
        Resource = var.kms_key_arn
      },
      {
        Sid    = "AllowS3Backups"
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          var.backup_bucket_arn,
          "${var.backup_bucket_arn}/*"
        ]
      }
    ]
  })
}

# 3. GitHub Actions OIDC Deployer Role (Zero Long-Lived Static Keys)
resource "aws_iam_role" "github_actions_oidc" {
  name = "secretvault-github-deployer-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringLike = {
          "token.actions.githubusercontent.com:sub" : "repo:${var.github_repository}:*"
        }
        StringEquals = {
          "token.actions.githubusercontent.com:aud" : "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "secretvault-github-actions-deploy-policy"
  role = aws_iam_role.github_actions_oidc.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEcsDeployment"
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices",
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition"
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowPassRoleToEcs"
        Effect = "Allow"
        Action = "iam:PassRole"
        Resource = [
          aws_iam_role.ecs_execution.arn,
          aws_iam_role.ecs_task.arn
        ]
      }
    ]
  })
}

# 4. Break-Glass Emergency Administrator Role (MFA Required)
resource "aws_iam_role" "break_glass_admin" {
  name = "secretvault-break-glass-admin-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
      }
      Action = "sts:AssumeRole"
      Condition = {
        Bool = {
          "aws:MultiFactorAuthPresent" : "true"
        }
      }
    }]
  })

  tags = {
    Environment = var.environment
    Security    = "BreakGlass"
  }
}

output "ecs_execution_role_arn" {
  value = aws_iam_role.ecs_execution.arn
}

output "ecs_task_role_arn" {
  value = aws_iam_role.ecs_task.arn
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions_oidc.arn
}

output "break_glass_role_arn" {
  value = aws_iam_role.break_glass_admin.arn
}
