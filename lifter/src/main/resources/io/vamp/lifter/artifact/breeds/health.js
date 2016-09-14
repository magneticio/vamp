'use strict';

var _ = require('highland');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

function health(lookupName, tags) {

  var errorCode = 500;
  var term = {ft: lookupName};
  var range = {ST: {gte: errorCode}};

  return metrics.count(term, range, window).map(function (total) {
    return total > 0 ? 0 : 1;
  }).tap(function (health) {
    publish(tags, health);
  });
}

function publish(tags, health) {
  api.event(tags, health, "health");
}

var run = function () {

  var collectHealth = function (x1, x2) {
    return x1 * x2;
  };

  api.gateways().each(function (gateway) {
    // gateway health
    health(gateway.lookup_name, ['gateways:' + gateway.name, 'gateway', 'health']);

    api.namify(gateway.routes).each(function (route) {
      // route health
      health(route.lookup_name, ['gateways:' + gateway.name, 'route', 'routes:' + route.name, 'health']);
    });
  });

  api.deployments().each(function (deployment) {
    api.namify(deployment.clusters).flatMap(function (cluster) {
      return api.namify(cluster.gateways).flatMap(function (gateway) {
        return _(cluster.services).flatMap(function (service) {
          return api.namify(gateway.routes).find(function (route) {
            return route.name === service.breed.name;
          }).flatMap(function (route) {
            // service health based on corresponding route health
            return health(route.lookup_name, ['deployments:' + deployment.name, 'clusters:' + cluster.name, 'service', 'services:' + service.breed.name, 'health']);
          });
        });
      }).reduce1(collectHealth).tap(function (health) {
        // cluster health
        publish(['deployments:' + deployment.name, 'clusters:' + cluster.name, 'cluster', 'health'], health);
      });
    }).reduce1(collectHealth).tap(function (health) {
      // deployment health
      publish(['deployments:' + deployment.name, 'deployment', 'health'], health);
    }).done(function () {
    });
  });
};

run();

setInterval(run, period * 1000);
