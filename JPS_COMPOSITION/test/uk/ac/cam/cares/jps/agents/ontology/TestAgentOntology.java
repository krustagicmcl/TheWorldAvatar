package uk.ac.cam.cares.jps.agents.ontology;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import uk.ac.cam.cares.jps.agents.discovery.ServiceDiscovery;
import uk.ac.cam.cares.jps.agents.ontology.ServiceBuilder;
import uk.ac.cam.cares.jps.agents.ontology.ServiceReader;
import uk.ac.cam.cares.jps.agents.ontology.ServiceWriter;
import uk.ac.cam.cares.jps.base.config.AgentLocator;
import uk.ac.cam.cares.jps.composition.servicemodel.MessagePart;
import uk.ac.cam.cares.jps.composition.servicemodel.Operation;
import uk.ac.cam.cares.jps.composition.servicemodel.Service;

public class TestAgentOntology extends TestCase {
	
	private List<MessagePart> createMessageParts(String... types) throws URISyntaxException {
		List<MessagePart> result = new ArrayList<MessagePart>();
		
		for (String current : types) {
			MessagePart part = new MessagePart();
			part.setModelReference(new URI(current));
			result.add(part);
		}
		
		return result;
	}

	public void testServiceBuilder() {
		
		Service service = new ServiceBuilder()
			.operation(null, "http://www.theworldavatar.com/test1")
			.input("inputrefuri1", false, "inputname1", true)
			.input("inputrefuri2", false, "inputname2", false)
			.output("outputrefuri3", false, "outputname3", true)
			.output("outputrefuri4", false, "outputname4", false)
			.operation(null, "http://www.theworldavatar.com/test2")
			.input("inputrefuri5", false, "inputname5", true)
			.build();
		
		Operation op1 = service.getOperations().get(0);
		assertEquals("http://www.theworldavatar.com/test1", op1.getHttpUrl());
		MessagePart part = op1.getInputs().get(0).getMandatoryParts().get(0);
		assertEquals("inputrefuri1", part.getModelReference().toASCIIString());
		assertEquals("inputname1", part.getName());
		part = op1.getInputs().get(0).getOptionalParts().get(0);
		assertEquals("inputrefuri2", part.getModelReference().toASCIIString());
		assertEquals("inputname2", part.getName());
		part = op1.getOutputs().get(0).getMandatoryParts().get(0);
		assertEquals("outputrefuri3", part.getModelReference().toASCIIString());
		assertEquals("outputname3", part.getName());
		part = op1.getOutputs().get(0).getOptionalParts().get(0);
		assertEquals("outputrefuri4", part.getModelReference().toASCIIString());
		assertEquals("outputname4", part.getName());
		
		Operation op2 = service.getOperations().get(1);
		assertEquals("http://www.theworldavatar.com/test2", op2.getHttpUrl());
		part = op2.getInputs().get(0).getMandatoryParts().get(0);
		assertEquals("inputrefuri5", part.getModelReference().toString());
		assertEquals("inputname5", part.getName());
	}	
	
	public void testOWLSerializationOneOperationTwoMandatoryParameters() throws FileNotFoundException, URISyntaxException {
		
		Service service = new ServiceBuilder()
				.operation(null, "http://www.theworldavatar.com/test1")
				.input("inputrefuri1", "inputname1")
				.output("outputrefuri2", "outputname2")
				.build();
		
		String owlService = new ServiceWriter().generateSerializedModel(service, "Test");
		
		System.out.println();
		System.out.println(owlService);
		System.out.println();
		
		List<Service> services = new ServiceReader().parse(owlService, null);
		assertEquals(1, services.size());
		assertEquals(1, services.get(0).getOperations().size());

		Operation op = services.get(0).getOperations().get(0);
		assertEquals("http://www.theworldavatar.com/test1", op.getHttpUrl());
		
		// assert input parameters
		assertEquals(1, op.getInputs().size());
		assertEquals(1, op.getInputs().get(0).getMandatoryParts().size());
		assertEquals(0, op.getInputs().get(0).getOptionalParts().size());
		MessagePart part = op.getInputs().get(0).getMandatoryParts().get(0);
		assertEquals("inputrefuri1", part.getModelReference().toASCIIString());
		assertEquals(false, part.isArray());
		assertEquals("inputname1", part.getName());
		
		// assert output parameters
		assertEquals(1, op.getOutputs().size());
		assertEquals(1, op.getOutputs().get(0).getMandatoryParts().size());
		assertEquals(0, op.getOutputs().get(0).getOptionalParts().size());
		part = op.getOutputs().get(0).getMandatoryParts().get(0);
		assertEquals("outputrefuri2", part.getModelReference().toASCIIString());
		assertEquals(false, part.isArray());
		assertEquals("outputname2", part.getName());
	}
	
