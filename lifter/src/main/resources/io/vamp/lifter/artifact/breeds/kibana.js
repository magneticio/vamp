'use strict';

let _ = require('highland');
let http = require('request-promise');
let vamp = require('vamp-node-client');

let api = new vamp.Api();

let index = 'logstash-*';

let httpStatus = function (url) {
  return _(http(url).promise().catch(function (response) {
    return response.statusCode != 404;
  }));
};

let createKibanaIndex = function (elasticsearch) {
  return _(http(elasticsearch + '/.kibana/config/_search').promise().then(JSON.parse)).flatMap(function (response) {
    let hit;
    let kibana;

    for (let i = 0; i < response.hits.hits.length; i++) {
      hit = response.hits.hits[i];
      if (hit._index === '.kibana') {
        kibana = hit;
        break;
      }
    }

    if (kibana._source.defaultIndex) return _([true]);

    kibana._source['defaultIndex'] = index;

    return _(http({
      url: elasticsearch + '/' + hit._index + '/' + hit._type + '/' + hit._id,
      method: 'POST',
      json: hit._source
    }).promise()).flatMap(function () {
      return _(http({
        url: elasticsearch + '/.kibana/index-pattern/' + index,
        method: 'POST',
        json: {
          'title': index,
          'timeFieldName': '@timestamp',
          'fields': '[{"name":"_index","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"geoip.ip","type":"ip","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"@timestamp","type":"date","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"geoip.location","type":"geo_point","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":false},{"name":"@version","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"geoip.latitude","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"_source","type":"_source","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"geoip.longitude","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CC.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hr","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"hs","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"type","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ft.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hr.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"metrics.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"host","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"Tq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tr","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tt","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ac","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"Tw","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"hs.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"rc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"metrics","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"fc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"bc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"B","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"tsc","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"s.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ft","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"bq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"sc","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CS.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"t.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"sq","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CC","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ST","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"b","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"ci","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"host.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"b.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"tsc.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"type.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"message","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"cp","type":"number","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"ci.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"CS","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"r.raw","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":false,"doc_values":true},{"name":"r","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"s","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"t","type":"string","count":0,"scripted":false,"indexed":true,"analyzed":true,"doc_values":false},{"name":"_id","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"_type","type":"string","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false},{"name":"_score","type":"number","count":0,"scripted":false,"indexed":false,"analyzed":false,"doc_values":false}]'
        }
      }).promise());
    }).map(function () {
      return false;
    });
  });
};

let updateKibanaGateways = function (elasticsearch) {
  return api.gateways().flatMap(function (gateway) {
    return httpStatus(elasticsearch + '/.kibana/search/' + gateway.lookup_name).flatMap(function (exists) {
      return exists ? _([true]) : _(http({
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
        }
      ).promise());
    }).flatMap(function () {
      return httpStatus(elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_tt').flatMap(function (exists) {
        return exists ? _([true]) : _(http({
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
        }).promise());
      });
    }).flatMap(function () {
      return httpStatus(elasticsearch + '/.kibana/visualization/' + gateway.lookup_name + '_count').flatMap(function (exists) {
        return exists ? _([true]) : _(http({
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
        }).promise());
      });
    });
  });
};

api.config().flatMap(function (config) {
  let elasticsearch = config['vamp.pulse.elasticsearch.url'];
  return createKibanaIndex(elasticsearch).flatMap(function (exists) {
    return exists ? updateKibanaGateways(elasticsearch) : _([true]);
  });
}).each(function () {
});
