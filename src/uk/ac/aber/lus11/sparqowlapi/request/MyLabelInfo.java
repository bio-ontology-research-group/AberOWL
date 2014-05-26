package uk.ac.aber.lus11.sparqowlapi.request;


import java.io.*;

public class MyLabelInfo implements Serializable {
    String label ;
    String id ;
    String uri ;
    public boolean equals(Object o) {
	if (o == null) {
	    return false ;
	}
	if (! (o instanceof MyLabelInfo)) {
	    return false ;
	}
	MyLabelInfo i = (MyLabelInfo) o ;
	return this.label.equals(i.label) ;
    }
}