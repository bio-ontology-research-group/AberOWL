var fs = require('fs'),
    _ = require('underscore')._;

file = JSON.parse(fs.readFileSync('ontologies.json', 'utf-8')),

_.each(file, function(elem, index) {
  if(!elem.description) {
    elem.description = ' ';
  }
  console.log('new aberowl.Ontology(acronym:"'+index+'",name:"'+elem.name+'",description:"""'+elem.description.replace(/\n/g,'')+'""",lastSubDate:'+elem.lastSubDate+').save()');
})
