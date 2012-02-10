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
package org.totalgrid.reef.entry

import org.totalgrid.reef.app.{ ApplicationSettings, ConnectedApplication }
import org.totalgrid.reef.client.service.proto.Application.ApplicationConfig
import org.totalgrid.reef.client.sapi.client.rest.{ Client, Connection }
import org.totalgrid.reef.protocol.api.Protocol
import org.totalgrid.reef.client.sapi.client.rest.RpcProvider
import org.totalgrid.reef.frontend._
import com.weiglewilczek.slf4s.Logging

class FepConnectedApplication(p: Protocol) extends ConnectedApplication with Logging {
  def getApplicationSettings = new ApplicationSettings("FEP-" + p.name, "FEP")

  private var fem = Option.empty[FrontEndManager]

  def onApplicationStartup(appConfig: ApplicationConfig, connection: Connection, appLevelClient: Client) = {
    fem = Some(makeFepNode(appLevelClient, appConfig, List(p)))
    fem.foreach { _.start() }
  }

  def onApplicationShutdown() = {
    fem.foreach { _.stop() }
  }

  def onConnectionError(msg: String) = {
    logger.info("FEP Error connecting: " + msg)
  }

  private def makeFepNode(client: Client, appConfig: ApplicationConfig, protocols: List[Protocol]) = {
    client.addRpcProvider(RpcProvider(c => new FrontEndProviderServicesImpl(client), List(classOf[FrontEndProviderServices])))

    val services = client.getRpcInterface(classOf[FrontEndProviderServices])

    def endpointClient = {
      client.spawn()
    }

    val frontEndConnections = new FrontEndConnections(protocols, endpointClient)
    val populator = new EndpointConnectionPopulatorAction(services)
    val connectionContext = new EndpointConnectionSubscriptionFilter(frontEndConnections, populator, client)

    // the manager does all the work of announcing the system, retrieving resources and starting/stopping
    // protocol masters in response to events
    new FrontEndManager(
      services,
      services,
      connectionContext,
      appConfig,
      protocols.map { _.name }.toList,
      5000)
  }
}