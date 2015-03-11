import groovy.json.*
import org.json.simple.JSONValue;

def rManager = application.rManager
response.contentType = 'application/json'
def keys = rManager.queryEngines.keySet()
print new JsonBuilder(keys).toString()
