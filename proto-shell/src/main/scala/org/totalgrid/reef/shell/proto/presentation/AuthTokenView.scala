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
package org.totalgrid.reef.shell.proto.presentation

import org.totalgrid.reef.client.service.proto.Auth.AuthToken
import org.totalgrid.reef.util.Table

import scala.collection.JavaConversions._

object AuthTokenView {

  def printAuthTokens(tokens: List[AuthToken]) = {
    Table.printTable(authTokenHeader, tokens.map(tokenRow(_)))
  }

  def authTokenHeader = {
    "Id" :: "Agent" :: "Version" :: "PermissionSets" :: "Expires" :: Nil
  }

  private def displayTime(expirationTime: Long): String = {
    if (expirationTime <= 0) "Expired" else EventView.timeString(Some(expirationTime))
  }

  def tokenRow(a: AuthToken) = {
    a.getId.getValue ::
      a.getAgent.getName ::
      a.getClientVersion ::
      a.getPermissionSetsList.toList.map { _.getName }.mkString(",") ::
      displayTime(a.getExpirationTime) ::
      Nil
  }

  def printAuthTokenStats(tokens: List[AuthToken]) = {
    Table.printTable(authTokenStatsHeader, authTokenStatsRows(tokens))
  }

  def authTokenStatsHeader = {
    "Agent" :: "Count" :: "Active" :: "Expired" :: "Versions" :: Nil
  }

  def authTokenStatsRows(allTokens: List[AuthToken]): List[List[String]] = {
    allTokens.groupBy(_.getAgent.getName).map {
      case (agentName, tokens) =>
        val count = tokens.size
        val active = tokens.count(_.getExpirationTime > System.currentTimeMillis())
        val expired = count - active
        val versions = tokens.map { _.getClientVersion }.distinct

        val strings = List(agentName, count, active, expired, versions.mkString(",")).map { _.toString }

        agentName -> strings
    }.values.toList
  }
}
