[
  {
    "name": "cl-lb_app_${env}",
    "image": "${app_image}",
    "cpu": ${fargate_cpu},
    "memory": ${fargate_memory},
    "networkMode": "awsvpc",
    "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/cl-lb_app_${env}",
          "awslogs-region": "${aws_region}",
          "awslogs-stream-prefix": "ecs"
        }
    },
    "portMappings": [
      {
        "containerPort": ${app_port},
        "hostPort": ${app_port}
      }
    ],
    "environment": [
        { "name": "CL_BUCKET", "value": "${app_bucket}" },
        { "name": "CL_APP_CONF", "value": "${app_conf_file}" }
    ]
  }
]