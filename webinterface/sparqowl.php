<?php

require_once('sparqllib.php');

if(php_sapi_name() === 'cli') {
    // do command line tings
    $sparqlquery = implode(' ', file('query.sparql'));
} else {
    $sparqlquery = $_POST['sparqlquery'];
    $sparqlendpoint = $_POST['endpoint'];
}

// Load the query...

//$sparqlquery = str_replace(array("\r\n", "\r", "\n", "\t", "  "), ' ', $sparqlquery);

// Find and resolve OWL blocks
// TODO: Resolve prefixes

/**
 * Match OWL ?id TYPEOFQUERY { Manchester Owl Syntax Query }
 *  0: Entire match
 *  1: Type of query (SUBCLASS etc)
 *  2: Endpoint URI
 *  3: Ontology URI
 *  4: Manchester OWL Syntax query
 */
//preg_match_all("/OWL\s(\?\w+)?\s?(\w+)?\s*<(.+)>\s*{\s*(.+?)\s*?}/", $query, $owls, PREG_SET_ORDER);
preg_match_all("/OWL\s+(\w+)\s*<(.+)>\s*<(.*)>\s*{\s*(.+?)\s*}/", $sparqlquery, $owls, PREG_SET_ORDER);
foreach($owls as $owl) {
  $type = strtolower($owl[1]) ;
  $endpoint = $owl[2] ;
  $onturi = $owl[3] ;
  $query = $owl[4] ;

  $result = owl_query($endpoint, $onturi, $query, $type) ;
  if(!$result) {
    print "OWL query failed. World ending.";
    exit;
  }
  $values = parse_owl($result, $owl[1]);

  // Replace OWL block with classes
  $sparqlquery = str_replace($owl[0], $values, $sparqlquery);
}

print 'Resolved Query: ' . $sparqlquery . '<br /><br />';

// Run the SPARQL query

$db = sparql_connect($sparqlendpoint);
$result = sparql_query(htmlspecialchars_decode($sparqlquery));
if(!$result) {
    exit;
}

// Print results

$fields = sparql_field_array($result);
while($row = sparql_fetch_array($result)) {
    foreach($fields as $field) {
        print '[' . $field . ']: ' . $row[$field] . '<br />';
    }
    print '<br />';
}

// Perform a remote OWL query
function owl_query($endpoint, $onturi, $query, $type) {
    $url = $endpoint . '?query=' . urlencode($query) . 
        '&type=' . strtolower($type) .
      '&ontology=' . $onturi;
    $request = curl_init($url);
    curl_setopt($request, CURLOPT_RETURNTRANSFER, 1);
    curl_setopt($request, CURLOPT_HTTPHEADER, array(
        'Accept: application/json'
    ));

    $result = curl_exec($request);
    $info = curl_getinfo($request);

    // TODO: Reasonable error handling
    if($result === '' || $info['http_code'] != 200) {
        return;
    }
    
    curl_close($request);

    return $result;
}

// OWL ?id
function parse_owl($owl, $idname) {
    $data = json_decode($owl, true);
    $values = "" ;
    foreach($data as $object) {
        if($object['owlClass']['iri']['remainder'] != 'Thing' && $object['owlClass']['iri']['remainder'] != 'Nothing') {
            if(isset($_POST['shortforms'])) {
                $uri = ' ' . str_replace('_', ':', $object['owlClass']['iri']['remainder']) . ' ';
            } else {
                $uri = ' &lt;' . $object['classURI'] . '&gt; ';
            }
            $values .= $uri;
        }
    }
    return $values;
}
