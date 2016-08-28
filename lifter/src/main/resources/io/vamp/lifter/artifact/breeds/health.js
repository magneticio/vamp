'use strict';

var _ = require('lodash');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

function health(lookupName, tags) {

    var errorCode = 500;
    var term = {ft: lookupName};
    var range = {ST: {gte: errorCode}};

    metrics.count(term, range, window, function (total) {
        api.event(tags, total > 0 ? 0 : 1);
    });
}

var process = function() {

  api.gateways(function (gateways) {

      _.forEach(gateways, function (gateway) {

          health(gateway.lookup_name, ['gateways:' + gateway.name, 'gateway', 'health'], 'health');

          _.forOwn(gateway.routes, function (route, routeName) {
              health(route.lookup_name, ['gateways:' + gateway.name, 'routes:' + routeName, 'route', 'health'], 'health');
          });
      });

      api.deployments(function (deployments) {
          _.forEach(deployments, function (deployment) {
              _.forOwn(deployment.clusters, function (cluster, clusterName) {
                  _.forEach(cluster.services, function (service) {
                      _.forOwn(cluster.gateways, function (gateway, gatewayName) {
                          _.forOwn(gateway.routes, function (route, routeName) {
                              if (routeName === service.breed.name)
                                  health(route.lookup_name, ['deployments:' + deployment.name, 'clusters:' + clusterName, 'services:' + service.breed.name, 'service', 'health'], 'health');
                          });
                      });
                  });
              });
          });
      });
  });
};

process();

setInterval(process, period * 1000);
