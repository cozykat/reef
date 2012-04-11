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
package org.totalgrid.reef.client.javaimpl

import org.totalgrid.reef.client.settings.UserSettings
import org.totalgrid.reef.client.exception.ReefServiceException

import org.totalgrid.reef.client.sapi.client.rest.{ Connection => SConnection }
import org.totalgrid.reef.client._
import org.totalgrid.reef.client.ServicesList
import net.agileautomata.executor4s.Executor
import registration.ServiceRegistration

class ConnectionWrapper(conn: SConnection, exe: Executor) extends Connection {
  def addConnectionListener(listener: ConnectionCloseListener) = conn.addConnectionListener(listener)

  def removeConnectionListener(listener: ConnectionCloseListener) = conn.removeConnectionListener(listener)

  @throws(classOf[ReefServiceException])
  def login(userSettings: UserSettings): Client =
    new ClientWrapper(conn.login(userSettings).await)

  def createClient(authToken: String): Client = new ClientWrapper(conn.login(authToken))

  def logout(authToken: String) = conn.logout(authToken).await

  def disconnect(): Unit = conn.disconnect()

  def addServicesList(servicesList: ServicesList) = conn.addServicesList(servicesList)

  def getServiceRegistration: ServiceRegistration = new ServiceRegistrationWrapper(conn, exe)
}