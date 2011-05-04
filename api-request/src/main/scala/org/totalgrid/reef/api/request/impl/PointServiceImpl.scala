/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.api.request.impl

import scala.collection.JavaConversions._

import org.totalgrid.reef.api.request.{ PointService }
import org.totalgrid.reef.proto.Model.{ Point, Entity, ReefUUID }
import org.totalgrid.reef.api.ISubscription
import org.totalgrid.reef.api.request.builders.PointRequestBuilders

trait PointServiceImpl extends ReefServiceBaseClass with PointService {

  def getAllPoints() = {
    ops.getOrThrow(PointRequestBuilders.getAll)
  }

  def getPointByName(name: String) = {
    reThrowExpectationException("Point not found.") {
      ops.getOneOrThrow(PointRequestBuilders.getByName(name))
    }
  }

  def getPointByUid(uuid: ReefUUID) = {
    reThrowExpectationException("Point not found.") {
      ops.getOneOrThrow(PointRequestBuilders.getByUid(uuid))
    }
  }

  def getPointsOwnedByEntity(parentEntity: Entity) = {
    ops.getOrThrow(PointRequestBuilders.getOwnedByEntity(parentEntity))
  }

  def getPointsBelongingToEndpoint(endpointUuid: ReefUUID) = {
    ops.getOrThrow(PointRequestBuilders.getSourcedByEndpoint(endpointUuid))
  }

  def getAbnormalPoints() = {
    ops.getOrThrow(PointRequestBuilders.getAbnormal)
  }

  def getAbnormalPoints(sub: ISubscription[Point]) = {
    ops.getOrThrow(PointRequestBuilders.getAbnormal, sub)
  }
}