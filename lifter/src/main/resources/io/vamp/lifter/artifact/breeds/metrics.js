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
              api.event(['gateways:' + gateway.name, 'metrics:rate'], rate);
              api.event(['gateways:' + gateway.name, 'metrics:responseTime'], responseTime);
          });
      });
  });
};

setInterval(process, period * 1000);
