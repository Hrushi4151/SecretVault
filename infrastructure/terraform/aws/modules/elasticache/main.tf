# Production Multi-AZ ElastiCache Redis Replication Group Module

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "isolated_subnet_ids" {
  type        = list(string)
  description = "Subnet IDs for isolated cache tier"
}

variable "elasticache_sg_id" {
  type        = string
  description = "Security Group ID for Redis"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Key ARN for at-rest encryption"
}

variable "redis_node_type" {
  type        = string
  default     = "cache.t4g.medium"
  description = "ElastiCache node type"
}

variable "redis_auth_token" {
  type        = string
  sensitive   = true
  description = "Redis AUTH token (password)"
}

resource "aws_elasticache_subnet_group" "redis" {
  name        = "secretvault-redis-subnet-group-${var.environment}"
  subnet_ids  = var.isolated_subnet_ids
  description = "Subnet group for Redis cluster"

  tags = {
    Environment = var.environment
  }
}

resource "aws_elasticache_parameter_group" "redis7" {
  name        = "secretvault-redis7-params-${var.environment}"
  family      = "redis7"
  description = "Hardened parameter group for Redis 7"

  parameter {
    name  = "maxmemory-policy"
    value = "volatile-ttl"
  }

  parameter {
    name  = "notify-keyspace-events"
    value = "Ex"
  }

  tags = {
    Environment = var.environment
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "secretvault-redis-${var.environment}"
  description          = "Production Multi-AZ Redis Replication Group for SecretVault"
  node_type            = var.redis_node_type
  num_cache_clusters   = 2
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.redis7.name
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [var.elasticache_sg_id]

  automatic_failover_enabled = true
  multi_az_enabled           = true
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  auth_token                 = var.redis_auth_token

  auto_minor_version_upgrade = true
  maintenance_window         = "sun:05:30-sun:06:30"
  snapshot_retention_limit   = 7
  snapshot_window            = "02:00-03:00"

  tags = {
    Name        = "secretvault-redis-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

output "primary_endpoint_address" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "reader_endpoint_address" {
  value = aws_elasticache_replication_group.main.reader_endpoint_address
}

output "port" {
  value = aws_elasticache_replication_group.main.port
}
