variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS deployment region"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "Target deployment environment (prod, staging)"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR block for VPC"
}

variable "availability_zones" {
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
  description = "Availability zones for multi-AZ deployment"
}

variable "github_repository" {
  type        = string
  default     = "Hrushi4151/SecretVault"
  description = "GitHub repository for Actions OIDC trust federation"
}

variable "certificate_arn" {
  type        = string
  default     = ""
  description = "ACM TLS Certificate ARN for HTTPS ALB listener"
}

variable "db_instance_class" {
  type        = string
  default     = "db.t4g.medium"
  description = "RDS PostgreSQL instance class"
}

variable "db_name" {
  type        = string
  default     = "secretvault"
  description = "Database name"
}

variable "db_username" {
  type        = string
  default     = "vault_admin"
  description = "Master database user"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "Master database password"
}

variable "redis_node_type" {
  type        = string
  default     = "cache.t4g.medium"
  description = "ElastiCache Redis node type"
}

variable "redis_auth_token" {
  type        = string
  sensitive   = true
  description = "Redis AUTH password token"
}

variable "backend_image" {
  type        = string
  default     = "123456789012.dkr.ecr.us-east-1.amazonaws.com/secretvault-backend:latest"
  description = "Backend container image URI"
}

variable "frontend_image" {
  type        = string
  default     = "123456789012.dkr.ecr.us-east-1.amazonaws.com/secretvault-frontend:latest"
  description = "Frontend container image URI"
}
