/**
 * The RemoteOntologyManager uses bioportal to keep a list of ontologies up to
 * date.
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )

import groovyx.net.http.HTTPBuilder

class RemoteOntologyManager {
  public final static String API_ROOT = 'http://data.bioontology.org/'
  public final static String API_KEY = ''
  def http
  def oDatabase

  RemoteOntologyManager() {
    http = new HTTPBuilder(API_ROOT)
    oBase = new OntologyDatabase()
  }

  void updateOntologies() {
    // Get the list of ontologies.
    http.get( path: 'ontologies', query: [ 'apikey': API_KEY ] ) { resp, ontologies ->
      println '[' + resp.status + '] /ontologies'

      ontologies.each { ont ->
        // We'll use the ontology acronym as a key for now. Ideally we'd use
        //  the URI, but ideally not BioPortal's.
        ont = ont.ontology
        OntologyRecord exOnt = oBase.getOntology(ont.acronym);
        
        if(!exont) { // Create a new ontology record
          exOnt = oBase.createOntology([
            'id': ont.acronym,
            'name': ont.name
          ]);
          println '[' + ont.acronym + '] Created'
        }

        // Check if there are any new entries
        http.get( path: ont.links.submissions, query: [ 'apikey': API_KEY ] ) { resp, submissions ->
          println '[' + resp.status + '] ' + ont.links.submissions
          
          def lastSubDate = submissions[0].released.toUnix(); // Probably need a library here or whatever
          if(lastSubDate > exOnt.lastSubDate) { // Save the new submission!
            exOnt.addNewSubmission([
              'released': lastSubDate,
              'download': ont.links.download
            ]) 
            oBase.saveOntology(exOnt)
          }
        }
      }
    }
  }

  public static void main(args) {
    RemoteOntologyManager r = new RemoteOntologyManager()
    r.updateOntologies();
  }

}
