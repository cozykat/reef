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
package org.totalgrid.reef.frontend

import org.totalgrid.reef.app.{ ApplicationSettings, ConnectedApplication }
import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.{ Client, Connection }
import com.weiglewilczek.slf4s.Logging
import org.totalgrid.reef.client.settings.UserSettings
import org.totalgrid.reef.protocol.api.{ ProtocolManager, Protocol }

class FepConnectedApplication(protocolName: String, p: Option[Protocol], mgr: Option[ProtocolManager], protocolSpecificUser: UserSettings)
    extends ConnectedApplication with Logging {

  def this(p: Protocol, protocolSpecificUser: UserSettings) = this(p.name, Some(p), None, protocolSpecificUser)

  def this(protocolName: String, mgr: ProtocolManager, protocolSpecificUser: UserSettings) = this(protocolName, None, Some(mgr), protocolSpecificUser)

  def getApplicationSettings = new ApplicationSettings("FEP-" + protocolName, "FEP")

  private var fem = Option.empty[FrontEndManager]
  private var client = Option.empty[Client]

  def onApplicationStartup(appConfig: ApplicationConfig, connection: Connection, appLevelClient: Client) = {

    client = Some(connection.login(protocolSpecificUser))
    //client = Some(appLevelClient.login(protocolSpecificUser))

    fem = Some(makeFepNode(client.get, appConfig))
    fem.foreach {
      _.start()
    }
  }

  def onApplicationShutdown() = {
    fem.foreach {
      _.stop()
    }
    client.foreach {
      _.logout()
    }
  }

  def onConnectionError(msg: String) = {
    logger.info("FEP Error connecting: " + msg)
  }

  private def makeFepNode(client: Client, appConfig: ApplicationConfig) = {
    client.addServiceProvider(FrontEndProviderServices.serviceInfo)

    val services = client.getService(classOf[FrontEndProviderServices])

    def endpointClient = {
      client.spawn()
    }

    val potentialManagers = List(
      mgr.map(m => protocolName -> m),
      p.map(p => p.name -> new ProtocolTraitToManagerShim(p)))

    val managers = potentialManagers.flatten.toMap
    if (managers.isEmpty) throw new IllegalArgumentException("Must include atleast one protocol implementation")

    val frontEndConnections = new FrontEndConnections(managers, endpointClient)
    val populator = new EndpointConnectionPopulatorAction(services)
    val connectionContext = new EndpointConnectionSubscriptionFilter(frontEndConnections, populator, client.getInternal.getExecutor)

    // the manager does all the work of announcing the system, retrieving resources and starting/stopping
    // protocol masters in response to events
    new FrontEndManager(
      services,
      services,
      connectionContext,
      appConfig,
      managers.keys.toList,
      5000)
  }
}