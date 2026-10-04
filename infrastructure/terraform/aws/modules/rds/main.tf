# Production Multi-AZ PostgreSQL RDS Module

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "isolated_subnet_ids" {
  type        = list(string)
  description = "Subnet IDs for isolated database tier"
}

variable "rds_sg_id" {
  type        = string
  description = "Security Group ID for RDS"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Key ARN for storage encryption"
}

variable "db_instance_class" {
  type        = string
  default     = "db.t4g.medium"
  description = "RDS instance class"
}

variable "db_name" {
  type        = string
  default     = "secretvault"
  description = "Initial database name"
}

variable "db_username" {
  type        = string
  default     = "vault_admin"
  description = "Master database username"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "Master database password"
}

resource "aws_db_subnet_group" "rds" {
  name        = "secretvault-rds-subnet-group-${var.environment}"
  subnet_ids  = var.isolated_subnet_ids
  description = "Database subnet group on isolated private subnets"

  tags = {
    Name        = "secretvault-rds-subnet-group-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_db_parameter_group" "pg16" {
  name        = "secretvault-pg16-params-${var.environment}"
  family      = "postgres16"
  description = "Hardened parameter group for PostgreSQL 16"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "200" # Log queries taking longer than 200ms
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  tags = {
    Environment = var.environment
  }
}

resource "aws_db_instance" "main" {
  identifier            = "secretvault-pg-${var.environment}"
  engine                = "postgres"
  engine_version        = "16.2"
  instance_class        = var.db_instance_class
  allocated_storage     = 50
  max_allocated_storage = 500
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  multi_az               = true
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  vpc_security_group_ids = [var.rds_sg_id]
  parameter_group_name   = aws_db_parameter_group.pg16.name

  backup_retention_period     = 30
  backup_window               = "03:00-04:00"
  maintenance_window          = "Sun:04:30-Sun:05:30"
  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "secretvault-pg-${var.environment}-final-snapshot"
  copy_tags_to_snapshot       = true

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  performance_insights_kms_key_id       = var.kms_key_arn

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Name        = "secretvault-postgres-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

output "db_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "db_address" {
  value = aws_db_instance.main.address
}

output "db_port" {
  value = aws_db_instance.main.port
}

output "db_resource_id" {
  value = aws_db_instance.main.resource_id
}
