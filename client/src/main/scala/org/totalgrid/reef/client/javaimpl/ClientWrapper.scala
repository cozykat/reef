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

import org.totalgrid.reef.client._
import exception.{ BadRequestException, ServiceIOException }
import operations.impl._
import operations.{ RequestListenerManager, ServiceOperations }
import org.totalgrid.reef.client.ServiceProviderInfo
import net.agileautomata.executor4s.Executor

import sapi.client.BasicRequestHeaders
import sapi.client.rest.impl.DefaultClient

class ClientWrapper(client: DefaultClient) extends Client {

  def getHeaders = client.getHeaders

  // TODO make client Headers mutable 0.5.x
  def setHeaders(headers: RequestHeaders) {
    client.setHeaders(headers)
  }

  def addSubscriptionCreationListener(listener: SubscriptionCreationListener) {
    client.addSubscriptionCreationListener(listener)
  }

  def removeSubscriptionCreationListener(listener: SubscriptionCreationListener) {
    client.removeSubscriptionCreationListener(listener)
  }

  def getService[A](klass: Class[A]) = client.getRpcInterface(klass)

  def addServiceProvider(info: ServiceProviderInfo) {
    client.addRpcProvider(info)
  }

  def logout() {
    client.logout().await
  }

  def getInternal: ClientInternal = {
    new ClientInternal {
      def getExecutor: Executor = client
    }
  }

  def spawn(): Client = new ClientWrapper(client.spawn())

  class BatchModeManager extends Batching {
    protected var currentOpsMode: OptionallyBatchedRestOperations = regular

    protected def regular = new DefaultRestOperations(client)

    def getOps: OptionallyBatchedRestOperations = currentOpsMode

    def start() {
      currentOpsMode = new DefaultBatchRestOperations(regular, client)
    }

    def exit() {
      currentOpsMode = regular
    }

    def flush() = {
      currentOpsMode.batched match {
        case None => throw new BadRequestException("No batch requests configured")
        case Some(batched) => batched.flush()
      }
    }

    def flush(chunkSize: Int) = {
      currentOpsMode.batched match {
        case None => throw new BadRequestException("No batch requests configured")
        case Some(batched) => batched.batchedFlush(chunkSize)
      }
    }
  }

  protected val batchMgr = new BatchModeManager

  def getServiceOperations: ServiceOperations = {
    val restOperations = batchMgr.getOps
    def createSingleOpsBatch() = {
      new DefaultBatchRestOperations(restOperations, client)
    }
    val bindOperations = new DefaultBindOperations(client)

    new DefaultServiceOperations(restOperations, bindOperations, createSingleOpsBatch _, client)
  }

  def getBatching: Batching = batchMgr

  def getRequestListenerManager: RequestListenerManager = client.listenerManager

  def getServiceRegistry: ServiceRegistry = new ServiceRegistryWrapper(client)

}