# SecretVault — Enterprise Production Cloud Infrastructure Root
# Orchestrates 3-Tier Multi-AZ VPC, KMS, Multi-AZ PostgreSQL, ElastiCache Redis, ECS Fargate, ALB, WAF, S3, and IAM.

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "SecretVault"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = "PlatformSecurity"
    }
  }
}

# 1. AWS KMS Customer Managed Key
module "kms" {
  source      = "./modules/kms"
  environment = var.environment
}

# 2. 3-Tier Multi-AZ Virtual Private Cloud (VPC)
module "vpc" {
  source             = "./modules/vpc"
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  kms_key_arn        = module.kms.key_arn
}

# 3. S3 Encrypted Backups & Audit Log Archive
module "s3" {
  source      = "./modules/s3"
  environment = var.environment
  kms_key_arn = module.kms.key_arn
}

# 4. Security Groups
module "security_groups" {
  source      = "./modules/security_groups"
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
}

# 5. IAM Least Privilege Roles & GitHub OIDC
module "iam" {
  source            = "./modules/iam"
  environment       = var.environment
  kms_key_arn       = module.kms.key_arn
  backup_bucket_arn = module.s3.bucket_arn
  github_repository = var.github_repository
}

# 6. Production Multi-AZ RDS PostgreSQL 16
module "rds" {
  source              = "./modules/rds"
  environment         = var.environment
  isolated_subnet_ids = module.vpc.isolated_db_subnet_ids
  rds_sg_id           = module.security_groups.rds_sg_id
  kms_key_arn         = module.kms.key_arn
  db_instance_class   = var.db_instance_class
  db_name             = var.db_name
  db_username         = var.db_username
  db_password         = var.db_password
}

# 7. Production Multi-AZ ElastiCache Redis 7
module "elasticache" {
  source              = "./modules/elasticache"
  environment         = var.environment
  isolated_subnet_ids = module.vpc.isolated_db_subnet_ids
  elasticache_sg_id   = module.security_groups.elasticache_sg_id
  kms_key_arn         = module.kms.key_arn
  redis_node_type     = var.redis_node_type
  redis_auth_token    = var.redis_auth_token
}

# 8. Application Load Balancer
module "alb" {
  source            = "./modules/alb"
  environment       = var.environment
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  alb_sg_id         = module.security_groups.alb_sg_id
  certificate_arn   = var.certificate_arn
}

# 9. ECS Fargate Cluster & Microservices
module "ecs" {
  source                    = "./modules/ecs"
  environment               = var.environment
  private_subnet_ids        = module.vpc.private_app_subnet_ids
  ecs_app_sg_id             = module.security_groups.ecs_app_sg_id
  ecs_execution_role_arn    = module.iam.ecs_execution_role_arn
  ecs_task_role_arn         = module.iam.ecs_task_role_arn
  backend_target_group_arn  = module.alb.backend_target_group_arn
  frontend_target_group_arn = module.alb.frontend_target_group_arn
  backend_image             = var.backend_image
  frontend_image            = var.frontend_image
  db_host                   = module.rds.db_address
  redis_host                = module.elasticache.primary_endpoint_address
  kms_key_arn               = module.kms.key_arn
  kms_key_alias             = module.kms.alias_name
}

# 10. CloudWatch Metric Alarms
module "cloudwatch" {
  source               = "./modules/cloudwatch"
  environment          = var.environment
  alb_arn              = module.alb.alb_arn
  ecs_cluster_name     = module.ecs.cluster_name
  backend_service_name = module.ecs.backend_service_name
  db_resource_id       = module.rds.db_resource_id
}

# 11. AWS WAF v2 DDoS & OWASP Protection
module "waf" {
  source      = "./modules/waf"
  environment = var.environment
  alb_arn     = module.alb.alb_arn
}
