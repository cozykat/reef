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
package org.totalgrid.reef.measproc

import org.totalgrid.reef.util.Logging

import org.totalgrid.reef.event.EventType._
import org.totalgrid.reef.frontend.KeyedMap

import org.totalgrid.reef.executor.{ Executor, Lifecycle, LifecycleManager }
import org.totalgrid.reef.proto.Measurements._
import org.totalgrid.reef.proto.Processing.{ MeasurementProcessingConnection => ConnProto }

import org.totalgrid.reef.executor.ReactActorExecutor
import org.totalgrid.reef.app.{ ServiceHandler, CoreApplicationComponents, ServiceContext }
import org.totalgrid.reef.util.BuildEnv.ConnInfo
import org.totalgrid.reef.persistence.{ InMemoryObjectCache }
import org.totalgrid.reef.measurementstore.{ MeasurementStoreToMeasurementCacheAdapter, MeasurementStoreFinder }

abstract class ConnectionHandler(fun: ConnProto => MeasurementStreamProcessingNode)
    extends ServiceHandler with ServiceContext[ConnProto] with KeyedMap[ConnProto]
    with Executor with Lifecycle {

  protected override def getKey(c: ConnProto) = c.getUid

  private var map = Map.empty[String, MeasurementStreamProcessingNode]

  override def addEntry(ep: ConnProto) = {
    val entry = fun(ep)
    map += getKey(ep) -> entry
    entry.start
  }

  override def removeEntry(ep: ConnProto) = {
    map.get(getKey(ep)).get.stop
    map -= getKey(ep)
  }

  override def hasChangedEnoughForReload(updated: ConnProto, existing: ConnProto) = {
    updated.getAssignedTime != existing.getAssignedTime
  }
}

/**
 *  Non-entry point meas processor setup
 */
class FullProcessor(components: CoreApplicationComponents, measStoreConfig: ConnInfo) extends Logging with Lifecycle {

  var lifecycles = new LifecycleManager(List(components.heartbeatActor))

  // caches used to store measurements and overrides
  val measStore = MeasurementStoreFinder.getInstance(measStoreConfig, lifecycles.add _)
  val measCache = new MeasurementStoreToMeasurementCacheAdapter(measStore)

  // TODO: make override caches configurable like measurement store

  val overCache = new InMemoryObjectCache[Measurement]
  val triggerStateCache = new InMemoryObjectCache[Boolean]

  val connectionHandler = new ConnectionHandler(addStreamProcessor(_)) with ReactActorExecutor

  override def doStart() {
    components.logger.event(System.SubsystemStarting)
    lifecycles.start
    subscribeToStreams
    components.logger.event(System.SubsystemStarted)
  }

  override def doStop() {
    components.logger.event(System.SubsystemStopping)
    connectionHandler.clear
    lifecycles.stop
    components.logger.event(System.SubsystemStopped)
  }

  def addStreamProcessor(streamConfig: ConnProto): MeasurementStreamProcessingNode = {
    val reactor = new ReactActorExecutor {}
    val streamHandler = new MeasurementStreamProcessingNode(components.amqp, components.registry, measCache, overCache, triggerStateCache, streamConfig, reactor)
    streamHandler.setHookSource(components.metricsPublisher.getStore("measproc-" + streamConfig.getLogicalNode.getName))
    streamHandler
  }

  def subscribeToStreams() = {
    val connection = ConnProto.newBuilder.setMeasProc(components.appConfig).build
    connectionHandler.addServiceContext(components.registry, 5000, ConnProto.parseFrom, connection, connectionHandler)
    connectionHandler.start
  }
}
