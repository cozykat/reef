/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.messaging

import javabridge.{Subscription, Deserializers}
import mock.{AMQPFixture, MockBrokerInterface}
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.totalgrid.reef.proto.Envelope
import org.totalgrid.reef.util.SyncVar
import serviceprovider.{PublishingSubscriptionActor, ServiceSubscriptionHandler}
import org.totalgrid.reef.reactor.mock.InstantReactor
import org.totalgrid.reef.protoapi.{ProtoServiceTypes, ServiceHandlerHeaders, RequestEnv}

@RunWith(classOf[JUnitRunner])
class ProtoSubscriptionTest extends FunSuite with ShouldMatchers {

  val exchange = "ProtoSubscriptionTest"
  val exchangeMap: Map[Class[_], ServiceInfo] = Map(
    classOf[Envelope.RequestHeader] -> new ServiceInfo(exchange, Deserializers.requestHeader))

  def setupTest(test: ProtoClient => Unit) {
    val connection = new MockBrokerInterface

    // TODO: fix setupTest to use all async and all sync

    AMQPFixture.run(connection, true) { amqp =>

      val pub = new PublishingSubscriptionActor(exchange+"_events", new InstantReactor{})
      amqp.add(pub)
      amqp.bindService(exchange, (new DemoSubscribeService(pub)).respond, true)

      AMQPFixture.sync(connection, true) { syncAmqp =>
        val client = new ProtoClient(syncAmqp, 10000, ServicesList.getServiceInfo(_, exchangeMap))

        test(client)
      }
    }
  }

  class DemoSubscribeService(subHandler : ServiceSubscriptionHandler) extends ProtoServiceable[Envelope.RequestHeader] {

    // list of entries
    private var entries = List.empty[Envelope.RequestHeader]

    private def handleSub(req: Envelope.RequestHeader, env : RequestEnv){
      val serviceHeaders = new ServiceHandlerHeaders(env)
      serviceHeaders.subQueue.foreach(subHandler.bind(_, req.getKey))
    }
    private def publish(evt : Envelope.Event, changes : List[Envelope.RequestHeader]){
      changes.foreach(msg => subHandler.publish(evt, msg, msg.getKey))
    }

    def deserialize(bytes: Array[Byte]) = Envelope.RequestHeader.parseFrom(bytes)
    def get(req: Envelope.RequestHeader, env: RequestEnv) = {
      handleSub(req, env)

      val response = req.getKey match {
        case "*" => entries
        case s => entries.find(_.getKey == s).toList
      }

      ProtoServiceTypes.Response(Envelope.Status.OK, "", response)
    }
    def post(req: Envelope.RequestHeader, env: RequestEnv) = put(req, env)
    def put(req: Envelope.RequestHeader, env: RequestEnv) = {
      handleSub(req, env)

      val status = entries.find(_.getKey == req.getKey) match{
        case None =>
          publish(Envelope.Event.ADDED, List(req))
          Envelope.Status.CREATED
        case Some(existing) =>
          publish(Envelope.Event.MODIFIED, List(req))
          Envelope.Status.UPDATED
      }
      entries = entries.filterNot(_.getKey == req.getKey) ::: List(req)

      ProtoServiceTypes.Response(status, "", List(req))
    }
    def delete(req: Envelope.RequestHeader, env: RequestEnv) = {
      handleSub(req, env)
      val _entries = entries.partition(_.getKey == req.getKey)
      entries = _entries._2
      publish(Envelope.Event.REMOVED, _entries._1)

      ProtoServiceTypes.Response(Envelope.Status.DELETED, "", _entries._1)
    }
  }

  def getEnv(sub: Subscription): RequestEnv = {
    val headers = new ServiceHandlerHeaders(new RequestEnv)
    sub.configure(headers)
    headers.env
  }

  test("ProtoClient Subscriptions") {
    setupTest { client =>

      val updates = new SyncVar[List[(Envelope.Event, Envelope.RequestHeader)]](Nil)
      val headerSubFunc = (evt: Envelope.Event, header: Envelope.RequestHeader) => updates.atomic(l => l ::: List((evt, header)))
      val headerSub = client.addSubscription(headerSubFunc)

      val integrity = client.get(Envelope.RequestHeader.newBuilder.setKey("*").setValue("*").build, getEnv(headerSub)) match{
        case ProtoServiceTypes.MultiSuccess(Nil) =>
        case _ => false should equal(true)
      }

      val created = client.putOneOrThrow(Envelope.RequestHeader.newBuilder.setKey("magic").setValue("abra").build)
      val modified = client.putOneOrThrow(Envelope.RequestHeader.newBuilder.setKey("magic").setValue("cadabra").build)
      val deleted = client.deleteOneOrThrow(Envelope.RequestHeader.newBuilder.setKey("magic").setValue("cadabra").build)

      updates.waitFor(_.size == 3)

      updates.current.map{_._1} should equal(List(Envelope.Event.ADDED, Envelope.Event.MODIFIED, Envelope.Event.REMOVED))
      updates.current.map{_._2} should equal(List(created, modified, deleted))
    }
  }
}