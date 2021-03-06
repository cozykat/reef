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
package org.totalgrid.reef.integration.authz

import org.totalgrid.reef.client.sapi.rpc.impl.util.ServiceClientSuite
import org.totalgrid.reef.client.sapi.sync.{ ClientOperations, AllScadaService }
import org.totalgrid.reef.client.settings.util.PropertyReader
import org.totalgrid.reef.client.settings.UserSettings
import org.totalgrid.reef.client.exception.{ ExpectationException, BadRequestException, UnauthorizedException }

class AuthTestBase extends ServiceClientSuite {

  override val modelFile = "../../assemblies/assembly-common/filtered-resources/samples/authorization/config.xml"

  var userConfig = Option.empty[UserSettings]

  override def beforeAll() {
    super.beforeAll()

    val props = PropertyReader.readFromFile("../../org.totalgrid.reef.test.cfg")
    userConfig = Some(new UserSettings(props))

    // update all of the agents to have the same system password
    client.getAgents().foreach { a =>
      client.setAgentPassword(a, userConfig.get.getUserPassword)
    }
  }

  /**
   * get a new client as a particular user (assumes password == username)
   */
  def as[A](userName: String, logout: Boolean = true)(f: AllScadaService => A): A = {
    val c = connection.login(new UserSettings(userName, userConfig.get.getUserPassword))
    c.setHeaders(c.getHeaders.setResultLimit(5000))
    val ret = f(c.getService(classOf[AllScadaService]))
    if (logout) c.logout()
    ret
  }

  /**
   * get a new client as a particular user (assumes password == username)
   */
  def asOps[A](userName: String, logout: Boolean = true)(f: ClientOperations => A): A = {
    val c = connection.login(new UserSettings(userName, userConfig.get.getUserPassword))
    c.setHeaders(c.getHeaders.setResultLimit(5000))
    val ret = f(c.getService(classOf[ClientOperations]))
    if (logout) c.logout()
    ret
  }

  /**
   * check that a call fails with an unauthorized error, just using intercept leads to useless error messages
   */
  def unAuthed(failureMessage: String)(f: => Unit) {
    try {
      f
      fail(failureMessage)
    } catch {
      // were expecting the auth error, let others bubble
      case a: UnauthorizedException =>
      // if the record we cared about has been filtered off then we may get a different error than an explict
      // unauthorized exception
      case b: BadRequestException =>
      case c: ExpectationException =>

    }
  }

  def executeCommands(service: AllScadaService, cmds: List[String]) {
    cmds.foreach { cmdName => executeCommand(service, cmdName) }
  }

  def cantExecuteCommands(service: AllScadaService, cmds: List[String]) {
    cmds.foreach { cmdName =>
      unAuthed("Expected executing command: " + cmdName + " to be unauthorized") {
        executeCommand(service, cmdName)
      }
    }
  }

  private def executeCommand(service: AllScadaService, cmdName: String) {
    val cmd = service.getCommandByName(cmdName)
    val lock = service.createCommandExecutionLock(cmd)
    try {
      service.executeCommandAsControl(cmd)
    } finally {
      service.deleteCommandLock(lock)
    }
  }
}
