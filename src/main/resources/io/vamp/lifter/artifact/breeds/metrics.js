'use strict';

var _ = require('lodash');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

var process = function() {
  api.gateways(function (gateways) {
      _.forEach(gateways, function(gateway) {
          metrics.average({ ft: gateway.lookup_name }, 'Tt', window, function(total, rate, responseTime) {
              api.event(['gateways:' + gateway.name, 'gateway', 'metrics:rate'], rate, 'metrics');
              api.event(['gateways:' + gateway.name, 'gateway', 'metrics:responseTime'], responseTime, 'metrics');
          });

          _.forOwn(gateway.routes, function (route, routeName) {
              metrics.average({ ft: route.lookup_name }, 'Tt', window, function(total, rate, responseTime) {
                  api.event(['gateways:' + gateway.name, 'routes:' + routeName, 'route', 'metrics:rate'], rate, 'metrics');
                  api.event(['gateways:' + gateway.name, 'routes:' + routeName, 'route', 'metrics:responseTime'], responseTime, 'metrics');
              });
          });
      });
  });
};

process();

setInterval(process, period * 1000);
