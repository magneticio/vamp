
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
