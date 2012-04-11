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

import org.totalgrid.reef.client.types.ServiceTypeInformation;
import org.totalgrid.reef.client.types.TypeDescriptor;

/**
 * Helper class for building ServiceTypeInformation instances.
 *
 * @param <T> Main service request/response type
 * @param <U> Service subscription event type
 */
public class BasicServiceTypeInformation<T, U> implements ServiceTypeInformation<T, U>
{
    private final TypeDescriptor<T> requestType;
    private final TypeDescriptor<U> eventType;
    private final String eventExchange;

    /**
     * @param requestType Type descriptor for request/response type
     * @param eventType Type descriptor for service subscription event type
     */
    public BasicServiceTypeInformation( TypeDescriptor<T> requestType, TypeDescriptor<U> eventType )
    {
        this.requestType = requestType;
        this.eventType = eventType;
        this.eventExchange = eventType.id() + "_events";
    }

    /**
     * @param requestType Type descriptor for request/response type
     * @param eventType Type descriptor for service subscription event type
     * @param eventExchange Name of the exchange events are published to
     */
    public BasicServiceTypeInformation( TypeDescriptor<T> requestType, TypeDescriptor<U> eventType, String eventExchange )
    {
        this.requestType = requestType;
        this.eventType = eventType;
        this.eventExchange = eventExchange;
    }

    @Override
    public TypeDescriptor<T> getDescriptor()
    {
        return requestType;
    }

    @Override
    public TypeDescriptor<U> getSubscriptionDescriptor()
    {
        return eventType;
    }

    @Override
    public String getEventExchange()
    {
        return eventExchange;
    }
}
