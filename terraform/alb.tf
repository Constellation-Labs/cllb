# alb.tf

resource "aws_alb" "main" {
  name            = "cl-lb-alb-${var.env}"
  subnets         = aws_subnet.public.*.id
  security_groups = [aws_security_group.lb.id]

  tags = {
    Name = "cl-lb_alb_${var.env}"
    Env = var.env
  }
}

resource "aws_alb_target_group" "settings" {
  name        = "cl-lb-cb-target-group-${var.env}-st"
  port        = var.settings_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    healthy_threshold   = "4"
    interval            = "30"
    protocol            = "HTTP"
    matcher             = "200"
    timeout             = "5"
    path                = var.health_check_path
    unhealthy_threshold = "6"
  }
}

resource "aws_alb_target_group" "app" {
  name        = "cl-lb-cb-target-group-${var.env}"
  port        = var.app_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    healthy_threshold   = "4"
    interval            = "30"
    protocol            = "HTTP"
    matcher             = "200"
    timeout             = "5"
    path                = var.health_check_path
    unhealthy_threshold = "6"
  }
}

# Redirect all traffic from the ALB to the target group
resource "aws_alb_listener" "front_end" {
  load_balancer_arn = aws_alb.main.id
  port              = var.app_port
  protocol          = "HTTP"

  default_action {
    target_group_arn = aws_alb_target_group.app.id
    type             = "forward"
  }
}

resource "aws_alb_listener" "settings" {
  load_balancer_arn = aws_alb.main.id
  port              = var.settings_port
  protocol          = "HTTP"

  default_action {
    target_group_arn = aws_alb_target_group.settings.id
    type             = "forward"
  }
}
