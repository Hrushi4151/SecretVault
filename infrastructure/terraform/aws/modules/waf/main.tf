# AWS WAF v2 Web Application Firewall & DDoS Defense Module

variable "environment" {
  type        = string
  description = "Deployment environment"
}

variable "alb_arn" {
  type        = string
  description = "Application Load Balancer ARN to associate with WAF"
}

resource "aws_wafv2_web_acl" "main" {
  name        = "secretvault-waf-${var.environment}"
  description = "Production WAF rules for SecretVault Control Plane"
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  # 1. AWS Managed Common Rule Set (OWASP Top 10 Protections)
  rule {
    name     = "AWS-AWSManagedRulesCommonRuleSet"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SecretVaultCommonRules"
      sampled_requests_enabled   = true
    }
  }

  # 2. AWS Managed Known Bad Inputs Rule Set
  rule {
    name     = "AWS-AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SecretVaultBadInputs"
      sampled_requests_enabled   = true
    }
  }

  # 3. AWS Managed SQL Injection Rule Set
  rule {
    name     = "AWS-AWSManagedRulesSQLiRuleSet"
    priority = 30

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SecretVaultSQLiRules"
      sampled_requests_enabled   = true
    }
  }

  # 4. Rate-Based Abuse Protection (Max 2,000 requests per 5 minutes per IP)
  rule {
    name     = "SecretVaultRateLimitPerIP"
    priority = 40

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SecretVaultRateLimit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "SecretVaultWebAcl"
    sampled_requests_enabled   = true
  }

  tags = {
    Name        = "secretvault-waf-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_wafv2_web_acl_association" "alb_association" {
  resource_arn = var.alb_arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn
}

output "web_acl_arn" {
  value = aws_wafv2_web_acl.main.arn
}

output "web_acl_id" {
  value = aws_wafv2_web_acl.main.id
}
