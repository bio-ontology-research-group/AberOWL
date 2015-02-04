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


    function redrawTable(id) {
	$('thead').css({
		display: "table-header-group"
	});
	$('#example').dataTable().fnDestroy();
	$('#example').dataTable( {
            "processing": false,
            "serverSide": false,
	    "paging": true,
	    "scrollY": 400,
	    "renderer": "bootstrap",
	    "aaSorting": [[ 2, "desc" ]],
	    "bAutoWidth": false,
	    "iDisplayLength": 100,
	    "bJQueryUI": true,
	    aoColumns : [
		{ "sWidth": "40%"},
		{ "sWidth": "10%"},
		{ "sWidth": "10%"},
		{ "sWidth": "10%"},
		{ "sWidth": "10%"},
		{ "sWidth": "10%"},
		{ "sWidth": "10%"},
	    ],
            "ajax": {
		"url": "../dermo/?type=phenotype&q="+id,
		"dataSrc": function ( json ) {
                    var datatable = new Array() ;
                    for ( var i=0, ien=json.length ; i<ien ; i++ ) {
			datatable[i] = new Array() ;
			datatable[i][0] = json[i].mpname;
			datatable[i][1] = json[i].mp;
			datatable[i][2] = json[i].pmi ;
			datatable[i][3] = json[i].tscore ;
			datatable[i][4] = json[i].zscore ;
			datatable[i][5] = json[i].lmi ;
			var mpname = json[i].mpname.trim();
			if (mpname.indexOf(' ')>0) {
				mpname = "'"+mpname+"'";
			}
			datatable[i][6] = "<a href=\"http://aber-owl.net/aber-owl/pubmed/?type=subeq&ontology=&owlquery="+encodeURI(mpname)+"&owlquery="+encodeURI(diseasename)+"\">Search</a>" ;
                    }
                    return datatable;
		}
            }
	} );
    }
$(function() {

    $( "#autocomplete" )
	.bind( "keydown", function( event ) {
	    if ( event.keyCode === $.ui.keyCode.TAB &&
            	 $( this ).data( "ui-autocomplete" ).menu.active ) {
          	event.preventDefault();
            }
      	})
	.autocomplete({
	    minLength: 1,
	    source: function( request, response ) {
		$.getJSON( "../dermo/", {
		    type: "names",
		    q: request.term,
		}, response );
	    },
	    select: function( event, ui ) {
		diseasename = ui.item.label.trim();
		$("#mousemodels").attr('href', 'models.php?q='+ui.item.id+'&name='+diseasename);
		if (diseasename.indexOf(' ') >= 0) {
			diseasename = "'" + diseasename + "'";
		}
		$(".demoHeaders").replaceWith("<h2 class=\"demoHeaders\">Phenotypes for "+diseasename+"</h2>"+
"<ul>"+
					      //"<li><a href=\"#tabs-main\">Phenotypes</a></li>"+
"<li><a target=\"_BLANK\" id=\"mousemodels\" href=\"http://aber-owl.net/aber-owl/dermophenotypes/models.php?q="+ui.item.id+"&name="+diseasename+"\">Mouse models</a></li>"+
					      //"<li><a target=\"_BLANK\" id=\"network\" href=\"\">Network</a></li>"+
		"</ul>"+
"");

		// (<small><a target=_blank href=\"http://aber-owl.net/aber-owl/dermophenotypes/models.php?q="+ui.item.id+"&name="+diseasename+"\">Similar mouse models</a>)</small></h2>") ;

		$("#autocomplete").val(ui.item.label);
		redrawTable(ui.item.id);
		return false;
            }
	})
	.data( "ui-autocomplete" )._renderItem = function( ul, item ) {
	    return $( "<li>" )
		.append( "<a>" + item.label +" (<tt>"+item.id+"</tt>)</a>" )
		.appendTo( ul );
	};
});
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
	thead {
		display: none;
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

	<h1 class="title" title="Semantically mined disease
	phenotypes">Dermatological Disease Phenotypes</h1>

  <br/><br/>

  <!-- Autocomplete -->

    <center>    <input size=100% onclick="this.value='';"
    id="autocomplete" title="Disease name" value="Enter disease name..."> </center>

<div id="tabs">

<p>
  <div id="results">
    <h2 class="demoHeaders"></h2>
    <table id="example" cellspacing="0" width="100%">
    <col style="width: 40%"/>
        <col style="width: 10%"/>
        <col style="width: 10%"/>
        <col style="width: 10%"/>
        <col style="width: 10%"/>
        <col style="width: 10%"/>
        <col style="width: 10%"/>
      <thead id="headers">
	<tr>
          <th>Phenotype</th>
          <th>ID</th>
          <th>Pointwise Mutual Information</th>
          <th>T-Score</th>
          <th>Z-Score</th>
          <th>Lexicographer's Mutual Information</th>
          <th>Find in Pubmed</th>
	</tr>
      </thead>
    </table>
  </div>
</p>

</body>
</html>
