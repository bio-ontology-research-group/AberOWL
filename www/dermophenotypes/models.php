<!doctype html>
<html lang="us">
<head>
	<meta charset="utf-8">
	<title>Semantically mined Dermatological Disease Phenotypes</title>
	<link href="../css/smoothness/jquery-ui-1.10.4.custom.css" rel="stylesheet">
	<link href="../css/dataTables.jqueryui.css" rel="stylesheet">
	<script src="../js/jquery-1.10.2.js"></script>
	<script src="../js/jquery-ui-1.10.4.custom.js"></script>
	<script src="../js/jquery.dataTables.js"></script>
	<script src="../js/dataTables.jqueryui.js"></script>
    <script>
    var diseasename = "";

function htmlEncode(value){
  //create a in-memory div, set it's inner text(which jQuery automatically encodes)
  //then grab the encoded contents back out.  The div never exists on the page.
  return $('<div/>').text(value).html();
}
   
    function redrawTable(id) {
      //	$('#example').dataTable().fnDestroy();
	$('#example').dataTable( {
            "processing": false,
            "serverSide": false,
	    "paging": true,
	    "scrollY": 400,
	    "renderer": "bootstrap",
	    "aaSorting": [[ 1, "desc" ]],
	    "bAutoWidth": false,
	    "iDisplayLength": 100,
	    "bJQueryUI": true,
	    aoColumns : [
		{ "sWidth": "60%"},
		{ "sWidth": "20%"},
	    ],
            "ajax": {
		"url": "../dermo/?type=sim&q="+id,
		"dataSrc": function ( json ) {
                    var datatable = new Array() ;
                    for ( var i=0, ien=json.length ; i<ien ; i++ ) {
			datatable[i] = new Array() ;
			datatable[i][0] = "<a href=http://www.informatics.jax.org/searchtool/Search.do?query="+json[i].mgi+">"+htmlEncode(json[i].name)+" (<tt>"+json[i].mgi+"</tt>)</a>";
			datatable[i][1] = json[i].val;
                    }
                    return datatable;
		
		}
            }
	} );
    }
</script>
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
<body onload=<?php
echo 'redrawTable("'.$_GET["q"].'");';
?>   
>
	<h1 class="title" title="Mouse models">Mouse models similar to <?php echo $_GET['name']." (<tt>".$_GET['q']."</tt>)"; ?></h1>

  <br/><br/>

  <!-- Autocomplete -->

<p>
  <div id="results">
    <h2 class="demoHeaders"></h2>

    <table id="example" cellspacing="0" width="100%">
    <col style="width: 60%"/>
        <col style="width: 20%"/>
      <thead id="headers">
	<tr>
          <th>Model</th>
          <th>Similarity</th>
	</tr>
      </thead>
    </table>
  </div>

</p>

</body>
</html>
