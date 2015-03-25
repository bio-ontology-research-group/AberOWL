var fs = require('fs'),
    _ = require('underscore')._;

var databank = require('databank').Databank;

// connect to the database
var params = {
    'schema': {},
    'port': 6379
};
var db = databank.get('redis', params);
db.connect({}, function(err) {
    if(err) {
        throw new Error('Could not connect to database');
    }
}.bind(this));

var file = JSON.parse(fs.readFileSync('ontologies.json', 'utf-8'));

_.each(file, function(elem, index) {
  if(!elem.description) {
    elem.description = ' ';
  }

  db.save('ontologies', index, elem, function() {
    console.log('done');
  });
})
