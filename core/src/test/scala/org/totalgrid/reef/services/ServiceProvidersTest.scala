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

import org.scalatest.{ FunSuite, BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.totalgrid.reef.reactor.Lifecycle
import org.totalgrid.reef.messaging.ServicesList

import org.totalgrid.reef.messaging.mock.AMQPFixture
import org.totalgrid.reef.messaging.ServiceRequestHandler
import org.totalgrid.reef.protoapi.RequestEnv

import org.totalgrid.reef.measurementstore.InMemoryMeasurementStore
import org.totalgrid.reef.proto.Envelope

import org.totalgrid.reef.persistence.squeryl.{ DbConnector, DbInfo }

@RunWith(classOf[JUnitRunner])
class ServiceProvidersTest extends FunSuite with ShouldMatchers with BeforeAndAfterAll {
  override def beforeAll() {
    DbConnector.connect(DbInfo.loadInfo("test"))
  }

  class ExchangeCheckingServiceContainer extends ServiceContainer {
    def addCoordinator(coord: ProtoServiceCoordinator) {}

    def addLifecycleObject(obj: Lifecycle) {}

    def attachService(endpoint: ProtoServiceEndpoint): ServiceRequestHandler = {
      ServicesList.getServiceInfo(endpoint.servedProto)
      new ServiceRequestHandler {
        def respond(req: Envelope.ServiceRequest, env: RequestEnv): Envelope.ServiceResponse =
          Envelope.ServiceResponse.getDefaultInstance
      }
    }
  }

  test("All Service Providers are in services list") {
    AMQPFixture.mock(true) { amqp =>
      ServiceBootstrap.resetDb
      ServiceBootstrap.seed
      val components = ServiceBootstrap.bootstrapComponents(amqp)
      val measStore = new InMemoryMeasurementStore
      val serviceContainer = new ExchangeCheckingServiceContainer

      val provider = new ServiceProviders(components, measStore)
      serviceContainer.addCoordinator(provider.coordinators)
      serviceContainer.attachServices(provider.services)
    }
  }

}