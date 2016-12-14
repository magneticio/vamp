'use strict';

let _ = require('highland');
let vamp = require('vamp-node-client');

let api = new vamp.Api();
let metrics = new vamp.ElasticsearchMetrics(api);

let window = 30; // seconds

function publish(tags, metrics) {
  api.log('metrics: [' + JSON.stringify(tags) + '] - ' + metrics);
  api.event(tags, metrics, 'metrics');
}

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
