Release Notes
==============

Releases are published to the maven repository at https://repo.totalgrid.org/artifactory/webapp/home.html

Version Numbers are of the format {Major}.{Minor}.{Patch}.

* Major version updates are reserved for major features releases
* Minor version updates imply a significant api or datatype change
* Patch version updates should have little to no api or datatype changes

Version 0.4.0
==============

Major refactoring of communication client and threading structure. See MIGRATION_STEPS.markdown
for help in updating an application from 0.3.x.

### Major Features:

* Reef client API is much simpler and easier to use, no reaching into any implementation packages
* Connecting to a reef broker is now by default a synchronous "single-shot" operation
* Service Specific Java interfaces are now used to auto-generate wrappers around scala implementations
* BatchServiceRequest is used by the loader-xml project to load models up to 70% faster than in 0.3.3. reef-175
* BatchServiceRequest is used to unload model, up to 90% faster than in 0.3.3
* Now setting qpid heartbeat timeout, requires new setting in config files: org.totalgrid.reef.amqp.heartbeatTimeSeconds=30. Fixes reef-183

### Shell Commands:

* Shell will attempt to auto-login using credentials in the org.totalgrid.reef.user.cfg file
* reef:unload will automatically disable and wait for endpoints to go to COMMS_DOWN before deleting. reef-173
* Better error handling when commands fail

### Service/API Updates:

* Entity service only returns BadRequestException about unknown types if no results are returned
* Added getEntityRelations queries to EntityService to make complex queries simpler to execute.
* Added SimpleAuthRequest proto and service to make logging in and out not dependent on complex reef types.
* Command requests are checked to verify right command type is used (control vs. setpoint)

### Reef Internals:

* Protocol interface includes reef Client
* Removed all usage of scala actors, replaced with Java Executors
* Added many more integration tests for dnp3 and model loading and upload, fixed many endpoint related bugs.
* Upgraded to karaf 2.2.4 from 2.2.2

Version 0.3.3
==============

Minor bug fix and feature release.

### Bug Fixes:

* Consistent error handling and error messages for MeasurementBatchService REEF-178, REEF-179
* loader-xml doesn't require indexes for protocols not explicitly an indexed protocol

### Client Updates:

* Added alterEndpointConnectionState calls to EndpointManagerService
* Added bindCommandHandler() implementation to CommandService

Version 0.3.2
==============

Minor bug fix release.

### Bug Fixes:

* Loader timeouts increased to handle removing points with large measurement counts
* Measurement removal happens during point removal
* reef:resetdb command asks for system password twice
* Minor fixes to entity and entity attributes services


Version 0.3.1
==============

### Major Features:

* DNP3 Slave Protocol Implemented (updated dnp3 library to 1.0.0)
* Support for AMQP over SSL
* Separate CLI distribution for inspecting remote systems

### Client Updates:

* RequestSpy logging for client API calls
* Client supports SSL connection broker using standard java trust-store files
* Maximum number of returned objects is client configurable (was default of 100)

### Service/API Updates:

* Added more requests for entities and their children
* Points and Commands can searched for by Endpoint
* Entity service only fails requests with unknown types if nothing is found
* Endpoints can be modeled as either data “sink” or “source”
* Reef objects can be created with externally defined UUIDs
* Added getCommandHistory for a single command
* Fixed response codes for Entity and EntityAttribute services
* When an Entity is deleted, all edges and events pointing to it are removed 

### Shell Commands:

* New configfile:download and configfile:upload commands to make adjusting configFiles easier
* New entity:tree command to view relationships between objects
* New command:hist to look at global and individual command history
* New event:view and event:publish commands
* Commands for managing event-configurations: event-config:list,view,delete,create
* Added meas:override,block,unblock for managing overriding measurements

### Xml Loader:

* Enabled Xml validation, loaded files now must be well formed before attempt processing
* Added support for attaching ConfigFiles and Attributes to any point/command/equipment
* Progress output wraps every 50 characters
* Config Files are loaded in a binary safe fashion
* Numerous minor bug fixes and enhanced error reporting

