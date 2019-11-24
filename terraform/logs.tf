# logs.tf
data "aws_caller_identity" "current" {}

resource "aws_cloudtrail" "cl_lb" {
  name                          = "tf-trail-foobar"
  s3_bucket_name                = aws_s3_bucket.cl_lb.id
  s3_key_prefix                 = "prefix"
  include_global_service_events = false
}

resource "aws_s3_bucket" "cl_lb" {
  bucket        = "cl-lb-cloudtrail"
  force_destroy = true

  policy = <<POLICY
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AWSCloudTrailAclCheck",
            "Effect": "Allow",
            "Principal": {
              "Service": "cloudtrail.amazonaws.com"
            },
            "Action": "s3:GetBucketAcl",
            "Resource": "arn:aws:s3:::cl-lb-cloudtrail"
        },
        {
            "Sid": "AWSCloudTrailWrite",
            "Effect": "Allow",
            "Principal": {
              "Service": "cloudtrail.amazonaws.com"
            },
            "Action": "s3:PutObject",
            "Resource": "arn:aws:s3:::cl-lb-cloudtrail/prefix/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
            "Condition": {
                "StringEquals": {
                    "s3:x-amz-acl": "bucket-owner-full-control"
                }
            }
        }
    ]
}
POLICY
}

# Set up CloudWatch group and log stream and retain logs for 30 days
resource "aws_cloudwatch_log_group" "cl_log_group" {
  name              = "/ecs/cl-app"
  retention_in_days = 30

  tags = {
    Name = "cl-lb-log-group"
  }
}

resource "aws_cloudwatch_log_stream" "cl_log_stream" {
  name           = "cl-lb-log-stream"
  log_group_name = aws_cloudwatch_log_group.cl_log_group.name
}