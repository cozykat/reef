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

import org.totalgrid.reef.services.framework.RequestContext
import org.totalgrid.reef.authz._
import org.totalgrid.reef.client.exception.UnauthorizedException
import com.weiglewilczek.slf4s.Logging

import java.util.UUID
import org.totalgrid.reef.models.{ Agent, ApplicationSchema }
import org.totalgrid.reef.client.service.proto.Auth.PermissionSet

trait AuthzService {

  def authorize(context: RequestContext, componentId: String, action: String, uuids: => List[UUID]): Unit

  // load up the permissions sets
  def prepare(context: RequestContext)
}

class NullAuthzService extends AuthzService {
  def authorize(context: RequestContext, componentId: String, action: String, uuids: => List[UUID]) {}
  def prepare(context: RequestContext) {}
}

object SqlAuthzService {
  import org.squeryl.PrimitiveTypeMode._

  case class AuthLookup(agent: Agent, permissionSets: List[PermissionSet])

  def lookupTokens(tokenList: List[String]): Option[AuthLookup] = {
    val now = System.currentTimeMillis

    import ApplicationSchema.{ authTokens, permissionSets, agents, tokenSetJoins }
    import org.totalgrid.reef.models.{ AuthToken => SqlToken, PermissionSet => SqlSet }

    val results: List[(SqlToken, Option[SqlSet])] =
      from(authTokens, permissionSets.leftOuter)((tok, set) =>
      where(tok.token in tokenList and tok.expirationTime.~ > now and
        (set.map(s => s.id) in from(tokenSetJoins)(j => where(j.authTokenId === tok.id) select (j.permissionSetId))))
        select (tok, set)).toList

    if (!results.isEmpty) {
      val agent = results.head._1.agent.value
      val permissions = results.flatMap(_._2.map(p => p.proto))
      Some(AuthLookup(agent, permissions))
    } else {
      None
    }
  }
}

class SqlAuthzService(filteringService: AuthzFilteringService) extends AuthzService with Logging {
  import SqlAuthzService._

  def this() = this(AuthzFilter)

  def authorize(context: RequestContext, componentId: String, action: String, uuids: => List[UUID]) {

    val permissions = context.get[List[Permission]]("permissions")
      .getOrElse(throw new UnauthorizedException(context.get[String]("auth_error").get))

    // just pass in a single boolean value, if it gets filtered we know we are not auhorized
    val filtered = filteringService.filter(permissions, componentId, action, List(true), List(uuids))

    filtered.find(!_.isAllowed) match {
      case Some(filterResult) => throw new UnauthorizedException(filterResult.toString)
      case None =>
    }
  }

  def prepare(context: RequestContext) = {
    // load the permissions by forcing an auth attempt
    loadPermissions(context)
  }

  def loadPermissions(context: RequestContext) {

    val authTokens = context.getHeaders.authTokens

    if (authTokens.isEmpty) context.set("auth_error", "No auth tokens in envelope header")
    else {

      lookupTokens(authTokens) match {
        case None => context.set("auth_error", "All tokens unknown or expired")
        case Some(AuthLookup(agent, permSets)) =>
          context.set("agent", agent)
          context.set("permissions", permSets.flatMap { Permission.fromProto(_, agent.entityName) })
      }
    }
  }

}