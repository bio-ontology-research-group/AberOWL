package src

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.annotation.Nonnull;

import org.semanticweb.owlapi.io.OWLObjectRenderer;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

/**
 * An implementation of the OWLObjectRenderer interface. (Renders standalone
 * class class expressions and axioms in the manchester syntax).
 * 
 * @author Matthew Horridge, The University Of Manchester, Bio-Health
 *         Informatics Group
 * @since 2.2.0
 */
public class AberOWLSyntaxRendererImpl implements OWLObjectRenderer {

    @Nonnull
  private AberOWLSyntaxRenderer ren;
    @Nonnull
  private final WriterDelegate writerDelegate;

  /** default constructor */
  public AberOWLSyntaxRendererImpl() {
    writerDelegate = new WriterDelegate();
    ren = new AberOWLSyntaxRenderer(writerDelegate, new SimpleShortFormProvider());
  }

    @Nonnull
    @Override
  public synchronized String render(@Nonnull OWLObject object) {
    writerDelegate.reset();
    object.accept(ren);
    return writerDelegate.toString();
  }

    @Override
  public synchronized void setShortFormProvider(ShortFormProvider shortFormProvider) {
    ren = new AberOWLSyntaxRenderer(writerDelegate, shortFormProvider);
  }

  private static class WriterDelegate extends Writer {

    private StringWriter delegate;

    /** default constructor */
    WriterDelegate() {}

    protected void reset() {
      delegate = new StringWriter();
    }

        @Nonnull
        @Override
    public String toString() {
      return delegate.getBuffer().toString();
    }

        @Override
    public void close() throws IOException {
      delegate.close();
    }

        @Override
    public void flush() {
      delegate.flush();
    }

        @Override
    public void write(char[] cbuf, int off, int len) {
      delegate.write(cbuf, off, len);
    }
  }
}
