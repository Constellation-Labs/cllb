locals {
  config_bucket_name = terraform.workspace == "mainnet" ? "constellationlabs-lb-config-${var.env}" : "constellationlabs-lb-dev-config-${var.env}"
}

resource "aws_s3_bucket" "cl_lb_config" {
  bucket        = local.config_bucket_name
  force_destroy = true
  acl    = "private"

  policy = <<POLICY
{
  "Id": "Policy1574629164669",
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Stmt1574629154311",
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::${local.config_bucket_name}/application-${var.env}.conf",
      "Principal": {
        "AWS": "${aws_iam_role.ecs_task_role.arn}"
      }
    }
  ]
}
POLICY

  depends_on = [aws_iam_role.ecs_task_role]

  tags = {
    Name = "cl-lb_config_${var.env}"
    Env = var.env
  }
}

resource "aws_s3_bucket_object" "application-conf" {
  bucket = local.config_bucket_name
  key    = "application-${var.env}.conf"
  source = "templates/application.conf"

  etag = filemd5("templates/application.conf")

  depends_on = [aws_s3_bucket.cl_lb_config]
}