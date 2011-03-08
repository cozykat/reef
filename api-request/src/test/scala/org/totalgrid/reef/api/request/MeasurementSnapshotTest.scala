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
package org.totalgrid.reef.api.request

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.JavaConversions._
import org.totalgrid.reef.proto.Model.{ Point, Entity, Relationship }
import org.totalgrid.reef.proto.Measurements.{ MeasurementSnapshot }

@RunWith(classOf[JUnitRunner])
class MeasurementSnapshotTest
    extends ServiceClientSuite("MeasurementSnapshot.xml", "MeasurementSnapshot",
      <div>
        <p>
          The MeasurementSnapshot service provides the current state of measurements. The request contains the
          list of measurement names, the response contains the requested measurement objects.
        </p>
      </div>)
    with ShouldMatchers {

  test("Simple gets") {

    val points = List("StaticSubstation.Line02.Current", "StaticSubstation.Breaker02.Bkr", "StaticSubstation.Breaker02.Tripped")

    client.addExplanation("Get single measurement", "Get the current state of a single measurement.")
    client.getOneOrThrow(MeasurementSnapshotRequestBuilders.getByName(points.head))

    client.addExplanation("Get multiple measurements", "Get the current state of multiple measurements. Notice that they are all returned wrapped in a single parent object.")
    client.getOneOrThrow(MeasurementSnapshotRequestBuilders.getByName(points))

  }
}
