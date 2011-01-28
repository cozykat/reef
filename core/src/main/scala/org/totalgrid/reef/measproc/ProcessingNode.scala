/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.measproc

import org.totalgrid.reef.persistence.{ ObjectCache, KeyValue }
import org.totalgrid.reef.util.{ HookableObject, MetricsHookContainer, Logging }
import org.totalgrid.reef.reactor.ReactActor

import org.totalgrid.reef.messaging.RoutingKeys
import org.totalgrid.reef.app.{ ServiceHandler, ServiceHandlerProvider, SubscriptionProvider }
import org.totalgrid.reef.proto.{ Measurements, Processing, FEP, Events, Model }
import org.totalgrid.reef.util.MetricsHooks

import Measurements._
import Processing._
import FEP._
import Model._

import scala.collection.immutable
import org.totalgrid.reef.messaging.{ RoutingKeys, ProtoRegistry, AMQPProtoFactory, AMQPProtoRegistry }
import org.totalgrid.reef.app.{ ServiceHandlerProvider, ServiceHandler }

trait ProcessingNode {
  def process(m: MeasurementBatch)

  def add(over: MeasOverride)
  def remove(over: MeasOverride)

  def add(set: TriggerSet)
  def remove(set: TriggerSet)

}

class BasicProcessingNode(procFun: Measurement => Unit, flushCache: () => Unit)
    extends MetricsHooks with Logging {

  protected lazy val measProcessingTime = timingHook[Unit]("measProcessingTime")
  protected lazy val measProcessed = counterHook("measProcessed")

  def process(b: MeasurementBatch) = {
    ProcessingNode.debatch(b) { meas =>
      measProcessingTime {
        debug("Processing: " + meas)
        procFun(meas)
      }
    }
    flushCache()
    measProcessed(b.getMeasCount)
  }
}
class MeasurementProcessor(
    publish: Measurement => Unit,
    measCache: ObjectCache[Measurement],
    overCache: ObjectCache[Measurement],
    stateCache: ObjectCache[Boolean],
    eventSubsystem: String,
    eventSink: Events.Event => Unit) extends ProcessingNode with MetricsHookContainer {
  val triggerFactory = new processing.TriggerProcessingFactory(eventSubsystem, eventSink)

  // List of measurements that get built during a batch process
  var list: List[Measurement] = Nil

  // function that builds the list and publishes to the bus
  def pubMeas(m: Measurement) = {
    list = m :: list
    publish(m)
  }

  // flushes the list to the measurement cache
  def flushCache() = {
    val publist = list.reverse.map { m => KeyValue(m.getName, m) }
    list = Nil
    measCache.put(publist)
  }

  val triggerProc = new processing.TriggerProcessor(pubMeas, triggerFactory, stateCache)
  val overProc = new processing.OverrideProcessor(triggerProc.process, overCache, measCache.get)

  val processor = new BasicProcessingNode(overProc.process, flushCache)
  addHookedObject(processor :: overProc :: triggerProc :: Nil)

  def process(b: MeasurementBatch) = processor.process(b)

  def add(over: MeasOverride) = { overProc.add(over) }
  def remove(over: MeasOverride) = { overProc.remove(over) }

  def add(set: TriggerSet) = { triggerProc.add(set) }
  def remove(set: TriggerSet) = { triggerProc.remove(set) }
}

object ProcessingNode {

  def debatch[A](b: MeasurementBatch)(f: Measurement => A) = {
    import scala.collection.JavaConversions._

    b.getMeasList.map { m =>
      if (m.hasTime) f(m)
      else f(Measurement.newBuilder(m).setTime(b.getWallTime).build)
    }
  }

  def apply(
    publish: Measurement => Unit,
    pointSource: Model.Entity,
    provider: SubscriptionProvider,
    measCache: ObjectCache[Measurement],
    overCache: ObjectCache[Measurement],
    stateCache: ObjectCache[Boolean],
    eventSubsystem: String,
    eventSink: Events.Event => Unit,
    start: () => Unit) = {

    val processingObject = new MeasurementProcessor(publish, measCache, overCache, stateCache, eventSubsystem, eventSink)

    val pointProto = Point.newBuilder.setLogicalNode(pointSource).build
    val multiNotify = new CompoundNotifier
    processingObject.triggerProc.subscribe(provider, pointProto, multiNotify.notifier)
    processingObject.overProc.subscribe(provider, pointProto, multiNotify.notifier)

    multiNotify.observe { start() }

    processingObject
  }
}

