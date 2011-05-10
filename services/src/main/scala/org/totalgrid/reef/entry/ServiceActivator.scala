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
package org.totalgrid.reef.entry

import org.osgi.framework._

import org.totalgrid.reef.api.service.IServiceAsync

import org.totalgrid.reef.services.{ Services, ServiceOptions, AuthService }
import org.totalgrid.reef.messaging.AMQPProperties
import org.totalgrid.reef.persistence.squeryl.SqlProperties
import org.totalgrid.reef.reactor.Lifecycle
import org.totalgrid.reef.osgi.OsgiConfigReader

import com.weiglewilczek.scalamodules._
import org.totalgrid.reef.proto.ReefServicesList

class ServiceActivator extends BundleActivator {

  var services: Option[Lifecycle] = None

  def start(context: BundleContext) {

    org.totalgrid.reef.reactor.Reactable.setupThreadPools

    val sql = SqlProperties.get(new OsgiConfigReader(context, "org.totalgrid.reef"))
    val amqp = AMQPProperties.get(new OsgiConfigReader(context, "org.totalgrid.reef"))
    val options = ServiceOptions.get(new OsgiConfigReader(context, "org.totalgrid.reef"))

    // create the authorization interface that other services will use. In the future, this could be retrieved via OSGi.
    val auth = new AuthService {}

    val srvContext = Services.makeContext(amqp, sql, sql, options, auth)

    // publish all of the services using the exchange as the filter
    srvContext.services.foreach { x =>
      val exchange = ReefServicesList.getServiceInfo(x.descriptor.getKlass).exchange
      context createService (x, "exchange" -> exchange, interface[IServiceAsync[_]])
    }

    services = Some(srvContext)
    services.get.start
  }

  def stop(context: BundleContext) = services.foreach { _.stop }

}

