'use strict';

var _ = require('highland');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

function publish(tags, metrics) {
  console.log('metrics: [' + JSON.stringify(tags) + '] - ' + metrics);
  api.event(tags, metrics, 'metrics');
}

var run = function () {

  api.gateways().each(function (gateway) {

    metrics.average({ft: gateway.lookup_name}, 'Tt', window).each(function (response) {
      publish(['gateways:' + gateway.name, 'gateway', 'metrics:rate'], response.rate);
      publish(['gateways:' + gateway.name, 'gateway', 'metrics:responseTime'], response.average);
    });

    api.namify(gateway.routes).each(function (route) {
      metrics.average({ft: route.lookup_name}, 'Tt', window).each(function (response) {
        publish(['gateways:' + gateway.name, 'routes:' + route.name, 'route', 'metrics:rate'], response.rate);
        publish(['gateways:' + gateway.name, 'routes:' + route.name, 'route', 'metrics:responseTime'], response.average);
      });
    });
  });
};

run();

setInterval(run, period * 1000);
