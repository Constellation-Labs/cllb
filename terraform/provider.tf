# provider.tf

# Specify the provider and access details
provider "aws" {
  region                  = var.aws_region

  version = "~> 2.0"
}