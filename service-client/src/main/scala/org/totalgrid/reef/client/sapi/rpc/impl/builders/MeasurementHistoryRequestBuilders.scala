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

import org.totalgrid.reef.client.service.proto.Measurements.MeasurementHistory
import org.totalgrid.reef.client.service.proto.Model.{ ReefUUID, Point }

object MeasurementHistoryRequestBuilders {
  def getByName(pointName: String, limit: Int) =
    MeasurementHistory.newBuilder.setPointName(pointName).setLimit(limit).build

  def getByNameSince(pointName: String, since: Long, limit: Int) =
    MeasurementHistory.newBuilder.setPointName(pointName).setLimit(limit).setStartTime(since).build

  def getByNameBetween(pointName: String, since: Long, before: Long, returnNewest: Boolean, limit: Int) =
    MeasurementHistory.newBuilder.setPointName(pointName).setLimit(limit).setStartTime(since).setEndTime(before).setKeepNewest(returnNewest).build

  def getByUuid(uuid: ReefUUID, limit: Int) =
    MeasurementHistory.newBuilder.setPoint(Point.newBuilder.setUuid(uuid)).setLimit(limit).build

  def getByUuidSince(uuid: ReefUUID, since: Long, limit: Int) =
    MeasurementHistory.newBuilder.setPoint(Point.newBuilder.setUuid(uuid)).setLimit(limit).setStartTime(since).build

  def getByUuidBetween(uuid: ReefUUID, since: Long, before: Long, returnNewest: Boolean, limit: Int) =
    MeasurementHistory.newBuilder.setPoint(Point.newBuilder.setUuid(uuid)).setLimit(limit).setStartTime(since).setEndTime(before).setKeepNewest(returnNewest).build
}