
resource "aws_s3_bucket" "cl_lb_config" {
  bucket        = "cl-lb-config"
  force_destroy = true

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
      "Resource": "arn:aws:s3:::cl-lb-config/application.conf",
      "Principal": "*"
    }
  ]
}
POLICY
}

resource "aws_s3_bucket_object" "application-conf" {
  bucket = "cl-lb-config"
  key    = "application.conf"
  source = "./terraform/templates/application.conf"

  etag = filemd5("./terraform/templates/application.conf")

  depends_on = [aws_s3_bucket.cl_lb_config]
}