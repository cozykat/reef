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
package org.totalgrid.reef.client.sapi.rpc.impl.builders

import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import org.totalgrid.reef.client.service.proto.Processing.MeasOverride
import org.totalgrid.reef.client.service.proto.Model.{ ReefID, ReefUUID, Point }

object MeasurementOverrideRequestBuilders {

  def makePoint(uuid: ReefUUID) = Point.newBuilder.setUuid(uuid).build

  def makeOverride(point: Point, measurement: Measurement) = {
    MeasOverride.newBuilder.setPoint(point).setMeas(measurement).build
  }
  def makeNotInService(point: Point) = MeasOverride.newBuilder.setPoint(point).build

  def getByPoint(point: Point) = MeasOverride.newBuilder.setPoint(point).build

  def getById(id: ReefID) = MeasOverride.newBuilder.setId(id).build
}