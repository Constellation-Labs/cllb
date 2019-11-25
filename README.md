# Constellation Network Loadbalancer

This application is a loadbalancer that routes HTTP on public API requests only to Nodes that are currently available. The initial list of nodes provided in the configuration file is used as a starting point for a cluster discovery.

## Configuration

Sample config file, where port is pointing to a peer API:

```
network-nodes = [
  {
    host = "35.235.86.151"
    port = "9001"
  },
  {
    host = "34.94.105.94"
    port = "9001"
  },
  {
    host = "35.236.93.144"
    port = "9001"
  },
  {
    host = "35.235.119.9"
    port = "9001"
  },
  {
    host = "34.94.142.154"
    port = "9001"
  }]
```

At least one of these nodes should always be operational and will serve as an entrypoint for cluster discovery.

## Building

Project is written in Scala and can be build with sbt. Building definiton includes task that provides a Docker image, ready to use for AWS deployment.

## Deployment

Before deployment this app should be published as a Docker image in the registry. `TODO: provide registry`

Once image is published, terraform script can be used to create, update or delete whole stack, including all dependencies and configuration.

Production configuration lives for now in the `terraform/templates/application.conf` file. It’s beeing automatically uploaded to S3 when the stack is applied.

Current stack definition deploys app to Fargate (ECS) on AWS, with autoscaling between 1 and 6 instances.

### Releasing after code changes

Once you update a Docker image (without changing it’s version) Terraform will not realise that it has to recreate ECS Task. To update project use `taint` command from terraform:

```
~/elb-manager on  master! ⌚ 17:29:00
$ terraform taint aws_ecs_task_definition.app
```



This way you will redeploy the app itself and AWS will recreate tasks with new Docker image.

### Releasing with configuration changes

Once you change configuration file use Terraform `apply` command in the project root directory.

```
~/elb-manager on  master! ⌚ 17:30:00

$ terraform apply terraform
```