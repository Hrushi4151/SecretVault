# VPC Module — 3-Tier Multi-AZ Architecture (Public, Private App, Isolated DB)

variable "environment" {
  type        = string
  description = "Deployment environment (prod, staging)"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "Base CIDR block for VPC"
}

variable "availability_zones" {
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
  description = "List of availability zones"
}

variable "kms_key_arn" {
  type        = string
  description = "KMS Key ARN for VPC Flow Logs encryption"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "secretvault-vpc-${var.environment}"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Internet Gateway for Public Subnets
resource "aws_internet_gateway" "gw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name        = "secretvault-igw-${var.environment}"
    Environment = var.environment
  }
}

# 1. Public Subnets (ALB & NAT Gateways)
resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name        = "secretvault-public-subnet-${var.availability_zones[count.index]}-${var.environment}"
    Tier        = "Public"
    Environment = var.environment
  }
}

# Elastic IPs and NAT Gateways for Private Subnet Outbound Egress
resource "aws_eip" "nat" {
  count  = length(var.availability_zones)
  domain = "vpc"

  tags = {
    Name        = "secretvault-nat-eip-${var.availability_zones[count.index]}-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_nat_gateway" "nat" {
  count         = length(var.availability_zones)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name        = "secretvault-nat-gw-${var.availability_zones[count.index]}-${var.environment}"
    Environment = var.environment
  }

  depends_on = [aws_internet_gateway.gw]
}

# 2. Private App Subnets (ECS Fargate Tasks)
resource "aws_subnet" "private_app" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 4)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "secretvault-private-app-subnet-${var.availability_zones[count.index]}-${var.environment}"
    Tier        = "PrivateApp"
    Environment = var.environment
  }
}

# 3. Isolated Database Subnets (RDS PostgreSQL & ElastiCache Redis — 0 Internet Route)
resource "aws_subnet" "isolated_db" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 8)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "secretvault-isolated-db-subnet-${var.availability_zones[count.index]}-${var.environment}"
    Tier        = "IsolatedDatabase"
    Environment = var.environment
  }
}

# Public Route Table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.gw.id
  }

  tags = {
    Name        = "secretvault-public-rt-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "public" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private App Route Tables (Routed to NAT Gateways for Secure Egress)
resource "aws_route_table" "private_app" {
  count  = length(var.availability_zones)
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat[count.index].id
  }

  tags = {
    Name        = "secretvault-private-app-rt-${var.availability_zones[count.index]}-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "private_app" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# Isolated DB Route Table (Strictly Internal — No Default Gateway)
resource "aws_route_table" "isolated_db" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name        = "secretvault-isolated-db-rt-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "isolated_db" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.isolated_db[count.index].id
  route_table_id = aws_route_table.isolated_db.id
}

# VPC Flow Logs (Encrypted with KMS in CloudWatch)
resource "aws_cloudwatch_log_group" "flow_logs" {
  name              = "/aws/vpc/secretvault-flow-logs-${var.environment}"
  retention_in_days = 90
  kms_key_id        = var.kms_key_arn

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role" "flow_logs" {
  name = "secretvault-vpc-flow-logs-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy" "flow_logs" {
  name = "secretvault-vpc-flow-logs-policy"
  role = aws_iam_role.flow_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Effect   = "Allow"
      Resource = "${aws_cloudwatch_log_group.flow_logs.arn}:*"
    }]
  })
}

resource "aws_flow_log" "main" {
  iam_role_arn    = aws_iam_role.flow_logs.arn
  log_destination = aws_cloudwatch_log_group.flow_logs.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.main.id

  tags = {
    Name        = "secretvault-vpc-flow-log-${var.environment}"
    Environment = var.environment
  }
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  value = aws_subnet.private_app[*].id
}

output "isolated_db_subnet_ids" {
  value = aws_subnet.isolated_db[*].id
}
