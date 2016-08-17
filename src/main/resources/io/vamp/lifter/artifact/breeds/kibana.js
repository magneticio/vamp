'use strict';

var _ = require('lodash');
var http = require('request');
var vamp = require('vamp-node-client');

var api = new vamp.Api();

var interval = 30; // seconds

var elasticsearch;
var logstash = 'logstash-*';

function createKibanaIndex(otherwise) {
  http({
    url: elasticsearch + '/.kibana/config/_search'
  }, function (error, response, body) {
      if (!error && response.statusCode == 200) {

        var kibana;
        var response = JSON.parse(body);

        for (var i = 0; i < response.hits.hits.length; i++) {
          var hit = response.hits.hits[i];
          if (hit._index === '.kibana') {
            kibana = hit;
            break;
          }
        }

        if (!kibana._source.defaultIndex) {

          kibana._source['defaultIndex'] = logstash;

          http({
            url: elasticsearch + '/' + hit._index + '/' + hit._type + '/' + hit._id,
            method: 'POST',
            json: hit._source
            }, function (error, response, body) {}
          );

          http({
            url: elasticsearch + '/.kibana/index-pattern/' + logstash,
            method: 'POST',
            json: {
                'title': logstash,
                'timeFieldName': '@timestamp',
                'fields': '[{"name":"_index","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"geoip.ip","type":"ip","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"@timestamp","type":"date","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"geoip.location","type":"geo_point","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":false},{"name":"@version","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"geoip.latitude","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"_source","type":"_source","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"geoip.longitude","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CC.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hr","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"hs","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"type","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ft.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hr.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"metrics.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"host","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"Tq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tr","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tt","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ac","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tw","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hs.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"rc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"metrics","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"fc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"bc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"B","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"tsc","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"s.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ft","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"bq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"sc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CS.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"t.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"sq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CC","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ST","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"b","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ci","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"host.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"b.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"tsc.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"type.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"message","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"cp","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ci.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CS","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"r.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"r","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"s","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"t","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"_id","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"_type","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"_score","type":"number","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false}]'
              }
            }, function (error, response, body) {}
          );
        } else otherwise();
      }
  });
}

function updateKibanaGateways() {
  api.gateways(function (gateways) {
      _.forEach(gateways, function (gateway) {

          // search
          http({
            url: elasticsearch + '/.kibana/search/' + gateway.lookup_name
            }, function (error, response, body) {
              if (response.statusCode == 404) {
                http({
                  url: elasticsearch + '/.kibana/search/' + gateway.lookup_name,
                  method: 'POST',
                  json: {
                      "title": "gateway: " + gateway.name,
                      "description": "",
                      "hits": 0,
                      "columns": [
                        "_source"
                      ],
                      "sort": [
                        "@timestamp",
                        "desc"
                      ],
                      "version": 1,
                      "kibanaSavedObjectMeta": {
                        "searchSourceJSON": '{"index":"${Logstash.index}","highlight":{"pre_tags":["@kibana-highlighted-field@"],"post_tags":["@/kibana-highlighted-field@"],"fields":{"*":{}},"require_field_match":false,"fragment_size":2147483647},"filter":[],"query":{"query_string":{"query":"type: "haproxy" AND ft: "' + gateway.lookup_name + '","analyze_wildcard":true}}}'
                      }
                    }
                  }, function (error, response, body) {}
                );
              }
            }
          );

          http({
            url: elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_tt'
            }, function (error, response, body) {
              // response time
              if (response.statusCode == 404) {
                http({
                  url: elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_tt',
                  method: 'POST',
                  json: {
                      "title": "total time: " + gateway.name,
                      "visState": '{"type":"histogram","params":{"shareYAxis":true,"addTooltip":true,"addLegend":true,"scale":"linear","mode":"stacked","times":[],"addTimeMarker":false,"defaultYExtents":false,"setYExtents":false,"yAxis":{}},"aggs":[{"id":"1","type":"avg","schema":"metric","params":{"field":"Tt"}},{"id":"2","type":"date_histogram","schema":"segment","params":{"field":"@timestamp","interval":"auto","customInterval":"2h","min_doc_count":1,"extended_bounds":{}}}],"listeners":{}}',
                      "description": "",
                      "savedSearchId": gateway.lookup_name,
                      "version": 1,
                      "kibanaSavedObjectMeta": {
                        "searchSourceJSON": '{"filter":[]}'
                       }
                    }
                  }, function (error, response, body) {}
                );
              }
            }
          );

          http({
            url: elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_count'
            }, function (error, response, body) {
              // response time
              if (response.statusCode == 404) {
                http({
                  url: elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_count',
                  method: 'POST',
                  json: {
                      "title": "request count: " + gateway.name,
                      "visState": '{"type":"histogram","params":{"shareYAxis":true,"addTooltip":true,"addLegend":true,"scale":"linear","mode":"stacked","times":[],"addTimeMarker":false,"defaultYExtents":false,"setYExtents":false,"yAxis":{}},"aggs":[{"id":"1","type":"count","schema":"metric","params":{}},{"id":"2","type":"date_histogram","schema":"segment","params":{"field":"@timestamp","interval":"auto","customInterval":"2h","min_doc_count":1,"extended_bounds":{}}}],"listeners":{}}',
                      "description": "",
                      "savedSearchId": gateway.lookup_name,
                      "version": 1,
                      "kibanaSavedObjectMeta": {
                        "searchSourceJSON": '{"filter":[]}'
                      }
                    }
                  }, function (error, response, body) {}
                );
              }
            }
          );
      });
  });
}

function run() {
  api.config(function (config) {

    elasticsearch = config['vamp.pulse.elasticsearch.url'];

    createKibanaIndex(function () {
      updateKibanaGateways();
    });
  });
}

run();

setInterval(run, interval * 1000);
