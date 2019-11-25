# variables.tf

variable "aws_region" {
  description = "The AWS region things are created in"
  default     = "us-west-1"
}

variable "ecs_task_execution_role_name" {
  description = "ECS task execution role name"
  default = "cl-lb"
}

variable "ecs_task_role_name" {
  description = "ECS task role name"
  default = "cl-lb-task"
}

variable "ecs_task_role_policy" {
  description = "ECS task role policy"
  default = "cl-lb-task-policy"
}

variable "ecs_auto_scale_role_name" {
  description = "ECS auto scale role Name"
  default = "myEcsAutoScaleRole"
}

variable "az_count" {
  description = "Number of AZs to cover in a given region"
  default     = "2"
}

variable "app_image" {
  description = "Docker image to run in the ECS cluster"
  default     = "abankowski/cluster-loadbalancer:0.1.1"
}

variable "app_port" {
  description = "Port exposed by the docker image to redirect traffic to"
  default     = 9000
}

variable "app_count" {
  description = "Number of docker containers to run"
  default     = 1
}

variable "health_check_path" {
  default = "/utils/health"
}

variable "fargate_cpu" {
  description = "Fargate instance CPU units to provision (1 vCPU = 1024 CPU units)"
  default     = "2048"
}

variable "fargate_memory" {
  description = "Fargate instance memory to provision (in MiB)"
  default     = "4096"
}