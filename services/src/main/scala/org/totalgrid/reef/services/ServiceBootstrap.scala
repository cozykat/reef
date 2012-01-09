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

import org.totalgrid.reef.client.sapi.client.BasicRequestHeaders

import org.totalgrid.reef.client.service.proto.FEP.FrontEndProcessor
import org.totalgrid.reef.client.service.proto.Auth.{ AuthToken, Agent }

import org.totalgrid.reef.services.framework.RequestContextSourceWithHeaders
import org.totalgrid.reef.client.settings.{ UserSettings, NodeSettings }
import org.totalgrid.reef.client.sapi.client.rest.Connection
import org.totalgrid.reef.client.sapi.rpc.impl.builders.ApplicationConfigBuilders
import org.totalgrid.reef.services.core.{ ModelFactories, ApplicationConfigService, AuthTokenService, FrontEndProcessorService }
import org.totalgrid.reef.client.service.list.ReefServicesList
import org.totalgrid.reef.event.SilentEventSink
import org.totalgrid.reef.measurementstore.InMemoryMeasurementStore

object ServiceBootstrap {

  def buildLogin(userSettings: UserSettings): AuthToken = {
    val agent = Agent.newBuilder
    agent.setName(userSettings.getUserName).setPassword(userSettings.getUserPassword)
    val auth = AuthToken.newBuilder
    auth.setAgent(agent)
    auth.build
  }

  /**
   * when we are starting up the system we need to define all of the event exechanges, we do that
   * during bootstrap so we correctly publish the "someone logged on" events
   */
  def defineEventExchanges(connection: Connection) {
    import scala.collection.JavaConversions._
    ReefServicesList.getServicesList.toList.foreach { serviceInfo =>
      connection.declareEventExchange(serviceInfo.getDescriptor.getKlass)
    }
  }

  /**
   * since _we_are_ a service provider we can create whatever services we would normally
   * use to enroll ourselves as an application to get the CoreApplicationComponents without
   * repeating that setup logic somewhere else
   */
  def bootstrapComponents(connection: Connection, systemUser: UserSettings, appSettings: NodeSettings) = {
    val dependencies = new RequestContextDependencies(connection, connection, "", new SilentEventSink)

    // define the events exchanges before "logging in" which will generate some events
    defineEventExchanges(connection)
    val headers = BasicRequestHeaders.empty.setUserName(systemUser.getUserName)

    val contextSource = new RequestContextSourceWithHeaders(new DependenciesSource(dependencies), headers)
    val modelFac = new ModelFactories(new InMemoryMeasurementStore, contextSource)
    val applicationConfigService = new ApplicationConfigService(modelFac.appConfig)
    val authService = new AuthTokenService(modelFac.authTokens)

    val login = buildLogin(systemUser)
    val authToken = authService.put(contextSource, login).expectOne

    val config = ApplicationConfigBuilders.makeProto(appSettings, appSettings.getDefaultNodeName + "-Services", List("Services"))
    val appConfig = applicationConfigService.put(contextSource, config).expectOne

    // the measurement batch service acts as a type of manual FEP
    val msg = FrontEndProcessor.newBuilder
    msg.setAppConfig(appConfig)
    msg.addProtocols("null")
    val fepService = new FrontEndProcessorService(modelFac.fep)
    fepService.put(contextSource, msg.build)

    (appConfig, authToken.getToken)
  }

  /**
   * sets up the default users and low level configurations for the system
   */
  def seed(systemPassword: String) {
    val context = new SilentRequestContext

    core.EventConfigService.seed()
    core.EntityService.seed()
    core.AuthTokenService.seed(context, systemPassword)
  }

  /**
   * drops and re-creates all of the tables in the database.
   */
  def resetDb() {
    import org.squeryl.PrimitiveTypeMode._
    import org.totalgrid.reef.models._

    transaction {
      ApplicationSchema.reset
    }
  }
}