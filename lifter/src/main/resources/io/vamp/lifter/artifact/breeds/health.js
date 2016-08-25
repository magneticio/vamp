'use strict';

var _ = require('lodash');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

var allHealth = [];
var allGateways = [];

function health(lookupName, tags) {

    var errorCode = 500;
    var term = {ft: lookupName};
    var range = {ST: {gte: errorCode}};

    metrics.count(term, range, window, function (total) {
        var value = total > 0 ? 0 : 1;

        allHealth[lookupName] = value;

        api.event(tags, value);
    });
}

var process = function() {

  api.gateways(function (gateways) {

      allGateways = gateways;
      allHealth = [];

      _.forEach(gateways, function (gateway) {
          health(gateway.lookup_name, ['gateways:' + gateway.name, 'health']);

          _.forOwn(gateway.routes, function (route, routeName) {
              health(route.lookup_name, ['gateways:' + gateway.name, 'routes:' + routeName, 'health']);
          });
      });
  });


//  api.deployments(function (deployments) {
//      _.forEach(deployments, function (deployment) {
//          _.forOwn(deployment.clusters, function (cluster, clusterName) {
//              _.forOwn(cluster.gateways, function (gateway, gatewayName) {
//                  var gateway = _.find(allGateways, function(item) { return item.name === deployment.name + '/' + clusterName + '/' + gatewayName; });
//                  if (gateway) health(gateway.lookup_name, 'deployments:' + deployment.name);
//              });
//          });
//      });
//  });
};

process();

setInterval(process, period * 1000);
