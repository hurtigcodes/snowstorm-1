package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.otf.owltoolkit.service.SnomedReasonerService;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptView;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.snomed.snowstorm.core.data.services.classification.pojo.EquivalentConceptsResponse;
import org.snomed.snowstorm.rest.pojo.ClassificationUpdateRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Api(tags = "Classification", description = "-")
@RequestMapping(value = "/{branch}/classifications", produces = "application/json")
public class ClassificationController {

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private BranchService branchService;

	@ApiOperation("Retrieve classifications on a branch")
	@RequestMapping(method = RequestMethod.GET)
	public ItemsPage<Classification> findClassifications(@PathVariable String branch) {
		return new ItemsPage<>(classificationService.findClassifications(BranchPathUriUtil.decodePath(branch)));
	}

	@ApiOperation("Retrieve a classification on a branch")
	@RequestMapping(value = "/{classificationId}", method = RequestMethod.GET)
	public Classification findClassification(@PathVariable String branch, @PathVariable String classificationId) {
		return classificationService.findClassification(BranchPathUriUtil.decodePath(branch), classificationId);
	}

	@ApiOperation("Retrieve relationship changes made by a classification run on a branch")
	@RequestMapping(value = "/{classificationId}/relationship-changes", method = RequestMethod.GET, produces = {"application/json", "text/csv"})
	public ItemsPage<RelationshipChange> getRelationshipChanges(
			@PathVariable String branch,
			@PathVariable String classificationId,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "1000") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		int maxLimit = 10_000;
		if (limit > maxLimit) {
			limit = maxLimit;
			offset = 0;
		}

		try {
			return new ItemsPage<>(classificationService.getRelationshipChanges(BranchPathUriUtil.decodePath(branch), classificationId,
					ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), ControllerHelper.getPageRequest(offset, limit)));
		} catch (IllegalStateException e) {
			return null;
		}
	}

	@ApiOperation("Retrieve a preview of a concept with classification changes applied")
	@RequestMapping(value = "/{classificationId}/concept-preview/{conceptId}", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptView getConceptPreview(
			@PathVariable String branch,
			@PathVariable String classificationId,
			@PathVariable String conceptId,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws ServiceException {

		return classificationService.getConceptPreview(BranchPathUriUtil.decodePath(branch), classificationId, conceptId, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@ApiOperation("Retrieve equivalent concepts from a classification run on a branch")
	@RequestMapping(value = "/{classificationId}/equivalent-concepts", method = RequestMethod.GET)
	public ItemsPage<EquivalentConceptsResponse> getEquivalentConcepts(
			@PathVariable String branch,
			@PathVariable String classificationId,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "1000") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return new ItemsPage<>(classificationService.getEquivalentConcepts(BranchPathUriUtil.decodePath(branch), classificationId,
				ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), ControllerHelper.getPageRequest(offset, limit)));
	}

	@ApiOperation("Create a classification on a branch")
	@RequestMapping(method = RequestMethod.POST)
	public ResponseEntity createClassification(@PathVariable String branch,
			@RequestParam(required = false, defaultValue = SnomedReasonerService.ELK_REASONER_FACTORY) String reasonerId,
			UriComponentsBuilder uriComponentsBuilder) throws ServiceException {

		branch = BranchPathUriUtil.decodePath(branch);
		Branch branchObject = branchService.findBranchOrThrow(branch);
		Classification classification = classificationService.createClassification(branchObject, reasonerId);
		return ResponseEntity.created(uriComponentsBuilder.path("/{branch}/classifications/{classificationId}")
				.buildAndExpand(branchObject.getPath(), classification.getId()).toUri()).build();
	}

	@ApiOperation(value = "Update a classification on a branch.",
	notes = "Update the specified classification run by changing its state property. Saving the results is an async operation due to " +
			"the possible high number of changes. It is advised to fetch the state of the classification run until the state changes " +
			"to 'SAVED' or 'SAVE_FAILED'.\n" +
			"Currently only the state can be changed from 'COMPLETED' to 'SAVED'.")
	@RequestMapping(value = "/{classificationId}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public void updateClassification(@PathVariable String branch, @PathVariable String classificationId, @RequestBody ClassificationUpdateRequest updateRequest) {
		if (updateRequest.getStatus() != ClassificationStatus.SAVED) {
			throw new IllegalArgumentException("The only expected status is " + ClassificationStatus.SAVED.toString());
		}
		String path = BranchPathUriUtil.decodePath(branch);
		classificationService.classificationSaveStatusCheck(path, classificationId);
		classificationService.saveClassificationResultsToBranch(path, classificationId, SecurityContextHolder.getContext());
	}

}