### Breaking Changes:

* AMQPConnectionInfo has more parameters
* Measurements subscriptions are no longer “raw” events and are not binary compatible with 0.3.0 client
* Removed “core” user and changed system user password to be user defined

### Reef Internals:

* Added Mockito, JMock test dependencies
* Created proto-shell entry-point to run outside of OSGi
* Reef etc files split up by functional area (amqp, user, node, sql)
* Refactored services to move all state into a RequestContext object and make services stateless.
* Broke up ‘core’ project into ‘application-framework’, ‘fep’, ‘measproc’ and ‘services’
* Scala library moved to 2.9.0-1

### Bug Fixes:

* Measproc/FEP correctly ignore connections received during shutdown
* Event services gracefully handles missing attributes when rendering events

Version 0.3.0
==============

Primarily Service and API refinements and refactorings.

### API Updates:

* Java facing APIs are 100% java
* XxxxService interfaces are now implemented using a SessionExecutionPool wrapper that
  uses a pool of sessions and handles the connection going up and down.
* Subscriptions don't automatically start flowing messages, a start() function was added
* Replaced getOne, getMany, getAsyncOne with unified functions that return Promises that
  have the one() or many() expectation functions.
* Javadoc and sources jars are now published into maven repo

### Service Updates:

* All protos for "long lived" and "static" resources now have ReefUUID field
* "long lived" and "static" resources use UUID instead of integers
* Distribtion renamed to totalgrid-reef-0.3.0.
* Loader now attaches Analog, Status, Counter types to Points on Load
* Authorization is now CRUD rather than verb based (can distinguish between a create and update)

### Shell Commands:

* Added configfile:list command to view config files

### Breaking Changes:

* APIs renamed and moved packages, "I" prefix removed, all java apis moved to 
  org.totalgrid.reef.japi.*.
* Most protos are no longer binary comptabile with 0.2.x versions.

### Reef Internals:

* Updated karaf to version 2.2.1
* Updated squeryl to 0.9.4-RC7-uuid for UUID support
* Updated to qpid 0.10 java-client-api
* Logback used in local tests
* Logging output now has correct line numbers from scala code
* Updated pax-logging to 1.6.3-LOCATION with line number patches until 1.6.3 is released
* DNP3 logging has better error messages


### Bug Fixes:

Version 0.2.3
==============

Primarily a stability and usability release, very limited new functionality.

### API Updates:

* IConnection got connect/disconnect functions
* IConnection start and stop are idempotent
* Added Mid-Level-APIs for Agents, Entities, MeasurementOverrides and Points
* Calling ISession.close is thread safe and will terminate any active requests, all future requests will fail instantly
* Mid-Level-APIs now have createSubscription(EventAcceptor<T>) functions so we don't need to pass sessions to mid-level client consumers
* Duplicate getMsg() function removed from ReefServiceException, use getMessage()

### Service Updates:

* Can subscribe to Events and Alarms through EventList and AlarmList services
* When issuing a Command we block until the status code is returned from the field, doesn't return executing
* Configuration files can now include custom types for a point or command. REEF-39
* Added setpoint support to karaf shell and xml loader

 ### Shell Commands:

* endpoint:list and channel:list to inspect communication path
* Added suite of Agent related commands to create/remove agents and change passwords
* "reef:login" no longer has password as argument, prompts for password
* added -dryRun option to "reef:load" to quickly check file for correctness
* invalid system models will not by load by default (added -ignoreWarnings option)
* "meas:download" will download measurement history to a Comma Separated File (CSV) for offline processing
* Added suite of Alarm related commands including silence, acknowledge and remove.
* Added remote-login command to support using karaf HMI on remote reef node
* Added metrics:throughput command to quickly measure measurement rates. use: "metrics:throughput *.measProcessed"
* Added "point:commands" command to display points with their feedback commands.
 
