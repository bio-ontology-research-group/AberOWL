package aberowl



import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

class OntologyController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Ontology.list(params), model:[ontologyInstanceCount: Ontology.count()]
    }

    def show(Ontology ontologyInstance) {
        respond ontologyInstance
    }

    def create() {
        respond new Ontology(params)
    }

    @Transactional
    def save(Ontology ontologyInstance) {
        if (ontologyInstance == null) {
            notFound()
            return
        }

        if (ontologyInstance.hasErrors()) {
            respond ontologyInstance.errors, view:'create'
            return
        }

        ontologyInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'ontology.label', default: 'Ontology'), ontologyInstance.id])
                redirect ontologyInstance
            }
            '*' { respond ontologyInstance, [status: CREATED] }
        }
    }

    def edit(Ontology ontologyInstance) {
        respond ontologyInstance
    }

    @Transactional
    def update(Ontology ontologyInstance) {
        if (ontologyInstance == null) {
            notFound()
            return
        }

        if (ontologyInstance.hasErrors()) {
            respond ontologyInstance.errors, view:'edit'
            return
        }

        ontologyInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'Ontology.label', default: 'Ontology'), ontologyInstance.id])
                redirect ontologyInstance
            }
            '*'{ respond ontologyInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(Ontology ontologyInstance) {

        if (ontologyInstance == null) {
            notFound()
            return
        }

        ontologyInstance.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'Ontology.label', default: 'Ontology'), ontologyInstance.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'ontology.label', default: 'Ontology'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
