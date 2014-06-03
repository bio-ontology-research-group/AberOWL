<?php

require_once('sparqllib.php');

if(php_sapi_name() === 'cli') {
    // do command line tings
    $query = implode(' ', file('query.sparql'));
} else {
    $query = $_POST['query'];
    $endpoint = $_POST['endpoint'];
}

// Load the query...

$query = str_replace(array("\r\n", "\r", "\n", "\t", "  "), ' ', $query);

// Find and resolve OWL blocks
// TODO: Resolve prefixes

/**
 * Match OWL ?id TYPEOFQUERY { Manchester Owl Syntax Query }
 *  0: Entire match
 *  1: Variable name
 *  2: Type of query (SUBCLASS etc)
 *  3: OWL endpoint URI
 *  4: Manchester OWL Syntax query
 */
preg_match_all("/OWL\s(\?\w+)?\s?(\w+)?\s*<(.+)>\s*{\s*(.+?)\s*?}/", $query, $owls, PREG_SET_ORDER);
foreach($owls as $owl) {
    if(!$owl[1]) $owl[1] = '?id';
    if(!$owl[2]) $owl[2] = ALL;
    $result = owl_query($owl[3], $owl[4], $owl[2]);
    if(!$result) {
        print "OWL query failed. World ending.";
        exit;
    }
    $values = parse_owl($result, $owl[1]);

    // Replace OWL block with classes
    $query = str_replace($owl[0], $values, $query);
}

print 'Resolved Query: ' . $query . '<br /><br />';

// Run the SPARQL query

$db = sparql_connect($endpoint);
$result = sparql_query(htmlspecialchars_decode($query));
if(!$result) {
    print sparql_errno() . ': ' . sparql_error() . '<br />';
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
function owl_query($endpoint, $query, $type) {
    $url = $endpoint . '?query=' . urlencode($query) . 
        '&type=' . strtolower($type);
    print $url;
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

// Turn the OWL query result into a VALUES array
// OWL ?id
function parse_owl($owl, $idname) {
    $data = json_decode($owl, true);
    $values = 'VALUES ' . $idname . ' { ';
    foreach($data as $object) {
        if($object['iri']['remainder'] != 'Thing' && $object['iri']['remainder'] != 'Nothing') {
		$uri = $object['iri']['prefix'] . $object['iri']['remainder'];
		$values .= ' &lt;' . $uri . '&gt; ';
        }
    }
    $values .= '} .';

    return $values;
}
