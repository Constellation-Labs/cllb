package org.constellation.util.aws.elb

import java.net.InetAddress

import cats.effect.{IO, Timer}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.elasticloadbalancingv2.{AmazonElasticLoadBalancingAsync, AmazonElasticLoadBalancingAsyncClient, AmazonElasticLoadBalancingAsyncClientBuilder}
import com.amazonaws.services.elasticloadbalancingv2.model.{DeregisterTargetsRequest, DescribeTargetGroupsRequest, RegisterTargetsRequest, RegisterTargetsResult, TargetDescription}
import org.constellation.util.cats.effect.JFutureBridge

import scala.jdk.CollectionConverters._

class AmazonElasticLoadBalancingScalaClient(val c: AmazonElasticLoadBalancingAsync)(implicit t: Timer[IO]) extends JFutureBridge {

  def add(targets: List[TargetDescription]) = {
    val req = new RegisterTargetsRequest
    //    req.setTargetGroupArn()
    req.setTargets(targets.asJava)

    toIO(IO(c.registerTargetsAsync(req)))
  }

  def remove(targets: List[TargetDescription]) = {

    val req = new DeregisterTargetsRequest

//    req.setTargetGroupArn()
    req.setTargets(targets.asJava)

    toIO(IO(c.deregisterTargetsAsync(req)))
  }

  def getTargets() = {

    val req = new DescribeTargetGroupsRequest

    req.setPageSize(400)

    // TODO:  for over 400 need to load paged results interativly
    toIO(IO(c.describeTargetGroupsAsync(req)))
  }
}


object AmazonElasticLoadBalancingScalaClient {

  def apply()(implicit t: Timer[IO]): AmazonElasticLoadBalancingScalaClient = {

    new AmazonElasticLoadBalancingScalaClient(AmazonElasticLoadBalancingAsyncClientBuilder.defaultClient())
  }

}