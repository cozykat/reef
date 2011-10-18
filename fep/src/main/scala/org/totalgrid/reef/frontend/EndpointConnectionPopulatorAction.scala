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
package org.totalgrid.reef.frontend

import scala.collection.JavaConversions._

import org.totalgrid.reef.api.proto.FEP.{ CommEndpointConfig, CommEndpointConnection }

class EndpointConnectionPopulatorAction(client: FrontEndProviderServices) {
  /**
   * takes a partially populated endpoint connection and makes requests to the services to fill in the
   * partial fields (For example, we don't send the full configFile text for each endpoint)
   */
  def populate(conn: CommEndpointConnection) = {

    val cp = CommEndpointConnection.newBuilder(conn)

    val endpointUuid = conn.getEndpoint.getUuid

    val ep = client.getEndpoint(endpointUuid).await()
    val endpoint = CommEndpointConfig.newBuilder(ep)

    ep.getConfigFilesList.toList.foreach(cf => endpoint.addConfigFiles(client.getConfigFileByUid(cf.getUuid).await()))

    if (ep.hasChannel) endpoint.setChannel(client.getCommunicationChannel(ep.getChannel.getUuid).await())
    cp.setEndpoint(endpoint).build
  }
}
