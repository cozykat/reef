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
package org.totalgrid.reef.httpbridge.servlets

import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import org.totalgrid.reef.httpbridge.ManagedConnection
import org.totalgrid.reef.httpbridge.servlets.helpers.argumentsources.ParameterArgumentSource
import org.totalgrid.reef.httpbridge.servlets.helpers._
import org.totalgrid.reef.httpbridge.JsonBridgeConstants._

/**
 * Proxies calls from the client, and prepares the apporiate ArgumentSource based on the method and
 * headers. This will allow us to provide these Apis for consumption using a number of techniques.
 */
class ApiServlet(connection: ManagedConnection, apiCallProvider: ApiCallProvider) extends ClientUsingServletBase(connection) {

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = handleErrors(resp) {
    val argumentSource = new ParameterArgumentSource(req)

    handleRequest(req, resp, argumentSource)
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = handleErrors(resp) {
    val argumentSource = new ParameterArgumentSource(req)

    handleRequest(req, resp, argumentSource)
  }

  private def handleRequest(req: HttpServletRequest, resp: HttpServletResponse, argumentSource: ArgumentSource) {

    val authToken = getAuthToken(req)
    val headers = getReefRequestHeaders(req)

    val function = Option(req.getPathInfo).getOrElse("").stripPrefix("/")
    val apiCall = apiCallProvider.prepareApiCall(function, argumentSource)

    val client = connection.getAuthenticatedClient(authToken)
    client.setHeaders(headers.setAuthToken(authToken))

    apiCall match {
      case SingleResultApiCall(func) =>
        resp.setHeader(RETURN_STYLE, "SINGLE")
        printSingleOutput(req, resp, func(client).await)
      case OptionalResultApiCall(func) =>
        resp.setHeader(RETURN_STYLE, "SINGLE")
        printOutput(req, resp, func(client).await.toList)
      case MultiResultApiCall(func) =>
        resp.setHeader(RETURN_STYLE, "MULTI")
        printOutput(req, resp, func(client).await)
      case SubscriptionResultApiCall(func) =>
        resp.setHeader(RETURN_STYLE, "MULTI")
        val subResult = func(client).await
        //val subToken = subscriptionHandler.addSubscription(subResult.getSubscription)
        // we are "tunneling" the subscription token out through one of the "simple response headers"
        // until the CORS support for expose Headers is up to snuff.
        resp.setHeader("Pragma", "fakeToken")
        printOutput(req, resp, subResult.getResult)
    }

    resp.setStatus(200)
  }
}