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
package org.totalgrid.reef.services.authz

import org.totalgrid.reef.client.proto.Envelope

import org.totalgrid.reef.services.framework.RequestContext
import org.totalgrid.reef.models.{ AuthPermission, ApplicationSchema }

import org.squeryl.PrimitiveTypeMode._

trait SimpleSqlAuthzService extends AuthService {

  private def deny(msg: String, status: Envelope.Status = Envelope.Status.UNAUTHORIZED) = Some(AuthDenied(msg, status))

  private def loadPermissions(context: RequestContext): Option[AuthDenied] = {
    val authTokens = context.getHeaders.authTokens

    if (authTokens.size == 0) deny("No auth tokens in envelope header", Envelope.Status.BAD_REQUEST)
    else {
      // lookup the tokens that are not expired
      val now = System.currentTimeMillis

      val tokens = ApplicationSchema.authTokens.where(t => t.token in authTokens and t.expirationTime.~ > now).toList
      if (tokens.size == 0) deny("All tokens unknown or expired")
      else {

        val permissions = tokens.map(token => token.permissionSets.value.toList.map(ps => ps.permissions.value).flatten).flatten.distinct

        val userName = tokens.head.agent.value.entityName

        // loaded valid permissions, store them on the context
        context.modifyHeaders(_.setUserName(userName))
        context.setPermissions(permissions)

        None
      }
    }
  }

  private def isAuthorized(componentId: String, actionId: String, permissions: List[AuthPermission]): Option[AuthDenied] = {
    // select only the permissions that either say this resource + verb exactly or are wildcarded
    val relevant = permissions.filter(p => (p.resource == "*" || p.resource == componentId) && (p.verb == "*" || p.verb == actionId))

    if (relevant.size == 0) {
      deny("No authorization found for access to resource: " + componentId + ":" + actionId)
    } else {

      val denied = relevant.find(p => p.allow == false)
      if (denied.isDefined) {
        deny("Access to resource: " + componentId + ":" + actionId + " explictly denied by permission: " + denied.get)
      } else {
        None
      }
    }
  }

  def isAuthorized(componentId: String, actionId: String, context: RequestContext): Option[AuthDenied] = {
    if (context.getPermissions.isEmpty) {
      val authFailed = loadPermissions(context)
      if (authFailed.isDefined) authFailed
      else isAuthorized(componentId, actionId, context.getPermissions.get)
    } else {
      isAuthorized(componentId, actionId, context.getPermissions.get)
    }
  }
}