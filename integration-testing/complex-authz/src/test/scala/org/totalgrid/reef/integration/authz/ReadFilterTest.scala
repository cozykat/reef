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
package org.totalgrid.reef.integration.authz

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.totalgrid.reef.client.settings.NodeSettings
import concurrent.ops

@RunWith(classOf[JUnitRunner])
class ReadFilterTest extends AuthTestBase {

  override val modelFile = "../../assemblies/assembly-common/filtered-resources/samples/authorization/config.xml"

  test("See only entities we're allowed to") {
    as("limited_regional_op") { ops =>

      val entNames = ops.getEntities().await.map(_.getName)
      val expected = List("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "Sub1", "Sub2", "Sub3", "Sub4", "East", "West", "limited_regional_op")
      entNames.toSet should equal(expected.toSet)
    }
  }

}
