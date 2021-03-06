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

import scala.collection.JavaConversions._
import org.totalgrid.reef.client.service.proto.Model.{ ReefUUID, Point }
import org.totalgrid.reef.client.service.proto.Measurements.MeasurementSnapshot

object MeasurementSnapshotRequestBuilders {
  def getByName(name: String) = MeasurementSnapshot.newBuilder.addPointNames(name).build
  def getByPoint(point: Point) = MeasurementSnapshot.newBuilder.addPoint(point).build
  def getByUuid(uuid: ReefUUID) = MeasurementSnapshot.newBuilder.addPoint(Point.newBuilder.setUuid(uuid)).build

  def getByNames(names: List[String]): MeasurementSnapshot = getByNames(names: java.util.List[String])
  def getByNames(names: java.util.List[String]): MeasurementSnapshot = MeasurementSnapshot.newBuilder.addAllPointNames(names).build

  def getByPoints(points: List[Point]): MeasurementSnapshot = MeasurementSnapshot.newBuilder.addAllPoint(points).build
  def getByPoints(points: java.util.List[Point]): MeasurementSnapshot = getByPoints(points.toList)

  def getByUuids(uuids: List[ReefUUID]): MeasurementSnapshot = MeasurementSnapshot.newBuilder.addAllPoint(uuids.map { Point.newBuilder.setUuid(_).build }).build
  def getByUuids(uuids: java.util.List[ReefUUID]): MeasurementSnapshot = getByUuids(uuids.toList)
}