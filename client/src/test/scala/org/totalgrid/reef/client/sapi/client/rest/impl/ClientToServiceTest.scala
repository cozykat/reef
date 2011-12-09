/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.client.sapi.client.rest.impl

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import net.agileautomata.commons.testing._
import org.totalgrid.reef.client.sapi.client.rest.Client
import org.totalgrid.reef.client.proto.Envelope

import org.totalgrid.reef.client.AnyNodeDestination

import org.totalgrid.reef.client.sapi.client.{ Promise, SuccessResponse, Response }
import net.agileautomata.executor4s._
import org.totalgrid.reef.client.sapi.client.rest.fixture._

@RunWith(classOf[JUnitRunner])
class QpidClientToService extends ClientToServiceTest with QpidBrokerTestFixture

@RunWith(classOf[JUnitRunner])
class MemoryClientToService extends ClientToServiceTest with MemoryBrokerTestFixture

// provides a specification for how the client should interact with brokers. testable on multiple brokers via minx
trait ClientToServiceTest extends BrokerTestFixture with FunSuite with ShouldMatchers {

  def fixture[A](attachService: Boolean)(fun: Client => A) = broker { b =>
    val executor = Executors.newScheduledThreadPool(5)
    var binding: Option[Cancelable] = None
    try {
      val conn = new DefaultConnection(b, executor, 100)
      conn.addServiceInfo(ExampleServiceList.info)
      binding = if (attachService) Some(conn.bindService(new SomeIntegerIncrementService(conn), executor, new AnyNodeDestination, true))
      else Some(conn.bindService(new BlackHoleService(SomeIntegerTypeDescriptor), executor, new AnyNodeDestination, true))
      fun(conn.login("foo"))
    } finally {
      binding.foreach(_.cancel())
      executor.terminate()
    }
  }

  def testSuccess(c: Client) {
    val i = SomeInteger(1)
    c.put(i).await should equal(Response(Envelope.Status.OK, i.increment))
  }

  test("Service calls are successful") {
    fixture(true) { c =>
      testSuccess(c)
    }
  }

  //  test("Service calls can be listened for") {
  //    fixture(true) { c =>
  //      val i = SomeInteger(1)
  //      val future = c.put(i)
  //      var listenFired = false
  //      future.listen { result =>
  //        result should equal(Response(Envelope.Status.OK, i.increment))
  //        listenFired = true
  //      }
  //      // await should force future.listen calls to have fired
  //      future.await
  //      listenFired should equal(true)
  //    }
  //  }
  //
  //  test("Service calls can be listened for (promise)") {
  //    fixture(true) { c =>
  //      val i = SomeInteger(1)
  //      val promise = Promise.from(c.put(i).map { _.one })
  //      var listenFired = false
  //      promise.listen { prom =>
  //        prom.await should equal(i.increment)
  //        listenFired = true
  //      }
  //      // await should force promise listens to have fired
  //      promise.await
  //      listenFired should equal(true)
  //    }
  //  }

  test("Subscription calls work") { //subscriptions not currently working with embedded broker
    fixture(true) { c =>
      val events = new SynchronizedList[SomeInteger]
      val sub = c.subscribe(SomeIntegerTypeDescriptor).await.get
      c.put(SomeInteger(1), sub).await should equal(SuccessResponse(list = List(SomeInteger(2))))
      sub.start(e => events.append(e.value))
      events shouldBecome SomeInteger(2) within 5000
      sub.cancel()
    }
  }

  test("Events come in right order") { //subscriptions not currently working with embedded broker
    fixture(true) { c =>
      val events = new SynchronizedList[Int]
      val sub = c.subscribe(SomeIntegerTypeDescriptor).await.get
      c.bindQueueByClass(sub.id(), "#", classOf[SomeInteger])
      sub.start(e => events.append(e.value.num))

      val range = 0 to 1500

      range.foreach { i => c.publishEvent(Envelope.SubscriptionEventType.MODIFIED, SomeInteger(i), "key") }

      events shouldBecome range.toList within 5000
      sub.cancel()
    }
  }

  def testTimeout(c: Client) {
    val i = SomeInteger(1)
    c.put(i).await should equal(Response(Envelope.Status.RESPONSE_TIMEOUT))
  }

  test("Failures timeout sucessfully") {
    fixture(false) { c =>
      testTimeout(c)
    }
  }

  def testFlatmapSuccess(c: Client) {
    val i = SomeInteger(1)
    // this test simulates an API call where we do 2 steps
    val f1: Future[Response[SomeInteger]] = c.put(i)
    f1.flatMap {
      _.one match {
        case Success(int) => f1.replicate[Response[Double]](Response(Envelope.Status.OK, int.num * 88.88))
        case fail: Failure => f1.asInstanceOf[Future[Response[Double]]]
      }
    }.await should equal(Response(Envelope.Status.OK, 88.88 * 2))
  }

  test("Flatmapped service calls are successful") {
    fixture(true) { c =>
      testFlatmapSuccess(c)
    }
  }

  // below show that these operations will succeed when run inside the strand (like on a subscription
  // callback)

  test("Service calls are successful (inside strand)") {
    fixture(true) { c =>
      c.attempt {
        testSuccess(c)
      }.await
    }
  }

  test("Failures timeout sucessfully (inside strand)") {
    fixture(false) { c =>
      c.attempt {
        testTimeout(c)
      }.await
    }
  }

  ignore("Flatmap services calls are successfull (inside strand)") {
    // currently deadlocks because flatMap is implemented with a listen call that gets marshalled
    // to this same strand and therefore can never be run (since we are inside that thread already)
    fixture(true) { c =>
      c.attempt {
        testFlatmapSuccess(c)
      }.await
    }
  }
}