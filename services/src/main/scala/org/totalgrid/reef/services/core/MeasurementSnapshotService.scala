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

import org.totalgrid.reef.proto.Descriptors

import org.totalgrid.reef.proto.Measurements.MeasurementSnapshot

import scala.collection.JavaConversions._

import org.totalgrid.reef.measurementstore.RTDatabase

import org.totalgrid.reef.messaging.serviceprovider.{ ServiceEventPublishers, ServiceSubscriptionHandler }
import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.sapi.client.Response
import org.totalgrid.reef.sapi.service.SyncServiceBase
import org.totalgrid.reef.japi.{ ExpectationException, BadRequestException, Envelope }

class MeasurementSnapshotService(cm: RTDatabase, subHandler: ServiceSubscriptionHandler) extends SyncServiceBase[MeasurementSnapshot] {

  def this(cm: RTDatabase, pubs: ServiceEventPublishers) = this(cm, pubs.getEventSink(classOf[MeasurementSnapshot]))

  override val descriptor = Descriptors.measurementSnapshot

  override def get(req: MeasurementSnapshot, env: RequestEnv): Response[MeasurementSnapshot] = {

    val measList = req.getPointNamesList().toList

    env.subQueue.foreach(subQueue => measList.map(_.replace("*", "#")).foreach(key => subHandler.bind(subQueue, key)))

    val searchList = if (measList.size == 1 && measList.head == "*") {
      Nil // TODO: get list of all points from other source
    } else {
      measList
    }

    val b = MeasurementSnapshot.newBuilder()
    // clients shouldn't ask for 0 measurements but if they do we should just return a blank rather than an error.
    if (searchList.size > 0) {
      val measurements = cm.get(searchList).values()
      val foundNames = measurements.map(_.getName).toList

      val missing = searchList.diff(foundNames)
      if (!missing.isEmpty) {
        throw new BadRequestException("Couldn't find measurements: " + missing.mkString(", "))
      }

      b.addAllPointNames(foundNames)
      b.addAllMeasurements(measurements)
    }
    Response(Envelope.Status.OK, b.build :: Nil)
  }

}