	public void testOWLSerializationTwoOperationsSevenParameters() throws FileNotFoundException, URISyntaxException {
		
		Service service = new ServiceBuilder()
				.operation(null, "http://www.theworldavatar.com/test1")
				.output("outputrefuri1", false, "outputname1", true)
				.input("inputrefuri2", false, "inputname2", true)
				.output("outputrefuri3", false, "outputname3", true)
				.input("inputrefuri4", false, "inputname4", false)
				.input("inputrefuri5", false, "inputname5", true)
				.operation(null, "http://www.theworldavatar.com/test2")
				.input("inputrefuri6", false, "inputname6", true)
				.output("outputrefuri7", false, "outputname7", true)
				.build();
		
		String owlService = new ServiceWriter().generateSerializedModel(service, "Test");
		
		System.out.println();
		System.out.println(owlService);
		System.out.println();
		
		List<Service> services = new ServiceReader().parse(owlService, null);
		assertEquals(1, services.size());
		assertEquals(2, services.get(0).getOperations().size());

		// assert first operation
		Operation op = services.get(0).getOperations().get(0);
		assertEquals("http://www.theworldavatar.com/test1", op.getHttpUrl());
		
		// assert input parameters
		assertEquals(1, op.getInputs().size());
		assertEquals(2, op.getInputs().get(0).getMandatoryParts().size());
		assertEquals(1, op.getInputs().get(0).getOptionalParts().size());
		MessagePart part = op.getInputs().get(0).getMandatoryParts().get(0);
		assertEquals("inputrefuri2", part.getModelReference().toASCIIString());
		part = op.getInputs().get(0).getMandatoryParts().get(1);
		assertEquals("inputrefuri5", part.getModelReference().toASCIIString());
		part = op.getInputs().get(0).getOptionalParts().get(0);
		assertEquals("inputrefuri4", part.getModelReference().toASCIIString());

		// assert output parameters
		assertEquals(1, op.getOutputs().size());
		assertEquals(2, op.getOutputs().get(0).getMandatoryParts().size());
		assertEquals(0, op.getOutputs().get(0).getOptionalParts().size());
		part = op.getOutputs().get(0).getMandatoryParts().get(0);
		assertEquals("outputrefuri1", part.getModelReference().toASCIIString());
		part = op.getOutputs().get(0).getMandatoryParts().get(1);
		assertEquals("outputrefuri3", part.getModelReference().toASCIIString());
		
		// assert second operation
		op = services.get(0).getOperations().get(1);
		assertEquals("http://www.theworldavatar.com/test2", op.getHttpUrl());
		
		// assert input parameters
		assertEquals(1, op.getInputs().size());
		assertEquals(1, op.getInputs().get(0).getMandatoryParts().size());
		assertEquals(0, op.getInputs().get(0).getOptionalParts().size());
		part = op.getInputs().get(0).getMandatoryParts().get(0);
		assertEquals("inputrefuri6", part.getModelReference().toASCIIString());
		
		// assert output parameters
		assertEquals(1, op.getOutputs().size());
		assertEquals(1, op.getOutputs().get(0).getMandatoryParts().size());
		assertEquals(0, op.getOutputs().get(0).getOptionalParts().size());
		part = op.getOutputs().get(0).getMandatoryParts().get(0);
		assertEquals("outputrefuri7", part.getModelReference().toASCIIString());
	}
	
	public void testOWLSerializationWithArrayParameters() throws FileNotFoundException, URISyntaxException {
		
		Service service = new ServiceBuilder()
				.operation(null, "http://www.theworldavatar.com/test1")
				.output("outputrefuri1", true, "outputname1", true)
				.input("inputrefuri2", false, "inputname2", true)
				.input("inputrefuri3", true, "inputname3", true)
				.output("outputrefuri4", false, "outputname4", true)
				.build();
		
		String owlService = new ServiceWriter().generateSerializedModel(service, null);
		
		System.out.println();
		System.out.println(owlService);
		System.out.println();
		
		List<Service> services = new ServiceReader().parse(owlService, null);
		
		Operation op = services.get(0).getOperations().get(0);
		List<MessagePart> parts = op.getInputs().get(0).getMandatoryParts();
		assertEquals(false, parts.get(0).isArray());
		assertEquals(true, parts.get(1).isArray());
		parts = op.getOutputs().get(0).getMandatoryParts();
		assertEquals(true, parts.get(0).isArray());
		assertEquals(false, parts.get(1).isArray());
	}
	
	public void testOWLSerializationWithValues() throws FileNotFoundException, URISyntaxException {
		
		Service service = new ServiceBuilder()
				.operation(null, "http://www.theworldavatar.com/test1")
				.output("outputrefuri1", true, "outputname1", true)
				.input("inputrefuri2", false, "inputname2", true)
				.input("inputrefuri3", true, "inputname3", true)
				.output("outputrefuri4", false, "outputname4", true)
				.build();
		
		String owlService = new ServiceWriter().generateSerializedModel(service, "Test");
		
		System.out.println();
		System.out.println(owlService);
		System.out.println();
		
		List<Service> services = new ServiceReader().parse(owlService, null);
		
		Operation op = services.get(0).getOperations().get(0);
		List<MessagePart> parts = op.getInputs().get(0).getMandatoryParts();
		assertEquals(false, parts.get(0).isArray());
		assertEquals(true, parts.get(1).isArray());
		parts = op.getOutputs().get(0).getMandatoryParts();
		assertEquals(true, parts.get(0).isArray());
		assertEquals(false, parts.get(1).isArray());
	}
	
	public void testServiceDiscovery() throws Exception {
		
		//String fileDirectory = AgentLocator.getCurrentJpsAppDirectory(this) + "/testres/serviceowlfiles";
		String fileDirectory = "C:/Users/nasac/Documents/GIT/JPS_COMPOSITION/" + "/testres/serviceowlfiles";
		
		ServiceDiscovery discovery = new ServiceDiscovery(fileDirectory);
		
		List<MessagePart> inputs = createMessageParts("op2inputrefuri1");
		List<Service> result = discovery.getAllServiceCandidates(inputs, new ArrayList<Service>());
		
		assertEquals(1, result.size());
	}
		
	public void testOWLSerializationWriteToFile() throws FileNotFoundException {
		
		Service service = new ServiceBuilder()
				.operation(null, "http://www.theworldavatar.com/op2")
				.input("op2inputrefuri1", "op1inputname1")
				.output("op2outputrefuri2", "op1outputname2")
				.build();
		
		new ServiceWriter().writeAsOwlFile(service, "Op2", "C:\\Users\\nasac\\Documents\\TMP\\newAgentsMSM");
	}
}