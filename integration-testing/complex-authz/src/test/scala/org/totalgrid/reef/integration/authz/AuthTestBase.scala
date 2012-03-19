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
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.reef.client.exception.UnauthorizedException

class AuthTestBase extends ServiceClientSuite {

  override val modelFile = "../../assemblies/assembly-common/filtered-resources/samples/authorization/config.xml"

  /**
   * get a new client as a particular user (assumes password == username)
   */
  def as[A](userName: String)(f: AllScadaService => A): A = {
    val c = session.login(userName, userName).await
    val ret = f(c.getRpcInterface(classOf[AllScadaService]))
    c.logout().await
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
      case a: UnauthorizedException =>
      // were expecting the auth error, let others bubble
    }
  }
}
