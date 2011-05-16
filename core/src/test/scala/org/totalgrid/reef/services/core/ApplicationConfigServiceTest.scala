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
package org.totalgrid.reef.services.core

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.totalgrid.reef.proto.Application._
import org.totalgrid.reef.messaging.ProtoSerializer.convertStringToByteString

import org.totalgrid.reef.services.ServiceResponseTestingHelpers._
import org.totalgrid.reef.messaging.serviceprovider.SilentEventPublishers
import org.totalgrid.reef.api.Envelope.Status
import org.totalgrid.reef.models.DatabaseUsingTestBase
import org.totalgrid.reef.proto.Model.ReefUUID

@RunWith(classOf[JUnitRunner])
class ApplicationConfigServiceTest extends DatabaseUsingTestBase {

  test("GetPutDelete") {

    val modelFac = new ModelFactories(new SilentEventPublishers, new SilentSummaryPoints)

    val service = new ApplicationConfigService(modelFac.appConfig)

    val b = ApplicationConfig.newBuilder
      .setUserName("fep")
      .setInstanceName("fep01")
      .setNetwork("any")
      .setLocation("farm1")
      .addCapabilites("FEP")

    service.get(ApplicationConfig.newBuilder().setUuid(ReefUUID.newBuilder.setUuid("*")).build).size should equal(0)

    one(Status.CREATED, service.put(b.build))

    service.get(ApplicationConfig.newBuilder().setUuid(ReefUUID.newBuilder.setUuid("*")).build).size should equal(1)
    val list = service.get(ApplicationConfig.newBuilder().setInstanceName("fep01").build)
    list.size should equal(1)
    val updated = one(Status.UPDATED, service.put(list.head.toBuilder.setLocation("farm2").build))
    updated.getLocation should equal("farm2")

    one(Status.NOT_MODIFIED, service.put(updated))
    val list2 = service.get(ApplicationConfig.newBuilder().setUuid(ReefUUID.newBuilder.setUuid("*")).build)
    list2.size should equal(1)

    val config = list2.head
    config.getLocation should equal("farm2")

    one(Status.DELETED, service.delete(config))
    service.get(ApplicationConfig.newBuilder().setUuid(ReefUUID.newBuilder.setUuid("*")).build).size should equal(0)
  }
}