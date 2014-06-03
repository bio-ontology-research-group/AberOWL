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
    if (type == 'values') {
      ta.value = "SELECT * WHERE { \n" +
    "# binds ?ontid to the results of the OWL query \n" +
    "VALUES ?ontid { \n" +
    "OWL <?php echo $type; ?> <http://jagannath.pdn.cam.ac.uk/aber-owl/service/> <<?php echo $ontology; ?>> { <?php echo $query; ?> }\n" +
   "} . \n\n" +
  "# continue query here; ?ontid is bound to the set of class IRIs of the OWL query \n\n"+
	"}\n";

    } else {
      ta.value = "SELECT * WHERE {\n" +
  " # enter query here \n\n" +
  " # filter on ?x \n" +
  " FILTER ( ?x IN ( OWL <?php echo $type; ?> <http://jagannath.pdn.cam.ac.uk/aber-owl/service/> <<?php echo $ontology; ?>> { <?php echo $query; ?> } ) ) . \n" +
      " }\n";

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
        <textarea id="ta" name="sparqlquery" cols="150" rows="5">
</textarea>
<a href="#" onclick="javascript:change('values');">Values form</a>
<a href="#" onclick="javascript:change('filter');">Filter form</a>

        <br />

        SPARQL Endpoint URI: <input type="text" name="endpoint" /><br />

        <input type="submit" />
    </form>
</body>

</html>
