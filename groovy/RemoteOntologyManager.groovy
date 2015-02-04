/**
 * The RemoteOntologyManager uses bioportal to keep a list of ontologies up to
 * date.
 */
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )

import groovyx.net.http.HTTPBuilder
import java.text.SimpleDateFormat

class RemoteOntologyManager {
  public final static String API_ROOT = 'http://data.bioontology.org/'
  public final static String API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316'
  def oBase

  RemoteOntologyManager() {
    oBase = new OntologyDatabase()
  }

  void updateOntologies() {
    // Get the list of ontologies.
    def http = new HTTPBuilder(API_ROOT)
    http.get( path: 'ontologies', query: [ 'apikey': API_KEY ] ) { resp, ontologies ->
      println '[' + resp.status + '] /ontologies'

      ontologies.each { ont ->
        // We'll use the ontology acronym as a key for now. Ideally we'd use
        //  the URI, but ideally not BioPortal's.
        OntologyRecord exOnt = oBase.getOntology(ont.acronym);
        
        if(!exOnt) { // Create a new ontology record
          exOnt = oBase.createOntology([
            'id': ont.acronym,
            'name': ont.name
          ]);
          println '[' + ont.acronym + '] Created'
        } else {
          println '[' + ont.acronym + '] Already exists'
        }

        // Check if there are any new entries
        def subsReq = new HTTPBuilder()
        subsReq.get( uri: ont.links.submissions, query: [ 'apikey': API_KEY ] ) { eResp, submissions ->
          println '[' + resp.status + '] ' + ont.links.submissions
          
          SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
          def lastSubDate = dateFormat.parse(submissions[0].released).toTimestamp().getTime() / 1000;
          
          if(lastSubDate > exOnt.lastSubDate) {
            exOnt.addNewSubmission([
              'released': lastSubDate,
              'download': ont.links.download
            ]) 
            oBase.saveOntology(exOnt)

            println '[' + ont.acronym + '] Adding new version'
          } else {
            println '[' + ont.acronym + '] Nothing new to report'
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
