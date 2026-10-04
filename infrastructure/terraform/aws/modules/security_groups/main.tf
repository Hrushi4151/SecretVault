# Defense-in-Depth Security Groups Module (Least-Privilege Inter-Tier Isolation)

variable "vpc_id" {
  type        = string
  description = "Target VPC ID"
}

variable "environment" {
  type        = string
  description = "Deployment environment"
}

# 1. Application Load Balancer Security Group
resource "aws_security_group" "alb" {
  name        = "secretvault-alb-sg-${var.environment}"
  description = "Controls HTTPS ingress traffic to SecretVault ALB"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTPS public ingress"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP to HTTPS redirect"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Outbound to private app containers"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = {
    Name        = "secretvault-alb-sg-${var.environment}"
    Environment = var.environment
  }
}

# 2. ECS Fargate Application Container Security Group
resource "aws_security_group" "ecs_app" {
  name        = "secretvault-ecs-app-sg-${var.environment}"
  description = "Restricts traffic to SecretVault backend and frontend containers"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Inbound from ALB to Frontend"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "Inbound from ALB to Backend API"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Outbound HTTPS for AWS KMS, AWS APIs, and cloud provider rotation push"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Outbound to RDS PostgreSQL"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    description = "Outbound to ElastiCache Redis"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = {
    Name        = "secretvault-ecs-app-sg-${var.environment}"
    Environment = var.environment
  }
}

# 3. RDS PostgreSQL Security Group (Isolated DB Tier — 0 Public Access)
resource "aws_security_group" "rds" {
  name        = "secretvault-rds-sg-${var.environment}"
  description = "Restricts PostgreSQL access strictly to application containers"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL ingress from ECS tasks only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_app.id]
  }

  tags = {
    Name        = "secretvault-rds-sg-${var.environment}"
    Environment = var.environment
  }
}

# 4. ElastiCache Redis Security Group (Isolated Cache Tier — 0 Public Access)
resource "aws_security_group" "elasticache" {
  name        = "secretvault-elasticache-sg-${var.environment}"
  description = "Restricts Redis access strictly to application containers"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis ingress from ECS tasks only"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_app.id]
  }

  tags = {
    Name        = "secretvault-elasticache-sg-${var.environment}"
    Environment = var.environment
  }
}

output "alb_sg_id" {
  value = aws_security_group.alb.id
}

output "ecs_app_sg_id" {
  value = aws_security_group.ecs_app.id
}

output "rds_sg_id" {
  value = aws_security_group.rds.id
}

output "elasticache_sg_id" {
  value = aws_security_group.elasticache.id
}
