resource "aws_s3_bucket" "cl_lb_config" {
  bucket        = "constellationlabs-lb-config-${var.env}"
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
      "Resource": "arn:aws:s3:::constellationlabs-lb-config-${var.env}/application-${var.env}.conf",
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
  bucket = "constellationlabs-lb-config-${var.env}"
  key    = "application-${var.env}.conf"
  source = "templates/application.conf"

  etag = filemd5("templates/application.conf")

  depends_on = [aws_s3_bucket.cl_lb_config]
}