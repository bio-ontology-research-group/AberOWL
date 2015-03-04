package aberowl



import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@Transactional(readOnly = true)
class OntologyVersionController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond OntologyVersion.list(params), model:[ontologyVersionInstanceCount: OntologyVersion.count()]
    }

    def show(OntologyVersion ontologyVersionInstance) {
        respond ontologyVersionInstance
    }

    def create() {
        respond new OntologyVersion(params)
    }

    @Transactional
    def save(OntologyVersion ontologyVersionInstance) {
        if (ontologyVersionInstance == null) {
            notFound()
            return
        }

        if (ontologyVersionInstance.hasErrors()) {
            respond ontologyVersionInstance.errors, view:'create'
            return
        }

        ontologyVersionInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'ontologyVersion.label', default: 'OntologyVersion'), ontologyVersionInstance.id])
                redirect ontologyVersionInstance
            }
            '*' { respond ontologyVersionInstance, [status: CREATED] }
        }
    }

    def edit(OntologyVersion ontologyVersionInstance) {
        respond ontologyVersionInstance
    }

    @Transactional
    def update(OntologyVersion ontologyVersionInstance) {
        if (ontologyVersionInstance == null) {
            notFound()
            return
        }

        if (ontologyVersionInstance.hasErrors()) {
            respond ontologyVersionInstance.errors, view:'edit'
            return
        }

        ontologyVersionInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'OntologyVersion.label', default: 'OntologyVersion'), ontologyVersionInstance.id])
                redirect ontologyVersionInstance
            }
            '*'{ respond ontologyVersionInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(OntologyVersion ontologyVersionInstance) {

        if (ontologyVersionInstance == null) {
            notFound()
            return
        }

        ontologyVersionInstance.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'OntologyVersion.label', default: 'OntologyVersion'), ontologyVersionInstance.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'ontologyVersion.label', default: 'OntologyVersion'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
