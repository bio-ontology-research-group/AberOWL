/**
 * The OntologyDatabase handles a bunch of ontologies and their versions. 
 * 
 * For now it is a mock and doesn't really save anything. That's okay.
 *
 */
import groovy.json.*

class OntologyDatabase {
  public final static String INDEX_FILE = 'ontologies.json'
  def ontologies
  
  OntologyDatabase() { // Load the ontology index...
    def oInput = new File(INDEX_FILE)
    ontologies = new JsonSlurper().parseText(oInput.text)
  }

  /**
   * Return an ontology ID by name.
   *
   * @param id The id of the record to return.
   * @return The ontology, or null.
   */
  OntologyRecord getOntology(String id) {
    if(ontologies[id]) {
      return new OntologyRecord(ontologies[id])
    } else {
      return null
    }
  }

  /**
   * Create a new ontology record, and add it to the 
   *
   * @param data An object with data about the ontology.
   */
  OntologyRecord createOntology(data) {
    data.lastSubDate = new Date().toTimestamp();
    def oRecord = new OntologyRecord(data)
    ontologies[data.id] = oRecord.asMap()

    flushDatabase()
    return oRecord
  }

  /**
   * Save an ontology to the database. Will overwrite any other OntologyRecord
   *  of the same id.
   * 
   * @param record The record to save.
   */
  void saveOntology(OntologyRecord record) {
    ontologies[record.id] = record
    flushDatabase()
  }

  private void flushDatabase() {
    def oOutput = new File(INDEX_FILE)
    oOutput.write(new JsonBuilder(ontologies).toPrettyString())
  }

  public static void main(args) {
    OntologyDatabase d = new OntologyDatabase()
    d.createOntology(['id': 'SQUIG', 'name': 'Squirrels'])
    d.ontologies.each{ k, v -> println "${k}:${v}" }
  }
}
