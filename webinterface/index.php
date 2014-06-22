<?php
$query = $_GET["query"];
$ontology = $_GET["ontology"];
$type = $_GET["type"];
if ($type == null) { 
  $type = "subclass" ;
}

// OWL TYPE <endpoint> <ontURI> { query }

?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"> 
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
    <title>SPARQOWL Query Builder</title>
        <script src="js/jquery-1.10.2.js"></script>  
        <script src="js/jquery.autosize.js"></script>  
<script language="javascript">
  function change( type ) {
    var ta = document.getElementById("ta");
    var hf = document.getElementById("hiddenfield");
    if (type == 'values') {
      hf.value = "values";
      ta.value = "SELECT ?s ?p ?o WHERE { \n" +
	"  ##############################################\n" +
	"  # binds ?ontid to the results of the OWL query \n" +
	"  VALUES ?ontid { \n" +
	"    OWL <?php echo $type; ?> <http://jagannath.pdn.cam.ac.uk/aber-owl/service/> <<?php echo $ontology; ?>>\n" +
	"      { <?php echo $query; ?> }\n" +
	"  } . \n" +
	"  ##############################################\n" +
	"  # continue query here; ?ontid is bound to the set of class IRIs of the OWL query \n\n"+
	"}\n";

    } else {
      hf.value = "filter";
      ta.value = "SELECT * WHERE {\n" +
      "  ##############################################\n" +
      "  # enter query here \n\n" +
      "  ##############################################\n" +
      "  # filter on ?x \n" +
      "  FILTER ( \n" +
      "    ?x IN ( OWL <?php echo $type; ?> <http://jagannath.pdn.cam.ac.uk/aber-owl/service/> <<?php echo $ontology; ?>>\n" +
      "      { <?php echo $query; ?> }\n" +
      "    )\n" +
      "  ) . \n" +
      "}\n";

    }
  }
$(document).ready(function() { 
    change('values');
    $('#ta').autosize() ;
} );
  </script>
</head>

<body>
    <form name="sparqowl" action="sparqowl.php" method="POST">
        <textarea id="ta" name="sparqlquery" cols="100" rows="5">
</textarea>
<p>
<a href="#" onclick="javascript:change('values');">Values form</a>
<a href="#" onclick="javascript:change('filter');">Filter form</a>
</p>

<p>SPARQL Endpoint URI: <input type="text" size=100% name="endpoint" /></p>
  <p>Use OBO-style URIs: <input type="checkbox" name="short" /></p>
  <input type="hidden" id="hiddenfield" name="qtype" value="values" /></p>
  <p><input type="submit" /></p>
    </form>
</body>

</html>
