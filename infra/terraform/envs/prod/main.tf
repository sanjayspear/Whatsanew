# Production skeleton (AWS ap-south-1, Mumbai: lowest latency to Bangalore).
# Each module is a thin wrapper over a community module, pinned in its own directory.
terraform {
  required_version = ">= 1.8"
  backend "s3" {
    bucket = "pulse-tfstate"
    key    = "prod/terraform.tfstate"
    region = "ap-south-1"
  }
}

provider "aws" { region = var.region }

variable "region" { default = "ap-south-1" }

# module "network"      { source = "../../modules/network"      cidr = "10.40.0.0/16" }
# module "eks"          { source = "../../modules/eks"          vpc_id = module.network.vpc_id }
# module "postgres"     { source = "../../modules/rds-postgres" engine_version = "16" extensions = ["postgis", "vector"] }
# module "kafka"        { source = "../../modules/msk"          broker_count = 3 }
# module "redis"        { source = "../../modules/elasticache" }
# module "media"        { source = "../../modules/s3-cdn"       buckets = ["pulse-media", "pulse-models"] }
#
# Phase 3 (global): replicate this env per region (eu-west-1, us-east-1) with region-scoped
# data (RegionCode partitioning) and a global model/CDN layer.
