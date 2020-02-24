# backend.tf

terraform {
  backend "s3" {
    bucket = "constellationlabs-terraform"
    key = "load-balancer"
    region = "us-west-1"
  }
}