### Breaking Changes:

* FrontEndPort Protobuf names changed to CommChannel
* Post verb is now off by default, only specific services use it now
* Commands now have correct "owns" relationship to parent equipment, only "feedback" to points

### Reef Internals:

* Protocol Adapters now inform system when channels (ports) and endpoints change online state
* All shell commands use Mid-Level-APIs
* ClientSession interface includes SubscriptionManagement trait and close function
* Postgres measurement store implementation was refactored to use multiple tables to decouple current value
  and history requests so more historical measurements can be stored without slowing down current value queries.

### Bug Fixes:

* "reef:resetdb" clears login token to avoid confusing error messages REEF-33
* Reef server no longer has 10 second delay on stopping REEF-29
* DNP Connections are correctly dropped when shutting down front ends REEF-17
* Event Service (not EventList Service) returns most recent 100, not oldest REEF-34
* Support for PULSE_TRIP control code for DNP endpoints REEF-31
* DNP protocol adapter sets units correctly REEF-24
* Other bugs mentioned in previous sections: REEF-43, REEF-10
* Alarm retrievals and updates by UID work as expected now
* Better loader warnings and behavior when config files are missing (includes REEF-46)
* Fixed "Content not allowed in prolog" XML loading issue REEF-61
* Multiple Controls with same index but different dnp3Options can be loaded by loader REEF-65
* Fixed measurement history trimming bug that caused slow down over time REEF-67

Version 0.2.3-dev
==============
### API Updates:

* Added more instrumentation and calculations to get measurement rates
* Created mid-level APIs for config files, commands, measurements and events
* Added new Service "EntityAttributes" that allows adding key-value data to entities
* Added display name to Commands
* Added "ValueMap" boolean -> string conversions to measurement pipeline
* Added synchronous start/stop functions to IConnection

### Breaking Changes:

* MeasurementHistory no longer has "ascending" flag, always oldest to newest. REEF-27
* Command UID is now Entity UUID, not name
* Units now more strictly enforced in XML loading
* IConnection Start/Stop now take millisecond timeouts

### Reef Internals:

* created wrapper to capture live request/responses for documentation
* Service providers are now asynchronous by nature
* Updated to scaliform 0.0.9
* Fixed mixed source compilation issues: REEF-19

### Bug Fixes:

* Indexes not needed for benchmark protocol communication endpoints
* Timers use "constant delay" rather than "constant offset" semantics
* Measurements are flushed when overridden (rather than on next field measurement update)
* Services requests for "all" of a resource are truncated to 100 entries: REEF-20
* Measurements from DNP3 endpoints have correct units: REEF-24
* Fixed corruption of password salts that was causing test failures.


Version 0.0.3
=============
### Major changes:
* Summary points (counts for alarms and 'abnormal' measurements)
* Events have a rendered text message and the arguments are now name+value rather than positional
* Replaced measurement and command streaming with "addressable services" so fep can't flood broker with messages
* Refactored MeasProc + FEP assignment into services and removed problematic standalone coordinator (faster and less prone to failure)
* Changed threading model for all entry points so actor starvation wont cause spurious application timeouts


Version 0.0.2
=============
### Major changes:
* Renamed/Moved proto definitions to match proto style guide and be more consistent
* Events + Alarms are now being generated and can be subscribe to by substation, device or point
* Rails HMI is now 100% on the bus, including the user authorization
* demo system has configuration to talk to demo hardware in the lab
* simulator produces 'random walks' to make the test data more interesting, usable
* # of 'abnormal' points is counted as a POC of the summary mechanisms


Version 0.0.1
=============
### Major changes:
* Entity queries (list of substations, list of equipment in substations, points in substations, commands under points etc)
* Points and Measurements (including subscriptions)


RoadMap
=============

* 

#### Formatting

This file uses github flavored markdown, a good test renderer is available at github:
http://github.github.com/github-flavored-markdown/preview.html