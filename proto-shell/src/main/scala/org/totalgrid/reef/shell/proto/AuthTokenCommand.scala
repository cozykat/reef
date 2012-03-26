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
package org.totalgrid.reef.shell.proto

import org.totalgrid.reef.client.service.proto.Auth.{ Agent, AuthToken }
import org.totalgrid.reef.client.service.ClientOperations
import org.totalgrid.reef.shell.proto.presentation.AuthTokenView

import scala.collection.JavaConversions._
import org.apache.felix.gogo.commands.{ Argument, Option => GogoOption, Command }

@Command(scope = "auth", name = "list", description = "List active auth tokens")
class AuthTokenListCommand extends ReefCommandSupport {

  @GogoOption(name = "--own", description = "Search for our own tokens", required = false, multiValued = false)
  var ownTokens: Boolean = false

  @GogoOption(name = "--agent", description = "Search by agent name", required = false, multiValued = false)
  var agentName: String = "*"

  @GogoOption(name = "--version", description = "Search by clientVersion", required = false, multiValued = false)
  var clientVersion: String = null

  //  @GogoOption(name = "--location", description = "Search by login location", required = false, multiValued = false)
  //  var location: String = null

  @GogoOption(name = "--stat", description = "So per-agent summary", required = false, multiValued = false)
  var stats: Boolean = false

  @GogoOption(name = "--revoked", description = "Include revoked tokens", required = false, multiValued = false)
  var includeRevoked: Boolean = false

  override def doCommand(): Unit = {

    val request = AuthToken.newBuilder()

    Option(clientVersion).foreach(request.setClientVersion(_))
    if (!ownTokens) {
      Option(agentName).foreach(n => request.setAgent(Agent.newBuilder.setName(n)))
    }
    if (!includeRevoked) request.setRevoked(false)

    val clientOps = reefClient.getRpcInterface(classOf[ClientOperations])

    val results = clientOps.getMany(request.build).toList

    if (stats) {
      AuthTokenView.printAuthTokenStats(results)
    } else {
      AuthTokenView.printAuthTokens(results)
    }
  }
}

