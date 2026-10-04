# CloudWatch Metric Alarms & Observability Module

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "alb_arn" {
  type        = string
  description = "ALB ARN"
}

variable "ecs_cluster_name" {
  type        = string
  description = "ECS Cluster Name"
}

variable "backend_service_name" {
  type        = string
  description = "Backend ECS Service Name"
}

variable "db_resource_id" {
  type        = string
  description = "RDS DB Resource Identifier"
}

# 1. High 5xx Error Rate Alarm
resource "aws_cloudwatch_metric_alarm" "alb_5xx_errors" {
  alarm_name          = "secretvault-alb-high-5xx-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Triggered when SecretVault backend generates more than 5 5xx errors per minute"
  treat_missing_data  = "notBreaching"

  tags = {
    Environment = var.environment
  }
}

# 2. ECS High CPU Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "secretvault-ecs-high-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Triggered when ECS Backend task CPU exceeds 80% for 3 consecutive minutes"

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.backend_service_name
  }

  tags = {
    Environment = var.environment
  }
}

# 3. ECS High Memory Utilization Alarm
resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  alarm_name          = "secretvault-ecs-high-memory-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "Triggered when ECS Backend task Memory exceeds 85% for 3 consecutive minutes"

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.backend_service_name
  }

  tags = {
    Environment = var.environment
  }
}

# 4. RDS PostgreSQL High Connection Spike Alarm
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "secretvault-rds-high-connections-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 120
  statistic           = "Average"
  threshold           = 150
  alarm_description   = "Triggered when PostgreSQL active connections exceed pool capacity threshold"

  tags = {
    Environment = var.environment
  }
}

output "alb_5xx_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.alb_5xx_errors.arn
}

output "ecs_cpu_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.ecs_cpu_high.arn
}
