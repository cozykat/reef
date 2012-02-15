
Reef Javascript Support
=========================

If the server has the http-bridge installed it means we can use any Http client to pull data from the server. In
particular it makes it very simple to access a small view into the data using javascript.

jQuery Client
=============

We have created a "native", jQuery based, client to the reef services that provides most of the same functionality as
the java client. We provide an auto-generated API layer to most of the functions in ReefServicesList. The client layer
is implemented in a similar fashion to the java client; the largest fundamental difference is there isn't the idea of
a "connection" to the server that can fail, each request is an independent event.

We chose to write the client using jQuery because it is the most popular framework and has a native promise like ajax
function (since 1.5.x). This allows us to implement the client in javascript in nearly the identical way as in the java
client. This should mean that transitioning between the two languages is relatively easy.

This guide is written assuming that the reader is already somewhat familiar with javascript. Below are some helpful links
for the most important jQuery pieces we make use of.

- jQuery ajax: http://api.jquery.com/jQuery.ajax/ (see "Callback function queues" section).
- Deferred Object (Promise): http://api.jquery.com/category/deferred-object/
- jQuery plugin guide: http://docs.jquery.com/Plugins/Authoring


### Code Organization

The client is split into two major of components:

- src/main/web/reef.client.js : Core client functionality. Includes 'login' and the base function used by api services
  to make requests to the server.
- src/main/web/reef.client.core-services.js : Auto-generated functions for all of the compatible functions in
  ReefServices. This code should never be altered by hand, instead the changes should be made to HttpServiceCallBindings.scala.

### Api Requests

The api request definitions in reef.client.core-services.js are auto-generated from the java Api interface files. This
means that the reef services javadocs should be used as the primary source of documentation for the functions provided
by the javascript client. We include the javadoc for each interface and method for convenience but we recommend that
developers refer to the javadoc api guides when planning out their applications.

Below is an example of an api request definition. The definition includes two important pieces of meta-data about the
function: the type of the result and how many results we should expect.

```javascript
/**
 * Gets list of all currently registered applications
 */
calls.getApplications = function() {
    return client.apiRequest({
        request: "getApplications",
        style: "MULTI",
        resultType: "application_config"
    });
};
```

"style" will be one of:

- MULTI - an array of 0 or more results
- SINGLE - a single result (or a failure will be thrown)
- OPTIONAL - a single result or undefined if there is no data

"resultType" is a form of the name of the proto (as understood by the /convert/ servlet). This name can be used to get
a description of the fields available on the result object by calling /convert/{resultType}. (See describeResultType section)

Some api functions are not ported to the javascript client. If they are not available there will be a comment in the
services definition file describing why. Usually it is because the types of the arguments are too complex to URL encode
but in most cases there is a related function that has the same effect with simpler arguments. If a needed function is
not available please include this comment in any feature requests:

```javascript
// Can't encode executeCommandAsControl : Can't encode type: org.totalgrid.reef.client.service.proto.Model.Command
```

### Promises

Each api request to the server returns a jQuery Deferred object (Promise) representing the eventual success or
failure of the request. We then attach functions to those promises that should be called with the result using the
.done() method of Deferred.

```javascript
var promise = client.getPoints();
promise.done(function(points){
    // do some work with the list of points here
});
```

The Deferred object nearly exactly matches the Promise class we have in the java client except that it lacks the "await"
method. This is expected because of the effectively single threaded nature of browser javascript runtimes, all work based
on ajax calls needs to be defined using callbacks (closures). Using promises makes writing callback heavy code easier
because we can making of a request and the handling of the data into separate sections, something that is hard to do
using naive callbacks.

#### Error Handling

We can also attach a failure handler to be notified if the request fails. For each of these handlers we can attach more
than one callback, they will all be called in the order they were added.

```javascript
var promise = client.getPoints();
promise.fail(function(errorMessage){
    // print or display the error message here.
});
```

We can also include a generic error handler that is notified of all failures.

### Examples

Retrieve the list of all Points in the system and print their names. Notice we use the done() to get the results of
request passed into our "payload function" which prints all the point names. We are also using the jQuery helper $.each
to iterate through the list cleanly.

```javascript
client.getPoints().done(function(points){
    $.each(points, function(point){
        console.log(point.name);
    });
}).fail(function(errString){
    // we can catch the error for the individual request here.
    console.log("Error getting points: " + errString);
};
```

### Limitations

- No subscriptions
- Can't use functions that require passing in complex types (protobuf objects or enums).

## describeResultType()

All api request promises define the convenience function describeResultType() that makes api calls somewhat reflective.
The describe call asks the server to describe the data so the describeResultType return value is another promise we
need a done() handler to get the payload data. The payload data is the protobuf "self-descriptor" format serialized to JSON.

In most cases it is easier to read the original proto definition files (since they are designed to be human readable).
Those files are embedded in the javadocs in the *.protodoc packages. This interface is primarily for making "smart"
displays that don't know what sort of data they will be displaying.

```javascript
client.getApplications().describeResultType().done(function(descriptor){
  console.log(JSON.stringify(descriptor, undefined, 2));
});
```

```json
{
  "name": "ApplicationConfig",
  "field": [
    {
      "name": "uuid",
      "number": 1,
      "label": "LABEL_OPTIONAL",
      "type": "TYPE_MESSAGE",
      "type_name": ".org.totalgrid.reef.client.service.proto.Model.ReefUUID"
    },
    {
      "name": "user_name",
      "number": 2,
      "label": "LABEL_OPTIONAL",
      "type": "TYPE_STRING"
    },
    {
      "name": "online",
      "number": 10,
      "label": "LABEL_OPTIONAL",
      "type": "TYPE_BOOL"
    },
    {
      "name": "times_out_at",
      "number": 11,
      "label": "LABEL_OPTIONAL",
      "type": "TYPE_UINT64"
    }
  ]
}
```

Widgets
========

We have provided a small set of simple, embeddable, javascript widgets that allow external applications to embedded
ui components that expose different aspects of the running system.

The widgets are written as jQuery plugins and assume that jQuery will have be loaded before they are imported. The
widgets have been developed against jQuery 1.7.1.

Each widget is generally designed to be called on a DOM object which is the container of the resultant data. The data
provided by the plugins will generally be a set of <div> elements that describe their content using CSS classes. This
should allow the embedding page to customize the look and feel of the widgets without needing to edit the plugin code.
In general the CSS files provided with the widgets should be considered more as documentation of the css classes rather
than a finished product.

Where ever possible the widgets should allow delegating the displaying of the data to an external function so a page can
render the data exactly as desired.

Widgets are tested and should work against most modern web browsers. (Chrome >= 15, Internet Explorer >= 7, Firefox >= 3)

[Minimal Widget Demo](js-service-client/src/main/web/reef.widget-demos.html)

Current Value Widget
---------------------

This widget is useful for showing the state of a known set of Measurements in an external web page. A page using this
widget must import jQuery and reef.widget.measurement.js and will want to provide CSS definitions for the the classes
in reef.widget.measurement.css. Below is the simplest invocation for the displayMeasurements routine but there are many
other options that can be overridden (see source for details).

```javascript
$(document).ready(function(){
    $('#data_div').displayMeasurements({
        'server'     : 'https://127.0.0.1:8886',
        'point_names' : [
            'SimulatedSubstation.Line01.Current',
            'StaticSubstation.Line02.Current',
            'SimulatedSubstation.Breaker01.Bkr',
            'StaticSubstation.Breaker02.Bkr',
        ]
    });
});
```