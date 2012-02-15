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
package org.totalgrid.reef.client.registration;

import org.totalgrid.reef.client.proto.Envelope;

import java.util.List;
import java.util.Map;

/**
 * Interface for service implementations.
 */
public interface Service
{
    /**
     * Handles a service request. Implementations use the verb, request payload, and headers to provide a
     * service response, which is sent asynchronously using a callback.
     *
     * The response id should be the same as the request id.
     *
     * @param request ServiceRequest representing this request
     * @param headers Request headers extracted from request
     * @param callback Callback object used to asynchronously respond to request
     */
    void respond( Envelope.ServiceRequest request, Map<String, List<String>> headers, ServiceResponseCallback callback );
}