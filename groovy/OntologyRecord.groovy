/**
 * An OntologyRecord is an instance of a 
 */

 import java.sql.Timestamp

class OntologyRecord {
  String id
  String name
  LinkedHashMap submissions
  Timestamp lastSubDate

  Map asMap() {
    this.class.declaredFields.findAll { !it.synthetic }.collectEntries {
      [ (it.name):this."$it.name" ]
    }
  }
}
     
