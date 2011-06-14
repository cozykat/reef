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
package org.totalgrid.reef.services.core

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.totalgrid.reef.util.SyncVar
import org.totalgrid.reef.proto.ProcessStatus._
import org.totalgrid.reef.proto.Application.ApplicationConfig

import org.totalgrid.reef.services.ServiceResponseTestingHelpers._

import com.google.protobuf.GeneratedMessage

import org.totalgrid.reef.proto.ReefServicesList
import org.totalgrid.reef.messaging.serviceprovider._
import org.totalgrid.reef.japi.Envelope
import org.totalgrid.reef.models.DatabaseUsingTestBase
import org.totalgrid.reef.services.ServiceDependencies

@RunWith(classOf[JUnitRunner])
class ProcessStatusCoordinatorTest extends DatabaseUsingTestBase {

  class CountingSubscriptionHandler extends ServiceSubscriptionHandler {
    var count = new SyncVar(0: Int)
    var lastEvent: Option[Envelope.Event] = None
    var lastKey: Option[String] = None
    var lastMessage: Option[GeneratedMessage] = None

    def publish(event: Envelope.Event, resp: GeneratedMessage, key: String) {
      lastEvent = Some(event)
      lastKey = Some(key)
      lastMessage = Some(resp)
      count.update(count.current + 1)
    }

    def bind(subQueue: String, key: String) {}

    def waitForNEvents(n: Int): Boolean = {
      // TODO: remove precondition check when syncvar is fixed
      if (count.current == n) return true
      count.waitUntil(n)
    }
  }

  class CountingEventPublishers extends ServiceEventPublisherMap(ReefServicesList) {
    def createPublisher(exchange: String): ServiceSubscriptionHandler = {
      new CountingSubscriptionHandler
    }
  }

  class ProcessStatusFixture {

    val pubs = new CountingEventPublishers

    val modelFac = new ModelFactories(ServiceDependencies(pubs))

    val service = new ProcessStatusService(modelFac.procStatus)

    val appService = new ApplicationConfigService(modelFac.appConfig)

    val processStatusCoordinator = new ProcessStatusCoordinator(modelFac.procStatus)

    val eventSink = pubs.getEventSink(classOf[StatusSnapshot]).asInstanceOf[CountingSubscriptionHandler]

    val app = enrollApp("processId1")

    def namedProto: StatusSnapshot.Builder = {
      StatusSnapshot.newBuilder().setInstanceName("fep01")
    }

    def enrollApp(processId: String) = {
      val b = ApplicationConfig.newBuilder
      b.setUserName("fep").setInstanceName("fep01").setNetwork("any")
      b.setLocation("any").addCapabilites("FEP").setProcessId(processId)
      appService.put(b.build).expectOne()
    }

    def coord = processStatusCoordinator
  }

  test("Add Heartbeats") {

    val fix = new ProcessStatusFixture
    val ss = fix.service.get(fix.namedProto.build).expectOne()
    ss.getOnline should equal(true)

    fix.eventSink.waitForNEvents(1)
    fix.eventSink.lastEvent should equal(Some(Envelope.Event.ADDED))
  }

  test("Warns on unknown heartbeats") {

    val fix = new ProcessStatusFixture

    val beat = fix.namedProto.setProcessId("11111").setTime(0).build

    fix.coord.handleRawStatus(beat)

    fix.service.get(StatusSnapshot.newBuilder().setProcessId("11111").build).expectNone()
  }

  test("Raw messages are correctly handled") {

    val fix = new ProcessStatusFixture

    fix.eventSink.waitForNEvents(1)
    fix.eventSink.lastEvent should equal(Some(Envelope.Event.ADDED))

    val ss = fix.service.get(fix.namedProto.build).expectOne()
    ss.getOnline should equal(true)
    val failsAt = ss.getTime

    // simulate a raw message received before the timeout
    val beat = fix.namedProto.setProcessId(fix.app.getHeartbeatCfg.getProcessId).setOnline(true).setTime(failsAt - 1).build
    fix.coord.handleRawStatus(beat)

    val ss2 = fix.service.get(fix.namedProto.build).expectOne()
    ss2.getOnline should equal(true)
    ss2.getTime should equal(failsAt - 1 + fix.app.getHeartbeatCfg.getPeriodMs * 2)

    // simulate a raw message received far in the future
    val failTime = failsAt + 5 * fix.app.getHeartbeatCfg.getPeriodMs
    val beat2 = fix.namedProto.setProcessId(fix.app.getHeartbeatCfg.getProcessId).setOnline(false).setTime(failTime).build
    fix.coord.handleRawStatus(beat2)

    // since it should have now failed, we should have seen a modified offline message 
    fix.eventSink.waitForNEvents(2)
    fix.eventSink.lastEvent should equal(Some(Envelope.Event.MODIFIED))

    val ss3 = fix.service.get(fix.namedProto.build).expectOne()
    ss3.getOnline should equal(false)
    ss3.getTime should equal(failTime)
  }

  test("Heartbeats expire") {

    val fix = new ProcessStatusFixture

    val ss = fix.service.get(fix.namedProto.build).expectOne()
    ss.getOnline should equal(true)
    val failsAt = ss.getTime

    fix.eventSink.waitForNEvents(1)
    fix.eventSink.lastEvent should equal(Some(Envelope.Event.ADDED))

    // hasn't timeout out yet, no failure, no new events
    fix.coord.checkTimeouts(failsAt - 1)
    fix.eventSink.waitForNEvents(1)

    // check again, just after the timeout
    fix.coord.checkTimeouts(failsAt + 10)
    fix.eventSink.waitForNEvents(2)
    fix.eventSink.lastEvent should equal(Some(Envelope.Event.MODIFIED))

    val ss2 = fix.service.get(fix.namedProto.build).expectOne()
    ss2.getOnline should equal(false)
    ss2.getTime should equal(failsAt + 10)
  }

  test("Old offline=false doesnt stop new app") {

    val fix = new ProcessStatusFixture

    val ss = fix.service.get(fix.namedProto.build).expectOne()
    ss.getOnline should equal(true)
    val failsAt = ss.getTime

    fix.enrollApp("processId2")

    val offlineHeartBeat = fix.namedProto.setProcessId("processId1").setOnline(false).setTime(failsAt).build
    fix.coord.handleRawStatus(offlineHeartBeat)

    val ss2 = fix.service.get(fix.namedProto.build).expectOne()
    ss2.getOnline should equal(true)
  }
}