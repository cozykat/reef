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
package org.totalgrid.reef.benchmarks.endpoints

import org.totalgrid.reef.client.service.proto.FEP.EndpointConnection
import org.totalgrid.reef.util.SyncVar
import org.totalgrid.reef.client.service.proto.Model.ReefUUID
import org.totalgrid.reef.client.{ SubscriptionEvent, SubscriptionEventAcceptor, SubscriptionResult }
import org.totalgrid.reef.benchmarks.FailedBenchmarkException
import org.totalgrid.reef.util.Timing.Stopwatch

class EndpointStateTransitionTimer(result: SubscriptionResult[List[EndpointConnection], EndpointConnection], endpointUuids: List[ReefUUID]) extends SubscriptionEventAcceptor[EndpointConnection] {

  case class TimingInfo(var stateTime: Option[Long], var enabledTime: Option[Long], var routingKeyTime: Option[Long])

  val stopwatch = new Stopwatch()
  val endpointStateMap = result.getResult.filter { e => endpointUuids.find(_ == e.getEndpoint.getUuid).isDefined }.map { e =>
    e.getEndpoint.getUuid -> (e, TimingInfo(None, None, None))
  }.toMap
  val syncVar = new SyncVar(endpointStateMap)

  result.getSubscription.start(this)

  def onEvent(ea: SubscriptionEvent[EndpointConnection]) = syncVar.atomic { m =>
    val update = ea.getValue
    m.get(update.getEndpoint.getUuid) match {
      case Some(tuple) =>
        val e = tuple._1
        val timing = tuple._2
        if (update.getState != e.getState) timing.stateTime = Some(stopwatch.elapsed)
        if (update.getEnabled != e.getEnabled) timing.enabledTime = Some(stopwatch.elapsed)
        if (update.getRouting.getServiceRoutingKey != e.getRouting.getServiceRoutingKey) timing.routingKeyTime = Some(stopwatch.elapsed)
        m + (update.getEndpoint.getUuid -> (update, timing))
      case None =>
        // not one of the endpoints we are monitoring
        m
    }
  }

  def start {
    stopwatch.reset()
  }

  def checkAllState(enabled: Boolean, state: EndpointConnection.State) {
    if (!syncVar.waitFor(x => x.values.forall(e => e._1.getEnabled == enabled && e._1.getState == state), 20000, false)) {
      throw new FailedBenchmarkException("Not all endpoints made it to enabled: " + enabled + " state: " + state + " - " + finalStateAsString)
    }
  }
  def checkState(uuid: ReefUUID, enabled: Boolean, state: EndpointConnection.State) {
    if (!syncVar.waitFor(x => x.get(uuid).map(e => e._1.getEnabled == enabled && e._1.getState == state).getOrElse(false), 20000, false)) {
      throw new FailedBenchmarkException("Endpoints " + uuid + " didnt make it to enabled: " + enabled + " state: " + state + " - " + finalStateAsString)
    }
  }

  private def finalStateAsString = {
    syncVar.current.values.map { e => "(" + e._1.getEndpoint.getName + " e: " + e._1.getEnabled + " s: " + e._1.getState + ")" }.mkString(",")
  }

  /**
   * convert the TimingInfo to the output EndpointCycleReading
   */
  def getStateReadings: List[EndpointCycleReading] = {
    val map = syncVar.current
    map.map {
      case (uuid, tuple) =>
        EndpointCycleReading(tuple._1.getEndpoint.getName, tuple._1.getEndpoint.getProtocol, tuple._1.getState, tuple._2.stateTime.get)
    }.toList
  }

}