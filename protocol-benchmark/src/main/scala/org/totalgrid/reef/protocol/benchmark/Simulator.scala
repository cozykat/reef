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
package org.totalgrid.reef.protocol.benchmark

import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.reactor.{ Reactable, Lifecycle, DelayHandler }

import java.util.Random
import scala.collection.JavaConversions._

import org.totalgrid.reef.proto.{ SimMapping, Measurements, Commands }
import Measurements.{ Measurement => Meas }

import org.totalgrid.reef.util.Conversion.convertIterableToMapified

import org.totalgrid.reef.protocol.api.IProtocol

class Simulator(name: String, publish: IProtocol.Publish, respondFun: IProtocol.Respond, config: SimMapping.SimulatorMapping, reactor: Reactable) extends Lifecycle with Logging {

  case class MeasRecord(name: String, unit: String, currentValue: CurrentValue[_])

  private val delay = config.getDelay
  private val batchSize = config.getBatchSize

  private val measurements = config.getMeasurementsList.map { x => MeasRecord(x.getName, x.getUnit, getValueHolder(x)) }.toList
  private val cmdMap = config.getCommandsList.map { x => x.getName -> x.getResponseStatus }.toMap

  private val rand = new Random
  private var repeater: Option[DelayHandler] = None

  override def afterStart() {
    reactor.execute { update(measurements, true) }
    setUpdateParams(delay, batchSize)
  }
  override def beforeStop() {
    repeater.foreach { _.cancel }
  }

  def setUpdateParams(newDelay: Int, newBatchSize: Int) = reactor.execute {
    info { "Updating parameters for " + name + ": delay = " + delay + " batch_size = " + batchSize }
    repeater.foreach(_.cancel)
    if (delay > 0) {
      repeater = Some(reactor.repeat(delay) {
        // Pick batchSize random values to update from the map
        val meases = for (i <- 1 to batchSize) yield measurements(rand.nextInt(measurements.size))
        update(meases.toList)
      })
    }
  }

  def update(meases: List[MeasRecord], force: Boolean = false): Unit = {
    val batch = Measurements.MeasurementBatch.newBuilder.setWallTime(System.currentTimeMillis)
    meases.foreach { meas =>
      if (meas.currentValue.next(force)) {
        batch.addMeas(getMeas(meas))
      }
    }
    if (batch.getMeasCount > 0) {
      debug { "publishing batch of size: " + batch.getMeasCount }
      publish(batch.build)
    }
  }

  /** Generate a random measurement */
  private def getMeas(meas: MeasRecord): Meas = {
    val point = Measurements.Measurement.newBuilder.setName(meas.name)
      .setQuality(Measurements.Quality.newBuilder.build)
      .setUnit(meas.unit)

    meas.currentValue.apply(point)

    point.build
  }

  def issue(cr: Commands.CommandRequest): Unit = reactor.execute {
    cmdMap.get(cr.getName) match {
      case Some(x) =>
        info { "handled command:" + cr }
        val rsp = Commands.CommandResponse.newBuilder
        rsp.setCorrelationId(cr.getCorrelationId).setStatus(x)
        respondFun(rsp.build)
      case None =>
    }
  }

  /////////////////////////////////////////////////
  // Simple random walk simulation components
  /////////////////////////////////////////////////

  def getValueHolder(config: SimMapping.MeasSim): CurrentValue[_] = {
    config.getType match {
      case Meas.Type.BOOL => BooleanValue(config.getInitial.toInt == 0, config.getChangeChance)
      case Meas.Type.DOUBLE => DoubleValue(config.getInitial, config.getMin, config.getMax, config.getMaxDelta, config.getChangeChance)
      case Meas.Type.INT => IntValue(config.getInitial.toInt, config.getMin.toInt, config.getMax.toInt, config.getMaxDelta.toInt, config.getChangeChance)
    }
  }

  abstract class CurrentValue[T](var value: T, val changeChance: Double) {

    def next(force: Boolean): Boolean = {
      if (force) return true
      if (rand.nextDouble > changeChance) return false
      val original = value
      _next
      original != value
    }

    def _next()
    def apply(meas: Measurements.Measurement.Builder)
  }
  case class DoubleValue(initial: Double, min: Double, max: Double, maxChange: Double, cc: Double) extends CurrentValue[Double](initial, cc) {
    def _next() = value = (value + maxChange * 2 * ((rand.nextDouble - 0.5))).max(min).min(max)
    def apply(meas: Measurements.Measurement.Builder) = meas.setDoubleVal(value).setType(Meas.Type.DOUBLE)
  }
  case class IntValue(initial: Int, min: Int, max: Int, maxChange: Int, cc: Double) extends CurrentValue[Int](initial, cc) {
    def _next() = value = (value + rand.nextInt(2 * maxChange + 1) - maxChange).max(min).min(max)
    def apply(meas: Measurements.Measurement.Builder) = meas.setIntVal(value).setType(Meas.Type.INT)
  }
  case class BooleanValue(initial: Boolean, cc: Double) extends CurrentValue[Boolean](initial, cc) {
    def _next() = value = !value
    def apply(meas: Measurements.Measurement.Builder) = meas.setBoolVal(value) setType (Meas.Type.BOOL)
  }

}