
console.log("VAMP_KEY_VALUE_STORE_TYPE: " + process.env.VAMP_KEY_VALUE_STORE_TYPE);
console.log("VAMP_KEY_VALUE_STORE_CONNECTION: " + process.env.VAMP_KEY_VALUE_STORE_CONNECTION);
console.log("VAMP_KEY_VALUE_STORE_ROOT_PATH: " + process.env.VAMP_KEY_VALUE_STORE_ROOT_PATH);
console.log("VAMP_WORKFLOW_DIRECTORY: " + process.env.VAMP_WORKFLOW_DIRECTORY);
console.log("VAMP_ELASTICSEARCH_CONNECTION: " + process.env.VAMP_ELASTICSEARCH_CONNECTION);

var connection = process.env.VAMP_KEY_VALUE_STORE_CONNECTION;
var colon = connection.indexOf(':');

var options = {};
options["host"] = connection.substring(0, colon);
options["port"] = connection.substring(colon + 1);

var consul = require('consul')(options);

consul.kv.get('vamp/scheduled-workflows/UUID/workflow', function(err, result) {
  if (err) throw err;
  console.log(result);
});

var elasticsearch = require('elasticsearch');

var client = new elasticsearch.Client({
  host: process.env.VAMP_ELASTICSEARCH_CONNECTION,
  log: 'info'
});

client.info({}, function (error, response, status) {
  if (error) {
    console.log(err);
  } else {
    console.log(response);
  }
});