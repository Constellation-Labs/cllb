# ecs.tf

resource "aws_ecs_cluster" "main" {
  name = "cl-lb_cluster_${var.env}"

  tags = {
    Name = "cl-lb_cluster_${var.env}"
    Env = var.env
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "cl-lb_app-task_${var.env}"
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.fargate_cpu
  memory                   = var.fargate_memory
  container_definitions    = templatefile("templates/cl_lb_app.json.tpl", {
    app_image      = var.app_image
    app_port       = var.app_port
    settings_port  = var.settings_port
    app_bucket     = aws_s3_bucket.cl_lb_config.bucket
    app_conf_file  = aws_s3_bucket_object.application-conf.key
    fargate_cpu    = var.fargate_cpu
    fargate_memory = var.fargate_memory
    aws_region     = var.aws_region
    env            = var.env
  })
}

resource "aws_ecs_service" "main" {
  name            = "cl-lb_service_${var.env}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_count
  launch_type     = "FARGATE"

  network_configuration {
    security_groups  = [aws_security_group.ecs_tasks.id]
    subnets          = aws_subnet.private.*.id
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_alb_target_group.app.id
    container_name   = "cl-lb_app_${var.env}"
    container_port   = var.app_port
  }

  load_balancer {
    target_group_arn = aws_alb_target_group.settings.id
    container_name   = "cl-lb_app_${var.env}"
    container_port   = var.settings_port
  }

  depends_on = [aws_alb_listener.front_end, aws_iam_role_policy_attachment.ecs_task_execution_role, aws_iam_role_policy_attachment.ecs_task_role]
}
