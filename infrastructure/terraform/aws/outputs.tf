output "alb_dns_name" {
  description = "Public DNS hostname of the Application Load Balancer"
  value       = module.alb.alb_dns_name
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "kms_key_arn" {
  description = "AWS KMS Master KEK Key ARN"
  value       = module.kms.key_arn
}

output "kms_key_alias" {
  description = "AWS KMS Key Alias"
  value       = module.kms.alias_name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint address"
  value       = module.rds.db_address
}

output "redis_endpoint" {
  description = "ElastiCache Redis Primary endpoint address"
  value       = module.elasticache.primary_endpoint_address
}

output "s3_backup_bucket" {
  description = "S3 backup and audit archive bucket name"
  value       = module.s3.bucket_id
}

output "ecs_cluster_name" {
  description = "ECS Fargate Cluster Name"
  value       = module.ecs.cluster_name
}

output "github_actions_role_arn" {
  description = "IAM Role ARN for GitHub Actions OIDC deployment"
  value       = module.iam.github_actions_role_arn
}
