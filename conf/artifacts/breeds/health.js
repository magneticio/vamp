'use strict';

let _ = require('highland');
let vamp = require('vamp-node-client');

let api = new vamp.Api();
let metrics = new vamp.ElasticsearchMetrics(api);

let window = 30; // seconds

function health(lookupName, tags) {
  let errorCode = 500;
  let term = {ft: lookupName};
  let range = {ST: {gte: errorCode}};

  return metrics.count(term, range, window).map(function (total) {
    return total > 0 ? 0 : 1;
  }).tap(function (health) {
    publish(tags, health);
  });
}

function publish(tags, health) {
  api.log('health: [' + JSON.stringify(tags) + '] - ' + health);
  api.event(tags, health, 'health');
}

let collectHealth = function (x1, x2) {
  return x1 * x2;
};

api.gateways().flatMap(function (gateway) {
  // gateway health
  return health(gateway.lookup_name, ['gateways:' + gateway.name, 'gateway', 'health']).flatMap(function () {
    return api.namify(gateway.routes).flatMap(function (route) {
      // route health
      return health(route.lookup_name, ['gateways:' + gateway.name, 'route', 'routes:' + route.name, 'health']);
    });
  });
}).done(function () {
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
