package aberowl



import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@Transactional(readOnly = true)
class OntologySubmissionController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond OntologySubmission.list(params), model:[ontologySubmissionInstanceCount: OntologySubmission.count()]
    }

    def show(OntologySubmission ontologySubmissionInstance) {
        respond ontologySubmissionInstance
    }

    def create() {
        respond new OntologySubmission(params)
    }

    @Transactional
    def save(OntologySubmission ontologySubmissionInstance) {
        if (ontologySubmissionInstance == null) {
            notFound()
            return
        }

        if (ontologySubmissionInstance.hasErrors()) {
            respond ontologySubmissionInstance.errors, view:'create'
            return
        }

        ontologySubmissionInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'ontologySubmission.label', default: 'OntologySubmission'), ontologySubmissionInstance.id])
                redirect ontologySubmissionInstance
            }
            '*' { respond ontologySubmissionInstance, [status: CREATED] }
        }
    }

    def edit(OntologySubmission ontologySubmissionInstance) {
        respond ontologySubmissionInstance
    }

    @Transactional
    def update(OntologySubmission ontologySubmissionInstance) {
        if (ontologySubmissionInstance == null) {
            notFound()
            return
        }

        if (ontologySubmissionInstance.hasErrors()) {
            respond ontologySubmissionInstance.errors, view:'edit'
            return
        }

        ontologySubmissionInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'OntologySubmission.label', default: 'OntologySubmission'), ontologySubmissionInstance.id])
                redirect ontologySubmissionInstance
            }
            '*'{ respond ontologySubmissionInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(OntologySubmission ontologySubmissionInstance) {

        if (ontologySubmissionInstance == null) {
            notFound()
            return
        }

        ontologySubmissionInstance.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'OntologySubmission.label', default: 'OntologySubmission'), ontologySubmissionInstance.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'ontologySubmission.label', default: 'OntologySubmission'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
