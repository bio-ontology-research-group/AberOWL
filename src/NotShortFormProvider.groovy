/* 
 * Copyright 2015 Luke Slater (luke.slater@kaust.edu.sa).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package src

import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx

/**
 * A short form provider which is not a short form provider. Provides full class IRI.
 * 
 * @see ShortFormProvider
 * @author OWLAPI, Luke Slater (luke.slater@kaust.edu.sa)
 */
public class NotShortFormProvider implements ShortFormProvider {
  /**
   * Return the class IRI as a 'short form' of the class IRI.
   */
  @Override
  public String getShortForm(OWLEntity entity) {
    return entity.getIRI()
  }

  @Override
  public void dispose() {}
}

