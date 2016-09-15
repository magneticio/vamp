'use strict';

var _ = require('highland');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

var run = function () {

  api.gateways().each(function (gateway) {

    metrics.average({ft: gateway.lookup_name}, 'Tt', window).each(function (response) {
      api.event(['gateways:' + gateway.name, 'gateway', 'metrics:rate'], response.rate, 'metrics');
      api.event(['gateways:' + gateway.name, 'gateway', 'metrics:responseTime'], response.average, 'metrics');
    });

    api.namify(gateway.routes).each(function (route) {
      metrics.average({ft: route.lookup_name}, 'Tt', window).each(function (response) {
        api.event(['gateways:' + gateway.name, 'routes:' + route.name, 'route', 'metrics:rate'], response.rate, 'metrics');
        api.event(['gateways:' + gateway.name, 'routes:' + route.name, 'route', 'metrics:responseTime'], response.average, 'metrics');
      });
    });
  });
};

run();

setInterval(run, period * 1000);
