var _ = require('lodash');

var elasticsearch = require('elasticsearch');

var esClient = new elasticsearch.Client({
  host: process.env.VAMP_ELASTICSEARCH_CONNECTION,
  log: 'info'
});

var index = function(tags, value) {
  esClient.index({
      index: 'vamp-pulse',
      type: 'event',
      body: {
        tags: tags,
        value: value
      }
    },
    function (error, response) {}
  );
}

var seconds = 30;

var metrics = function(gateways) {
  _.forEach(gateways, function(gateway) {

    esClient.search({
      index: 'logstash-*',
      type: 'haproxy',
      body: {
        query: {
          filtered: {
            query: {
              match_all: {}
            },
            filter: {
              bool: {
                must: [
                  {
                    term: {
                      ft: gateway.lookup_name
                    }
                  },
                  {
                    range: {
                      "@timestamp": {
                        gt: "now-" + seconds + "s"
                      }
                    }
                  }
                ]
              }
            }
          }
        },
        aggregations: {
          Tt: {
            avg: {
              field: "Tt"
            }
          }
        },
        size: 0
      }
    }).then(function (resp) {

        var total = resp.hits.total;
        var rate = Math.round(total / seconds * 100) / 100;
        var responseTime = Math.round(resp.aggregations.Tt.value * 100) / 100;

        console.log("gateway       : " + gateway.name);
        console.log("total         : " + total);
        console.log("rate          : " + rate);
        console.log("response time : " + responseTime);

        index(["gateways", "gateways:" + gateway.name, "gateways:" + gateway.lookup_name, "metrics", "metrics:rate"], rate);
        index(["gateways", "gateways:" + gateway.name, "gateways:" + gateway.lookup_name, "metrics", "metrics:response-time"], responseTime);

    }, function (err) {
        console.log(err.message);
    });
  });
};

var zookeeper = require('node-zookeeper-client');

var zkClient = zookeeper.createClient(process.env.VAMP_KEY_VALUE_STORE_CONNECTION);
var path = '/vamp/gateways';

zkClient.once('connected', function () {
  zkClient.getData(
    path,
    function () {},
    function (err, data, stat) {
      if (err) throw err;
      if (data) metrics(JSON.parse(data.toString()));
      zkClient.close();
    });
});

zkClient.connect();
