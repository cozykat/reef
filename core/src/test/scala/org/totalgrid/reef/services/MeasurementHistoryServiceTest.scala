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
package org.totalgrid.reef.services

import org.totalgrid.reef.measurementstore.Historian
import org.totalgrid.reef.measurementstore.MeasSink.Meas
import org.totalgrid.reef.proto.Measurements
import org.totalgrid.reef.proto.Measurements.MeasurementHistory

import org.scalatest.{ FunSuite, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.totalgrid.reef.messaging.mock.AMQPFixture

import org.totalgrid.reef.protoapi.ProtoServiceTypes

class FakeHistorian(map: Map[String, List[Meas]]) extends Historian {
  var begin: Long = -1
  var end: Long = -1
  var max: Long = -1
  var ascending = false
  def getInRange(name: String, b: Long, e: Long, m: Int, a: Boolean): Seq[Meas] = {
    begin = b; end = e; max = m; ascending = a
    map.get(name) match {
      case Some(l) => l
      case None => Nil
    }
  }

  def numValues(name: String): Int = { throw new Exception }
  def remove(names: Seq[String]): Unit = { throw new Exception }
}

@RunWith(classOf[JUnitRunner])
class MeasurementHistoryServiceTest extends FunSuite with ShouldMatchers with BeforeAndAfterAll {

  def getMeas(name: String, time: Int, value: Int) = {
    val meas = Measurements.Measurement.newBuilder
    meas.setName(name).setType(Measurements.Measurement.Type.INT).setIntVal(value)
    meas.setQuality(Measurements.Quality.newBuilder.build)
    meas.setTime(time)
    meas.build
  }

  def validateHistorian(historian: FakeHistorian, begin: Long, end: Long, max: Int, ascending: Boolean) = {
    historian.begin should equal(begin)
    historian.end should equal(end)
    historian.max should equal(max)
    historian.ascending should equal(ascending)
  }

  test("History Service defaults are sensible") {
    AMQPFixture.mock(true) { amqp =>
      val points = Map("meas1" -> List(getMeas("meas1", 0, 1), getMeas("meas1", 1, 1)))
      val historian = new FakeHistorian(points)
      val service = new MeasurementHistoryService(historian)
      amqp.bindService("test", service.respond)

      val client = amqp.getProtoServiceClient("test", 500000, MeasurementHistory.parseFrom)

      val getMeas1 = client.getOneOrThrow(MeasurementHistory.newBuilder().setPointName("meas1").build)
      getMeas1.getMeasurementsCount() should equal(2)
      validateHistorian(historian, 0, Long.MaxValue, service.HISTORY_LIMIT, true)

      client.getOneOrThrow(MeasurementHistory.newBuilder().setPointName("meas1").setStartTime(10).setEndTime(1000).build)
      validateHistorian(historian, 10, 1000, service.HISTORY_LIMIT, true)

      client.getOneOrThrow(MeasurementHistory.newBuilder().setPointName("meas1").setAscending(false).setLimit(99).build)
      validateHistorian(historian, 0, Long.MaxValue, 99, false)
    }
  }
}