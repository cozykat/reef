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
/**
 * Proto definition file for FEP.
 * 
 * <pre>
 * package org.totalgrid.reef.client.service.proto.FEP;
 * 
 * import "ApplicationManagement.proto";
 * import "Model.proto";
 * 
 * option java_package = "org.totalgrid.reef.client.service.proto";
 * option java_outer_classname = "FEP";
 * 
 * message IpPort {
 *     enum Mode {
 *         CLIENT = 1; // the slave device is trying to establish a connection to a known port (uncommon) 
 *         SERVER = 2; // the slave device has an open port waiting for connection
 *     }
 *     required string address  = 1;
 *     required uint32 port     = 2;
 *     optional Mode   mode     = 3 [default = SERVER];
 *     optional string network  = 4 [default = 'any'];
 * }
 * 
 * message SerialPort {
 *     enum Flow {
 *       FLOW_NONE = 1;
 *       FLOW_HARDWARE = 2;
 *       FLOW_XONXOFF = 3;
 *     }
 *     
 *     enum Parity {
 *         PAR_NONE = 1;
 *         PAR_EVEN = 2;
 *         PAR_ODD  = 3;
 *     }
 *     
 *     required string location   = 1; //name of machine that has the correct physical serial port 
 *     required string port_name  = 2; // should be either a /dev/* or COM*
 *     optional uint32 baud_rate  = 3 [default = 9600];
 *     optional uint32 stop_bits  = 4 [default = 1];
 *     optional uint32 data_bits  = 5 [default = 8];
 *     optional Parity parity     = 6 [default = PAR_NONE];
 *     optional Flow   flow       = 7 [default = FLOW_NONE]; 
 * }
 * 
 * // this is a wrapper type that we can reference by name or id which contains channel details
 * // either ip or serial will be populated, client code must check which is set.
 * message CommChannel {
 * 
 *     enum State {
 *         CLOSED = 1;
 *         OPENING = 2;
 *         OPEN = 3;
 *         ERROR = 4;
 *         UNKNOWN = 5;
 *     }
 * 
 * 
 *     optional org.totalgrid.reef.client.service.proto.Model.ReefUUID       uuid    = 1;
 *     optional string     name   = 2;
 *     optional IpPort     ip     = 3;
 *     optional SerialPort serial = 4;
 *     optional State      state  = 5;
 * }
 * 
 * message CommEndpointRouting {
 *     optional string service_routing_key   = 1;
 * }
 * 
 * message FrontEndProcessor {
 *     optional org.totalgrid.reef.client.service.proto.Model.ReefUUID       uuid       = 1;
 *     repeated string protocols = 2; // protocol names ex: dnp3, modbus, benchmark
 *     optional org.totalgrid.reef.client.service.proto.Application.ApplicationConfig app_config = 3;
 * }
 * 
 * message EndpointOwnership {
 *     repeated string points   = 1;
 *     repeated string commands = 2;
 * }
 * 
 * // and endpoint config _is_a_ LogicalNode with the extra port + protocol parameters
 * message Endpoint {
 *     optional org.totalgrid.reef.client.service.proto.Model.ReefUUID       uuid          = 1;
 *     optional string            name         = 2;
 *     optional org.totalgrid.reef.client.service.proto.Model.Entity entity = 7;
 *     optional string            protocol     = 3;
 *     optional CommChannel       channel      = 4;
 *     optional EndpointOwnership ownerships   = 6;
 *     repeated org.totalgrid.reef.client.service.proto.Model.ConfigFile        config_files = 5;
 *     // some endpoints produce data and need measurement processors
 *     optional bool dataSource = 8;
 * }
 * 
 * message EndpointConnection {
 * 
 *     enum State {
 *         COMMS_UP = 1;
 *         COMMS_DOWN = 2;
 *         UNKNOWN = 3;
 *         ERROR = 4;
 *     }
 * 
 *     optional org.totalgrid.reef.client.service.proto.Model.ReefID                 id                 = 1;
 *     optional FrontEndProcessor      front_end           = 2;
 *     optional Endpoint     endpoint            = 3;
 *     optional State                  state               = 4;
 *     optional CommEndpointRouting    routing             = 5;
 *     optional uint64                 last_update         = 7;
 *     optional bool                   enabled             = 8;
 * }
 * 
 * /*
 *   When an application wants to handle the commands for an endpoint they should post this message with the
 *   endpoint_connection object and the name/id of the SubscriptionBinding queue).
 * -/
 * message CommandHandlerBinding {
 *     optional EndpointConnection   endpoint_connection       = 1;
 *     optional string               command_queue             = 2;
 * }
 * </pre>
 */
package org.totalgrid.reef.client.service.protodoc.fep;

