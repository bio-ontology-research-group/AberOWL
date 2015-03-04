package aberowl

class Ontology {
    static hasMany = [submissions: OntologySubmission] 

    String acronym
    String name
    String description
    long lastSubDate

    static constraints = {
    }

    static mapping = {
      acronym index:true
      name index:true
    }

    String toString() {
      return acronym
    }
}
