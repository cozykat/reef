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
package org.totalgrid.reef.japi.request.impl

import org.totalgrid.reef.japi.request.AllScadaService
import org.totalgrid.reef.japi.client.{ SubscriptionCreationListener, SessionExecutionPool }
import org.totalgrid.reef.sapi.client.{ ClientSession }
import org.totalgrid.reef.sapi.request.{ AllScadaService => ScalaAllScadaService }
import org.totalgrid.reef.sapi.request.impl.{ AllScadaServiceScalaSingleSession, AllScadaServiceExecutionPool }

/**
 * "Super" interface that includes all of the helpers for the individual services. This could be broken down
 * into smaller functionality based sections or not created at all.
 */
class AllScadaServicePooledWrapper(scalaClient: ScalaAllScadaService)
    extends AllScadaService with AllScadaServiceJavaShim {

  def this(pool: SessionExecutionPool, authToken: String) = this(new AllScadaServiceExecutionPool(pool, authToken))

  def addSubscriptionCreationListener(listener: SubscriptionCreationListener) = scalaClient.addSubscriptionCreationListener(listener)

  def service = scalaClient
}

class AllScadaServiceSingleSession(scalaClient: AllScadaServiceScalaSingleSession)
    extends AllScadaService with AllScadaServiceJavaShim {

  def this(session: ClientSession) = this(new AllScadaServiceScalaSingleSession(session))

  def addSubscriptionCreationListener(listener: SubscriptionCreationListener) = scalaClient.addSubscriptionCreationListener(listener)

  def service = scalaClient

  def session = scalaClient.session
}

