@Grab('com.github.albaker:GroovySparql:0.7.2')
import groovy.sparql.*

class QuerySparql {

  public static Set query(String q) {
    def sparql = new Sparql(endpoint:"http://sparql.bioontology.org/ontologies/sparql/?apikey=24e0413e-54e0-11e0-9d7b-005056aa3316")
    def query = q
    println "Running SPARQL query: $query"
    Set s = new LinkedHashSet()
    sparql.eachRow(query) { row ->
      s.add(row)
    }
    return s
  }
}
