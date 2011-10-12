package org.totalgrid.reef.sapi.request.impl

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
import org.totalgrid.reef.sapi.request.MeasurementOverrideService
import org.totalgrid.reef.proto.Model.Point
import org.totalgrid.reef.proto.Processing.MeasOverride
import org.totalgrid.reef.proto.Measurements.Measurement
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.japi.request.builders.MeasurementOverrideRequestBuilders

import org.totalgrid.reef.sapi.request.framework.ReefServiceBaseClass

trait MeasurementOverrideServiceImpl extends ReefServiceBaseClass with MeasurementOverrideService {

  override def setPointOutOfService(point: Point) = {
    ops("Couldn't set point uuid:" + point.uuid + " name: " + point.name + " out of service") {
      _.put(MeasurementOverrideRequestBuilders.makeNotInService(point)).map { _.expectOne }
    }
  }

  override def setPointOverride(point: Point, measurement: Measurement) = {
    ops("Couldn't override point uuid:" + point.uuid + " name: " + point.name + " to: " + measurement) {
      _.put(MeasurementOverrideRequestBuilders.makeOverride(point, measurement)).map { _.expectOne }
    }
  }

  override def deleteMeasurementOverride(measOverride: MeasOverride) = {
    // TODO: measurementOverride needs uid - backlog-63
    ops("Couldn't delete measurementOverride: " + measOverride.meas + " on: " + measOverride.point) {
      _.delete(measOverride).map { _.expectOne }
    }
  }

  override def clearMeasurementOverridesOnPoint(point: Point) = {
    ops("Couldn't clear measurementOverrides on point uuid: " + point.uuid + " name: " + point.name) {
      _.delete(MeasurementOverrideRequestBuilders.getByPoint(point)).map {
        _.expectMany() match {
          case List(one) => one
          case _ => null
        }
      }
    }
  }

}