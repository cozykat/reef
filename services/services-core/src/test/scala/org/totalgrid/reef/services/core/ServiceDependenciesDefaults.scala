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
package org.totalgrid.reef.services.core

import org.totalgrid.reef.measurementstore.{ InMemoryMeasurementStore, MeasurementStore }
import org.totalgrid.reef.event.{ SilentEventSink, SystemEventSink }
import org.totalgrid.reef.client.Connection
import org.totalgrid.reef.client.sapi.client.BasicRequestHeaders
import org.totalgrid.reef.services.{ DependenciesRequestContext, RequestContextDependencies, ServiceDependencies }
import org.mockito.Mockito
import org.totalgrid.reef.persistence.squeryl.DbConnection
import org.totalgrid.reef.services.authz.{ NullAuthzService, AuthzService }
import org.totalgrid.reef.services.framework.{ RequestContext, SilentServiceSubscriptionHandler }
import org.totalgrid.reef.models.Entity
import org.totalgrid.reef.test.MockitoStubbedOnly
import org.totalgrid.reef.client.registration.{ ServiceRegistration, EventPublisher }

object ServiceDependenciesDefaults {

  def mockConnection: Connection = {
    val conn = Mockito.mock(classOf[Connection], new MockitoStubbedOnly)
    val servReg = Mockito.mock(classOf[ServiceRegistration])
    Mockito.doReturn(servReg).when(conn).getServiceRegistration
    conn
  }
}

class ServiceDependenciesDefaults(
  dbConnection: DbConnection,
  connection: Connection = ServiceDependenciesDefaults.mockConnection,
  pubs: EventPublisher = new SilentServiceSubscriptionHandler,
  cm: MeasurementStore = new InMemoryMeasurementStore,
  eventSink: SystemEventSink = new SilentEventSink,
  authToken: String = "",
  auth: AuthzService = new NullAuthzService) extends ServiceDependencies(dbConnection, connection, pubs, cm, eventSink, authToken, auth)
