# Production ECS Fargate Cluster & Microservices Module

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private application subnet IDs"
}

variable "ecs_app_sg_id" {
  type        = string
  description = "Security group for ECS tasks"
}

variable "ecs_execution_role_arn" {
  type        = string
  description = "ECS execution role ARN"
}

variable "ecs_task_role_arn" {
  type        = string
  description = "ECS task role ARN"
}

variable "backend_target_group_arn" {
  type        = string
  description = "ALB target group ARN for backend"
}

variable "frontend_target_group_arn" {
  type        = string
  description = "ALB target group ARN for frontend"
}

variable "backend_image" {
  type        = string
  default     = "secretvault/backend:latest"
  description = "Docker image for backend"
}

variable "frontend_image" {
  type        = string
  default     = "secretvault/frontend:latest"
  description = "Docker image for frontend"
}

variable "db_host" {
  type        = string
  description = "RDS DB Hostname"
}

variable "redis_host" {
  type        = string
  description = "ElastiCache Redis Hostname"
}

variable "kms_key_arn" {
  type        = string
  description = "AWS KMS Key ARN"
}

variable "kms_key_alias" {
  type        = string
  description = "AWS KMS Key Alias"
}

# ECS Cluster with Container Insights
resource "aws_ecs_cluster" "main" {
  name = "secretvault-cluster-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name        = "secretvault-cluster-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# CloudWatch Log Groups for Container Output
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/secretvault-backend-${var.environment}"
  retention_in_days = 90
  kms_key_id        = var.kms_key_arn

  tags = {
    Environment = var.environment
  }
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/secretvault-frontend-${var.environment}"
  retention_in_days = 90
  kms_key_id        = var.kms_key_arn

  tags = {
    Environment = var.environment
  }
}

# 1. Backend API Task Definition
resource "aws_ecs_task_definition" "backend" {
  family                   = "secretvault-backend-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.ecs_task_role_arn

  container_definitions = jsonencode([{
    name      = "backend"
    image     = var.backend_image
    essential = true

    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_HOST", value = var.db_host },
      { name = "DB_PORT", value = "5432" },
      { name = "REDIS_HOST", value = var.redis_host },
      { name = "REDIS_PORT", value = "6379" },
      { name = "KMS_PROVIDER", value = "aws" },
      { name = "KMS_KEY_ID", value = var.kms_key_alias }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = "backend"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 30
    }
  }])

  tags = {
    Environment = var.environment
  }
}

# 2. Frontend Web Control Plane Task Definition
resource "aws_ecs_task_definition" "frontend" {
  family                   = "secretvault-frontend-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.ecs_task_role_arn

  container_definitions = jsonencode([{
    name      = "frontend"
    image     = var.frontend_image
    essential = true

    portMappings = [{
      containerPort = 80
      hostPort      = 80
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.frontend.name
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = "frontend"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -q --spider http://localhost:80/ || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 10
    }
  }])

  tags = {
    Environment = var.environment
  }
}

# 3. Backend Service (Zero-Downtime Rolling Update Multi-AZ)
resource "aws_ecs_service" "backend" {
  name                               = "secretvault-backend-${var.environment}"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.backend.arn
  desired_count                      = 2
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_app_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.backend_target_group_arn
    container_name   = "backend"
    container_port   = 8080
  }

  tags = {
    Environment = var.environment
  }
}

# 4. Frontend Service
resource "aws_ecs_service" "frontend" {
  name                               = "secretvault-frontend-${var.environment}"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.frontend.arn
  desired_count                      = 2
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_app_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.frontend_target_group_arn
    container_name   = "frontend"
    container_port   = 80
  }

  tags = {
    Environment = var.environment
  }
}

output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "backend_service_name" {
  value = aws_ecs_service.backend.name
}

output "frontend_service_name" {
  value = aws_ecs_service.frontend.name
}
