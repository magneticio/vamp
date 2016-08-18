'use strict';

var _ = require('lodash');
var http = require('request');
var vamp = require('vamp-node-client');

var api = new vamp.Api();

var interval = 60; // seconds

function run() {
  api.config(function (config) {

    var mesos = config['vamp.container-driver.mesos.url'];
    var marathon = config['vamp.container-driver.marathon.url'];
    var zookeeper = config['vamp.persistence.key-value-store.zookeeper.servers'];
    var haproxy = config['vamp.gateway-driver.haproxy.version'];
    var logstash = config['vamp.gateway-driver.logstash.host'];

    http({
      url: mesos + '/master/slaves'
    }, function (error, response, body) {
        if (!error && response.statusCode == 200) {

          var response = JSON.parse(body);
          var instances = response.slaves.length;

          var vga = {
            "id": "/vamp/vamp-gateway-agent",
            "args": [
              "--storeType=zookeeper",
              "--storeConnection=" + zookeeper,
              "--storeKey=/vamp/haproxy/" + haproxy,
              "--logstash=" + logstash + ":10001"
            ],
            "cpus": 0.2,
            "mem": 256.0,
            "instances": instances,
            "container": {
                "type": "DOCKER",
                "docker": {
                    "image": "magneticio/vamp-gateway-agent:0.9.0",
                    "network": "HOST",
                    "portMappings": [],
                    "privileged": true,
                    "parameters": []
                }
            },
            "env": {},
            "constraints": [
                ["hostname", "UNIQUE"]
            ],
            "labels": {}
          };

          console.log('checking if deployed: /vamp/vamp-gateway-agent');

          http({
            url: marathon + '/v2/apps/vamp/vamp-gateway-agent',
            }, function (error, response, body) {

              var deploy = response.statusCode == 404;

              if (!deploy) console.log('already deployed, checking number of instances...');

              if (!deploy && response.statusCode == 200) {
                var app = JSON.parse(body)
                console.log('deployed instances: ' + app.app.instances);
                console.log('expected instances: ' + instances);
                deploy = app.app.instances != instances;
              }

              if (deploy) {

                console.log('deploying...');

                http({
                  url: marathon + '/v2/apps',
                  method: 'POST',
                  json: vga
                  }, function (error, response, body) {
                    if (!error && response.statusCode === 201)
                      console.log('done.');
                    else
                      console.log('error.');
                });
              } else console.log('done.');
          });
        }
    });
  });
}

run();

setInterval(run, interval * 1000);
