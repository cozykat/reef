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
package org.totalgrid.reef.clientapi.sapi.types

import org.totalgrid.reef.clientapi.types.{ ServiceInfo, TypeDescriptor }
import org.totalgrid.reef.clientapi.proto.SimpleAuth
import org.totalgrid.reef.clientapi.proto.Envelope.BatchServiceRequest

object BuiltInDescriptors {
  def authRequest() = new TypeDescriptor[SimpleAuth.AuthRequest] {
    def serialize(typ: SimpleAuth.AuthRequest): Array[Byte] = typ.toByteArray
    def deserialize(bytes: Array[Byte]) = SimpleAuth.AuthRequest.parseFrom(bytes)
    def getKlass = classOf[SimpleAuth.AuthRequest]
    def id = "auth_request"
  }
  def authRequestServiceInfo = new ServiceInfo(authRequest, authRequest)

  def batchServiceRequest() = new TypeDescriptor[BatchServiceRequest] {
    def serialize(typ: BatchServiceRequest): Array[Byte] = typ.toByteArray
    def deserialize(bytes: Array[Byte]) = BatchServiceRequest.parseFrom(bytes)
    def getKlass = classOf[BatchServiceRequest]
    def id = "batch_service"
  }
  def batchServiceRequestServiceInfo = new ServiceInfo(batchServiceRequest, batchServiceRequest)
}