/**
 * An OntologyRecord is an instance of a 
 */

@Grapes([
  @Grab(group='commons-io', module='commons-io', version='2.4'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
])

import groovyx.net.http.HTTPBuilder
import java.sql.Timestamp
import java.lang.reflect.Modifier
import groovyx.net.http.ContentType
import org.apache.commons.io.FileUtils

class OntologyRecord {
  public final static String BASE_ONTOLOGY_DIRECTORY = 'onts/'
  public final static String API_KEY = '24e0413e-54e0-11e0-9d7b-005056aa3316' // TODO: Global config

  String id
  String name
  LinkedHashMap submissions
  long lastSubDate

  void addNewSubmission(data) {
    def http = new HTTPBuilder()
    def fileName = id+'_'+(submissions.size()+1)+'.ont'
    def oFile = new File(BASE_ONTOLOGY_DIRECTORY+fileName)

    http.get( uri: data.download, contentType: ContentType.BINARY, query: [ 'apikey': API_KEY ] ) { 
      resp, ontology ->
        FileUtils.copyInputStreamToFile(ontology, oFile)
    }

    lastSubDate = data.released
    submissions[data.released] = fileName
  }

  Map asMap() {
    this.class.declaredFields.findAll { !it.synthetic && !Modifier.isStatic(it.modifiers) }.collectEntries {
      [ (it.name):this."$it.name" ]
    }
  }
}
     
