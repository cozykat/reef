/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.broker.memory

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.mockito.Mockito._
import collection.immutable.{ Queue => ScalaQueue }
import net.agileautomata.executor4s.testing.MockExecutor
import org.totalgrid.reef.broker._

@RunWith(classOf[JUnitRunner])
class MemoryBrokerStateTest extends FunSuite with ShouldMatchers {

  import MemoryBrokerState._

  test("Publishing to queue writes to first consumer and rotates consumers") {
    val msg = mock(classOf[BrokerMessage])
    val mc1 = mock(classOf[BrokerMessageConsumer])
    val mc2 = mock(classOf[BrokerMessageConsumer])
    val mc3 = mock(classOf[BrokerMessageConsumer])

    val exe = new MockExecutor

    Queue("q1", exe, consumers = List(mc1, mc2, mc3)).publish(msg) should equal(Queue("q1", exe, consumers = List(mc2, mc3, mc1)))

    exe.runNextPendingAction()

    verify(mc1).onMessage(msg)
    verifyZeroInteractions(mc2, mc3)
  }

  test("Publishing to queue with no consumers will enqueue message") {
    val msg1 = mock(classOf[BrokerMessage])
    val msg2 = mock(classOf[BrokerMessage])
    val exe = new MockExecutor
    val afterMsg1 = Queue("q1", exe).publish(msg1)
    afterMsg1 should equal(Queue("q1", exe, unread = ScalaQueue.empty[BrokerMessage] ++ List(msg1)))
    val afterMsg2 = afterMsg1.publish(msg2)
    afterMsg2 should equal(Queue("q1", exe, unread = ScalaQueue.empty[BrokerMessage] ++ List(msg1, msg2)))
  }

  test("Exchange can match queues") {
    val ex = Exchange("ex", "topic", List(Binding("#", "q1")))
    ex.getMatches("all") should equal(List("q1"))
  }

  test("Broker state declares exchanges") {
    State().declareExchange("hello", "topic") should equal(State(Map("hello" -> Exchange("hello", "topic"))))
  }

  test("Broker state declares queues") {
    val exe = new MockExecutor()
    State().declareQueue("queue", exe) should equal(State(queues = Map("queue" -> Queue("queue", exe))))
  }

  test("Broker state binds queues") {
    val testMsg = BrokerMessage(Array(0xFF.toByte), None)
    var msg: Option[BrokerMessage] = None
    val consumer = new BrokerMessageConsumer { def onMessage(m: BrokerMessage) = msg = Some(m) }

    val exe = new MockExecutor
    val state = State().declareExchange("ex", "topic").declareQueue("q", exe).bindQueue("q", "ex", "#", false).listen("q", consumer)
    state.exchanges("ex").bindings(0).queue should equal("q")
    state.publish("ex", "key", testMsg)
    exe.runNextPendingAction()
    msg should equal(Some(testMsg))
  }
}