# backend.tf

terraform {
  backend "s3" {
    bucket = "constellationlabs-tf"
    key = "load-balancer"
    region = "us-west-1"
  }
}
