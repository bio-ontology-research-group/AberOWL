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
        #radio {
            padding: 4px;
            display: inline-block;
        }
	.title {
		text-align: center;
		margin: 0px;
	}
	</style>

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
	"  # ?ontid is bound to the set of class IRIs of the OWL query \n\n"+
	"  # enter query here\n" +
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
    $( "#radio" ).buttonset();
} );
  </script>
</head>

<body>
  	<p class="menubar" align="right"><small><a href="help.html">Help</a></small></p>
	<h1 class="title" title="Framework for ontology-based data access">Aber-OWL: SPARQL</h1>
  <br /><br />
    <form name="sparqowl" action="sparqowl.php" method="POST">
        <textarea id="ta" name="sparqlquery" cols="100" rows="5">
</textarea>
<p>
<div id="radio">
    <input onchange="javascript:change('values');" type="radio" id="radio1" name="radio" value="values" checked="checked"><label for="radio1">Use in VALUES statement</label>
  <input onchange="javascript:change('filter');" type="radio" id="radio2" name="radio" value="filter" style="float:right;"><label for="radio2">Use in FILTER statement</label>
</div>
</p>

<p>SPARQL Endpoint URI: <input type="text" size=100% name="endpoint" /></p>
  <p>Use OBO-style URIs: <input type="checkbox" name="short" /></p>
  <input type="hidden" id="hiddenfield" name="qtype" value="values" /></p>
  <p><input type="submit" /></p>
    </form>
</body>

</html>
