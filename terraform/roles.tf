# ECS task execution role data
data "aws_iam_policy_document" "ecs_task_execution_role" {
  version = "2012-10-17"
  statement {
    sid = ""
    effect = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ECS task execution role
resource "aws_iam_role" "ecs_task_execution_role" {
  name               = var.ecs_task_execution_role_name
  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_role.json
}

# ECS task execution role policy attachment
resource "aws_iam_role_policy_attachment" "ecs_task_execution_role" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ------------------------------------------

# ECS task role data
resource "aws_iam_policy" "ecs_task_s3_policy" {
  name = var.ecs_task_role_policy
  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "1",
            "Effect": "Allow",
            "Action": "s3:*",
            "Resource": [
              "arn:aws:s3:::${local.config_bucket_name}",
              "arn:aws:s3:::${local.config_bucket_name}/*"
            ]
        }
    ]
}
EOF
}

# ECS task role
resource "aws_iam_role" "ecs_task_role" {
  name               = var.ecs_task_role_name
  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_role.json
}

# ECS task role policy attachment
resource "aws_iam_role_policy_attachment" "ecs_task_role" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_task_s3_policy.arn
}