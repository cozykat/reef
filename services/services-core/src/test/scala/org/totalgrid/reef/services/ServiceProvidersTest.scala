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
package org.totalgrid.reef.services

import org.totalgrid.reef.measurementstore.InMemoryMeasurementStore

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.totalgrid.reef.models.DatabaseUsingTestBase
import org.totalgrid.reef.services.framework.{ ServiceContainer, ServerSideProcess }
import org.totalgrid.reef.client.settings.{ UserSettings, NodeSettings }
import org.totalgrid.reef.util.Lifecycle
import org.totalgrid.reef.client.{ RequestHeaders, Connection }
import org.totalgrid.reef.client.sapi.service.{ AsyncService }
import org.totalgrid.reef.client.registration.ServiceResponseCallback
import org.totalgrid.reef.client.proto.Envelope
import org.totalgrid.reef.client.types.TypeDescriptor
import org.totalgrid.reef.services.authz.NullAuthzService
import net.agileautomata.executor4s.testing.InstantExecutor
import org.totalgrid.reef.client.settings.util.PropertyReader
import org.totalgrid.reef.services.settings.ServiceOptions

/**
 * A concrete example service that always responds immediately with Success and the correct Id
 */
class NoOpService extends AsyncService[Any] {

  import Envelope._

  /// noOpService that returns OK
  def respond(request: ServiceRequest, env: RequestHeaders, callback: ServiceResponseCallback) =
    callback.onResponse(ServiceResponse.newBuilder.setStatus(Status.OK).setId(request.getId).build)

  override val descriptor = new TypeDescriptor[Any] {
    def serialize(typ: Any): Array[Byte] = throw new Exception("unimplemented")
    def deserialize(data: Array[Byte]): Any = throw new Exception("unimplemented")
    def getKlass: Class[Any] = throw new Exception("unimplemented")
    def id = "Any"
  }
}

@RunWith(classOf[JUnitRunner])
class ServiceProvidersTest extends DatabaseUsingTestBase {

  class ExchangeCheckingServiceContainer(amqp: Connection) extends ServiceContainer {
    def addCoordinator(coord: ServerSideProcess) {}

    def addLifecycleObject(obj: Lifecycle) {}

    def attachService(endpoint: AsyncService[_]): AsyncService[_] = {
      val klass = endpoint.descriptor.getKlass
      //call just so an exception will be thrown if it doesn't exist
      amqp.getServiceRegistration.declareEventExchange(klass)
      new NoOpService
    }
  }

  test("All Service Providers are in services list") {
    ConnectionFixture.mock() { amqp =>
      ServiceBootstrap.seed(dbConnection, "system")

      val userSettings = new UserSettings("system", "system")
      val nodeSettings = new NodeSettings("node1", "network", "location")
      val properties = PropertyReader.readFromFile("../../org.totalgrid.reef.test.cfg")
      val serviceOptions = new ServiceOptions(properties)

      val components = ServiceBootstrap.bootstrapComponents(dbConnection, amqp, userSettings, nodeSettings)
      val measStore = new InMemoryMeasurementStore
      val serviceContainer = new ExchangeCheckingServiceContainer(amqp)

      val provider = new ServiceProviders(dbConnection, amqp, measStore, serviceOptions,
        new NullAuthzService, "", new InstantExecutor())
      serviceContainer.addCoordinator(provider.coordinators)
      serviceContainer.attachServices(provider.services)
    }
  }

}