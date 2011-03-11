/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.api.request

import org.totalgrid.reef.proto.Auth.{ AuthToken, Agent }

object AuthTokenRequestBuilders {
  def requestAuthToken(user: String, password: String) = {
    AuthToken.newBuilder.setAgent(Agent.newBuilder.setName(user).setPassword(password)).build
  }

  def deleteAuthToken(token: String) = {
    AuthToken.newBuilder.setToken(token).build
  }
}

trait AuthTokenServiceImpl extends ReefServiceBaseClass with AuthTokenService {

  def createNewAuthorizationToken(user: String, password: String): String = {
    val resp = ops.putOneOrThrow(AuthTokenRequestBuilders.requestAuthToken(user, password))
    resp.getToken
  }

  def deleteAuthorizationToken(token: String): Boolean = {
    ops.deleteOneOrThrow(AuthTokenRequestBuilders.deleteAuthToken(token))
    false
  }
}
