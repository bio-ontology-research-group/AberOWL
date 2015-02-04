<!doctype html>
<html lang="us">
<head>
	<meta charset="utf-8">
	<title>ROBBI-Pubmed: ontology-based access to Pubmed</title>
	<link href="css/smoothness/jquery-ui-1.10.4.custom.css" rel="stylesheet">
	<link href="css/dataTables.jqueryui.css" rel="stylesheet">
	<script src="js/jquery-1.10.2.js"></script>
	<script src="js/jquery-ui-1.10.4.custom.js"></script>
	<script src="js/jquery.dataTables.js"></script>
	<script src="js/dataTables.jqueryui.js"></script>
	<script>

$(function() {
		
	
		
            $( "#button" ).button();
	    $( "#radioset" ).buttonset();
		
	});


	</script>
	<style>
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
	.demoHeaders {
		margin-top: 2em;
	}
	#icons {
		margin: 0;
		padding: 0;
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
</head>
<body>
	<p class="menubar" align="right"><small><a href="help.html">Help</a></small></p>
	<h1 class="title" title="Ontology-based access to Pubmed">ROBBI-Pubmed</h1>

  <br/><br/>

<p>
  <div id="results">
   <ul>

   </ul>
  </div>

</p>

</body>
</html>
