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
package org.totalgrid.reef.japi;

/**
 * thrown to represent an unexpected and uncaught error in the services. This is like
 * a 500 Error code on the web, it means the request was well formed and valid
 * but something unexpected occurred on the server which caused an internal error, retrying
 * the same request is likely to cause the same error
 */
public class InternalServiceException extends ReplyException
{
    public InternalServiceException( String msg )
    {
        super( msg, Envelope.Status.INTERNAL_ERROR );
    }
}
