package aberowl

class Ontology {
    String id
    String name
    long int lastSubDate
    HashMap submissions

    static mapWith = 'redis'

    static constraints = {
    }
}
