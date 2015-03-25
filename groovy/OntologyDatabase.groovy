/**
 * The OntologyDatabase handles a bunch of ontologies and their versions. 
 * 
 * For now it is a mock and doesn't really save anything. That's okay.
 *
 */
import groovy.json.*
import redis.clients.jedis.*

class OntologyDatabase {
  public final static String DB_PREFIX = 'ontologies:'
  def db
  
  OntologyDatabase() {
    db = new Jedis('localhost');
  }

  /**
   * Return an ontology ID by name.
   *
   * @param id The id of the record to return.
   * @return The ontology, or null.
   */
  OntologyRecord getOntology(String id, boolean noprefix) {
    if(noprefix != true) {
      id = DB_PREFIX + id 
    }
    def item = db.get(id)
    if(item) {
      return new OntologyRecord(new JsonSlurper().parseText(item))
    } else {
      return null
    }
  }

  Set<String> allOntologies() {
    def onts = []
    db.keys('ontologies:*').each { id ->
      onts.add(getOntology(id, true))
    }
    return onts
  }

  /**
   * Create a new ontology record, and add it to the 
   *
   * @param data An object with data about the ontology.
   */
  OntologyRecord createOntology(data) {
    // Not really the right place for this
    data.lastSubDate = 0
    data.submissions = new LinkedHashMap()

    def oRecord = new OntologyRecord(data)
    db.set(DB_PREFIX + data.id, new JsonBuilder(oRecord.asMap()).toString())

    return oRecord
  }

  /**
   * Save an ontology to the database. Will overwrite any other OntologyRecord
   *  of the same id.
   * 
   * @param record The record to save.
   */
  void saveOntology(OntologyRecord record) {
    db.set(DB_PREFIX + record.id, new JsonBuilder(record.asMap()).toString())
  }
}
