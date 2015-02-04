<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"> 
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Aber-OWL: SPARQL</title>
	<link href="../css/smoothness/jquery-ui-1.10.4.custom.css" rel="stylesheet">
	<link href="../css/dataTables.jqueryui.css" rel="stylesheet">
        <script src="../js/jquery-1.10.2.js"></script>  
        <script src="../js/jquery-ui-1.10.4.custom.js"></script>  
        <script src="../js/jquery.autosize.js"></script>  
	<script src="../js/jquery.dataTables.js"></script>
	<style>
	table.display {
  	  	margin: 0 auto;
  		width: 100%;
  		clear: both;
  		border-collapse: collapse;
		table-layout: fixed;         // add this 
		word-wrap:break-word;        // add this 
	}
	table{
		font: 80% "Trebuchet MS", sans-serif;
		margin: 80px;
	}
	body{
		font: 100% "Trebuchet MS", sans-serif;
		margin: 80px;
	}
	.menubar {
	  position: fixed;
	  top: 0;
	  left: 0;
	  z-index: 999;
	  width: 95%;
	}
	.menubarleft {
	  position: fixed;
	  top: 0;
	  left: 20px;
	  z-index: 999;
	  width: 95%;
	  display: none;
	}
	.demoHeaders {
		margin-top: 2em;
	}
	#icons {
		margin: 0;
		padding: 0;
	}
	.display thead {
	}
	#icons li {
		margin: 2px;
		position: relative;
		padding: 4px 0;
		cursor: pointer;
		float: left;
		list-style: none;
	}
	#icons span.ui-icon {
		float: left;
		margin: 0 4px;
	}
	.fakewindowcontain .ui-widget-overlay {
		position: absolute;
	}
	.title {
		text-align: center;
		margin: 0px;
	}
	</style>
<script language="javascript">
   $(function() {
       $( "#accordion" ).accordion({
	 collapsible: true,
	     active: 1
	     });
     });

$(document).ready(function() { 
    //    $('#ta').autosize() ;
    $('#example').dataTable( {
        "processing": false,
        "serverSide": false,
	"paging": true,
	"scrollY": 400,
	"renderer": "bootstrap",
	"bAutoWidth": false,
	"iDisplayLength": 50,
	"bJQueryUI": true
	  });
} );
  </script>

</head>
<body>
	<p class="menubar" align="right"><small><a href="../help.html">Help</a></small></p>
	<h1 class="title" title="Framework for ontology-based data access">Aber-OWL: SPARQL</h1>


<?php

require_once('sparqllib.php');

if(php_sapi_name() === 'cli') {
    // do command line tings
    $sparqlquery = implode(' ', file('query.sparql'));
} else {
    $sparqlquery = $_POST['sparqlquery'];
    $sparqlendpoint = $_POST['endpoint'];
    $short = $_POST['short'];
    $filter = $_POST['filter'];
    $qt = $_POST['qtype'];
}

$globalprefix = array();

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
    print "<b>OWL query failed. Please verify the URI of the Aber-OWL endpoint.</b>";
    exit;
  }
  $values = parse_owl($result, $owl[1], $short, $qt);
  // Replace OWL block with classes
  $sparqlquery = str_replace($owl[0], $values, $sparqlquery);
  $globalprefix = array_unique($globalprefix);
  foreach($globalprefix as $key => $prefix) {
    if (preg_match_all("/PREFIX\s+".preg_quote($key)."/", $sparqlquery) === 0) {
      $sparqlquery = "PREFIX $key <$prefix>\n".$sparqlquery;
    }
  }
}
?>
<br /><br />
<div id="accordion">
  <h3>View/modify SPARQL Query</h3>
  <div>
  <form name="sparqowl" action="sparqowl.php" method="POST">
        <textarea id="ta" name="sparqlquery" cols="150" rows="20">
<?php
  print $sparqlquery;
print "</textarea>\n";
print "<input type=\"hidden\" name=\"qtype\" value=\"$qt\" />";
print "<p>SPARQL Endpoint URI: <input type=\"text\" size=100% name=\"endpoint\" value=\"".$sparqlendpoint."\"/></p>";
?>
  <p><input type="submit" /></p>
  </div>
    </form>
  <h3>Results</h3>
  <div>
  
<?php

// Run the SPARQL query

$db = sparql_connect($sparqlendpoint);
$result = sparql_query(htmlspecialchars_decode($sparqlquery));
if(!$result) {
  print "<p>Error: " . sparql_error() . '('.sparql_errno().')</p>';
  print "</div></div></body></html>";
    exit;
}

// Print results
?>
<table id="example" class="display" cellspacing="0"
  width="100%">
  <thead id="headers">
  <tr>
<?php
  $fields = sparql_field_array($result);
  foreach($fields as $field) {
    echo "<th>$field</th>\n";
  }
  echo "</tr>\n</thead>\n";

while($row = sparql_fetch_array($result)) {
  echo "<tr>\n";
    foreach($fields as $field) {
      echo "<td>".$row[$field]."</td>\n";
    }
  echo "</tr>\n";
}
?>
  </table>
<?php

// Perform a remote OWL query
function owl_query($endpoint, $onturi, $query, $type) {
    $url = $endpoint . '?query=' . urlencode($query) . 
        '&type=' . strtolower($type) .
      '&ontology=' . $onturi;
    //print $url;
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
function parse_owl($owl, $idname, $short, $qt) {
  $data = json_decode($owl, true);
  $values = "" ;
  $resarray = array();
  foreach($data as $object) {
    if($object['owlClass']['iri']['remainder'] != 'Thing' && $object['owlClass']['iri']['remainder'] != 'Nothing') {
      $uri = $object['classURI'];
      $prefix = $object['owlClass']['iri']['prefix'];
      $local = substr($object['owlClass']['iri']['remainder'], 0, strpos($object['owlClass']['iri']['remainder'], "_")+1);
      $spref = str_replace("_",":",$local);
      $GLOBALS['globalprefix'][$spref] = $prefix.$local;
      $id = str_replace("_", ":", $object['owlClass']['iri']['remainder']);
      if ($short == true) {
	$resarray[] = $id ;
	//	$values .= ' ' . $id . ' ';
      } else {
	$resarray[] = '<'.$uri.'>';
	//	$values .= ' <' . $uri . '> ';
      }
    }
  }
  if (strcmp($qt, "values") == 0) {
    $values = implode(" ", $resarray) ;
  } else {
    $values = implode(", ", $resarray) ;
  }

  return $values;
}

?>
  </div>
</div>
</body>
