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
package org.totalgrid.reef.protocol.integration.dnp3

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.totalgrid.reef.util.SyncVar
import org.totalgrid.reef.client.service.proto.Model.CommandType
import org.totalgrid.reef.client.{ SubscriptionEvent, SubscriptionEventAcceptor }
import org.totalgrid.reef.client.service.proto.FEP.EndpointConnection.State._
import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import org.totalgrid.reef.client.service.proto.Measurements.Quality.Validity
import org.totalgrid.reef.client.sapi.rpc.impl.util.{ EndpointConnectionStateMap, ServiceClientSuite }

@RunWith(classOf[JUnitRunner])
class Dnp3ProtocolTest extends ServiceClientSuite {

  override val modelFile = "../../protocol-dnp3/src/test/resources/sample-model.xml"

  test("Cycle endpoints") {
    val endpoints = client.getEndpoints().await.toList

    endpoints.isEmpty should equal(false)

    val result = client.subscribeToEndpointConnections().await

    val map = new EndpointConnectionStateMap(result)

    // make sure everything starts comms_up and enabled
    map.checkAllState(true, COMMS_UP)

    (1 to 5).foreach { i =>

      val start = System.currentTimeMillis()
      endpoints.foreach { e => client.disableEndpointConnection(e.getUuid).await }

      map.checkAllState(false, COMMS_DOWN)
      val disabled = System.currentTimeMillis()
      println("Disabled to COMMS_DOWN in: " + (disabled - start))

      endpoints.foreach { e => client.enableEndpointConnection(e.getUuid).await }

      map.checkAllState(true, COMMS_UP)
      val enabled = System.currentTimeMillis()
      println("Enabled to COMMS_UP in: " + (enabled - disabled))

      waitForRepublishedMeasurement()
      println("COMMS_UP to first measurement roundtripped in: " + (System.currentTimeMillis() - enabled))
    }
  }

  test("Issue Commands") {
    val endpoint = client.getEndpointByName("DNPInput").await
    val commands = client.getCommandsBelongingToEndpoint(endpoint.getUuid).await.toList

    val lock = client.createCommandExecutionLock(commands).await
    try {
      commands.foreach { cmd =>
        cmd.getType match {
          case CommandType.CONTROL => client.executeCommandAsControl(cmd).await
          case CommandType.SETPOINT_DOUBLE => client.executeCommandAsSetpoint(cmd, 55.55).await
          case CommandType.SETPOINT_INT => client.executeCommandAsSetpoint(cmd, 100).await
          case CommandType.SETPOINT_STRING => client.executeCommandAsSetpoint(cmd, "TestString").await
        }
      }
    } finally {
      client.deleteCommandLock(lock).await
    }
  }

  private def setupMeasSubscribe(names: List[String]): Map[String, SyncVar[List[Measurement]]] = {
    def validMeas(meas: Measurement) = meas.getQuality.getValidity == Validity.GOOD

    val syncVars = names.map { _ -> new SyncVar(List.empty[Measurement]) }.toMap

    val subResult = client.subscribeToMeasurementsByNames(names).await
    subResult.getSubscription.start(new SubscriptionEventAcceptor[Measurement] {
      def onEvent(event: SubscriptionEvent[Measurement]) {
        val m = event.getValue
        if (validMeas(m)) syncVars(m.getName).atomic(a => m :: a)
      }
    })

    syncVars
  }

  private def waitForRepublishedMeasurement() {

    val originalMeas = setupMeasSubscribe("OriginalSubstation.Line01.Current" :: "OriginalSubstation.Line01.ScaledCurrent" :: Nil)

    val roundtripMeas = setupMeasSubscribe("RoundtripSubstation.Line01.Current" :: "RoundtripSubstation.Line01.ScaledCurrent" :: Nil)

    def checkRoundtrip(originalName: String, roundtripName: String, scaling: Double = 1.0) {

      val originalEvents = originalMeas(originalName)
      val roundtrip = roundtripMeas(roundtripName)

      originalEvents.waitFor(_.size > 0)

      val meas = originalEvents.current.apply(0)

      def dEqual(d1: Double, d2: Double, ep: Double = 0.0001) = scala.math.abs(d1 - d2) <= ep

      roundtrip.waitFor(_.find(m => dEqual(m.getDoubleVal, meas.getDoubleVal * scaling) && m.getTime == meas.getTime).isDefined)
    }

    checkRoundtrip("OriginalSubstation.Line01.Current", "RoundtripSubstation.Line01.Current")

    checkRoundtrip("OriginalSubstation.Line01.ScaledCurrent", "RoundtripSubstation.Line01.ScaledCurrent", 1000.0)
  }

}