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

import org.totalgrid.reef.app.{ CoreApplicationComponents, ApplicationEnroller }
import org.totalgrid.reef.api.RequestEnv
import org.totalgrid.reef.api.ServiceHandlerHeaders._
import org.totalgrid.reef.api.service.AsyncToSyncServiceAdapter

import org.totalgrid.reef.proto.FEP.FrontEndProcessor
import org.totalgrid.reef.messaging.AMQPProtoFactory
import org.totalgrid.reef.proto.ReefServicesList
import org.totalgrid.reef.messaging.serviceprovider.ServiceEventPublisherRegistry

object ServiceBootstrap {
  /**
   * since _we_are_ a service provider we can create whatever services we would normally
   * use to enroll ourselves as an application to get the CoreApplicationComponents without
   * repeating that setup logic somewhere else
   */
  def bootstrapComponents(amqp: AMQPProtoFactory): CoreApplicationComponents = {
    val pubs = new ServiceEventPublisherRegistry(amqp, ReefServicesList)
    val modelFac = new core.ModelFactories(pubs, new core.SilentSummaryPoints)
    val applicationConfigService = new core.ApplicationConfigService(modelFac.appConfig)
    val authService = new core.AuthTokenService(modelFac.authTokens)

    val login = ApplicationEnroller.buildLogin()
    val authToken = authService.put(login).result.head

    val config = ApplicationEnroller.buildConfig(List("Services"))
    val appConfig = applicationConfigService.put(config).result.head

    // the measurement batch service acts as a type of manual FEP
    val msg = FrontEndProcessor.newBuilder
    msg.setAppConfig(appConfig)
    msg.addProtocols("null")
    val fepService = new core.FrontEndProcessorService(modelFac.fep)
    fepService.put(msg.build)

    val env = new RequestEnv
    env.addAuthToken(authToken.getToken)
    new CoreApplicationComponents(amqp, appConfig, env)
  }

  /**
   * sets up the default users and low level configurations for the system
   */
  def seed() {
    core.EventConfigService.seed()
    core.AuthTokenService.seed()
  }

  /**
   * drops and re-creates all of the tables in the database.
   */
  def resetDb() {
    import org.squeryl.PrimitiveTypeMode._
    import org.totalgrid.reef.models._

    workaroundToClearAllTablesOnPostgresql()

    transaction {
      ApplicationSchema.reset
    }
  }

  /**
   * HACK to remove all tables from postgres database to workaround table name changes between version
   * 0.9.4-RC3 and 0.9.4-RC6 of squeryl. This will be mooted when we move to a proper database schema migration system.
   * Also mooted when we EOL 0.2.3
   * TODO: remove workaroundToClearAllTablesOnPostgresql
   */
  private def workaroundToClearAllTablesOnPostgresql() {
    import org.squeryl.Session
    import org.squeryl.PrimitiveTypeMode._
    transaction {
      val s = Session.currentSession.connection.createStatement
      val rs = s.executeQuery("SELECT 'DROP TABLE \"' || c.relname || '\" CASCADE;' FROM pg_catalog.pg_class AS c LEFT JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace WHERE relkind ='r' AND n.nspname NOT IN ('pg_catalog', 'pg_toast') AND pg_catalog.pg_table_is_visible(c.oid);")
      val dropStatement = Session.currentSession.connection.createStatement
      while (rs.next()) {
        dropStatement.addBatch(rs.getString(1))
      }
      dropStatement.executeBatch()
      s.close
      rs.close
      dropStatement.close
    }
  }
}