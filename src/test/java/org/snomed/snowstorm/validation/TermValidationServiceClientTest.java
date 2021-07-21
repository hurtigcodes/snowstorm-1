package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.response.InvalidContent;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.DeserializationFeature;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TermValidationServiceClientTest {

	private final TermValidationServiceClient client = new TermValidationServiceClient(null, 0.6f);

	@Test
	void testHandleResponse() throws IOException {
		final List<InvalidContent> invalidContents = new ArrayList<>();

		client.handleResponse(client.getObjectMapper().readValue(getClass().getResourceAsStream("/term-validation-service/response-1.json"),
				TermValidationServiceClient.ValidationResponse.class),
				new Concept(Concepts.CLINICAL_FINDING), "MAIN/TEST/TEST-123", invalidContents);

		final Map<String, InvalidContent> idToInvalidContentMap = invalidContents.stream().collect(Collectors.toMap(InvalidContent::getRuleId, Function.identity()));
		assertEquals(2, invalidContents.size());

		final InvalidContent duplicate = idToInvalidContentMap.get(TermValidationServiceClient.DUPLICATE_RULE_ID);
		assertNotNull(duplicate);
		assertEquals("Terms are similar to description 'Atypical diabetes mellitus (disorder)' in concept 530558861000132104. Is this a duplicate?", duplicate.getMessage());
		assertEquals("404684003", duplicate.getConceptId());
		assertEquals("404684003", duplicate.getComponentId());// Same as concept

		final InvalidContent similarToInactive = idToInvalidContentMap.get(TermValidationServiceClient.SIMILAR_TO_INACTIVE_RULE_ID);
		assertNotNull(similarToInactive);
		assertEquals("Concept is similar to many that have been made inactive in the past with inactivation reason 'Limited'.", similarToInactive.getMessage());
		assertEquals("404684003", similarToInactive.getConceptId());
		assertEquals("404684003", similarToInactive.getComponentId());// Same as concept
	}
}
