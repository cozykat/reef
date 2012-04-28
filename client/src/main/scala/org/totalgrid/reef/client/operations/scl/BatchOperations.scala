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
package org.totalgrid.reef.client.operations.scl

import org.totalgrid.reef.client.Promise
import org.totalgrid.reef.client.proto.Envelope.BatchServiceRequest

trait BatchOperations {

  def startBatchRequests()
  def stopBatchRequests()
  def flushBatchRequests(): Promise[BatchServiceRequest]
  def batchedFlushBatchRequests(batchSize: Int): Promise[Boolean]
}

object BatchOperations {

  // TODO: replace BatchOperations.batchOperations with batchedFlushBatchRequests()
  def batchOperations[A <: BatchOperations](client: A, uploadActions: scala.List[A => Promise[_]], batchSize: Int) {

    try {
      if (batchSize > 0) client.startBatchRequests()
      var i = 0
      uploadActions.foreach { action =>
        i = i + 1
        if (batchSize > 0) {
          action(client)
          if (i % batchSize == 0) client.flushBatchRequests().await
        } else {
          action(client).await
        }
      }
      if (batchSize > 0) {
        client.flushBatchRequests().await
      }
    } finally {
      if (batchSize > 0) client.stopBatchRequests()
    }
  }
}

