@Grapes([
          @Grab(group='javax.servlet', module='javax.servlet-api', version='3.1.0'),
          @Grab(group='org.eclipse.jetty', module='jetty-server', version='9.3.0.M2'),
          @Grab(group='org.eclipse.jetty', module='jetty-servlet', version='9.3.0.M2'),
          @Grab(group='redis.clients', module='jedis', version='2.5.2'),
          @Grab(group='com.google.code.gson', module='gson', version='2.3.1'),
          @Grab(group='org.apache.lucene', module='lucene-queryparser', version='5.2.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),

          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.1.0-RC2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.1.0-RC2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.1.0-RC2'),

          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
          @Grab(group='org.apache.lucene', module='lucene-core', version='5.2.1'),
          @Grab(group='org.apache.lucene', module='lucene-analyzers-common', version='5.2.1'),
          @Grab(group='aopalliance', module='aopalliance', version='1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
@Grab(group='javax.el', module='javax.el-api', version='3.0.0')
 
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.reasoner.structural.StructuralReasoner
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.owllink.*;
import org.semanticweb.owlapi.util.*;
import org.semanticweb.owlapi.search.*;

import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.search.similarities.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.*
import org.apache.lucene.queryparser.simple.*
import org.apache.lucene.search.highlight.*
import org.apache.lucene.index.IndexWriterConfig.OpenMode

import java.util.concurrent.*
import java.util.timer.*
import db.*;
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool

public class AberOWLSimilarity extends DefaultSimilarity {

  /*  public long computeNorm(FieldInvertState state) {
    norm = (float) (state.getBoost() / state.getLength())
    return norm
  }
  */
  public float idf(int i, int i1) {
    return 1;
  }

  public float lengthNorm(FieldInvertState state) {
    def a = (1 / Math.pow(state.getUniqueTermCount(), 2))
    //    println state.getName()+": "+a
    a
  }
}
