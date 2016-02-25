
var connection = process.env.VAMP_KEY_VALUE_STORE_CONNECTION;
var colon = connection.indexOf(':');

var options = {};
options["host"] = connection.substring(0, colon);
options["port"] = connection.substring(colon + 1);

var consul = require('consul')(options);

// read and log this workflow script
consul.kv.get(process.env.VAMP_KEY_VALUE_STORE_ROOT_PATH + '/workflow', function(err, result) {
  if (err) throw err;
  console.log(result);
});
