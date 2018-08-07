package uk.ac.cam.cares.jps.agent.owl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import uk.ac.cam.cares.jps.composition.ServiceModel.MessageContent;
import uk.ac.cam.cares.jps.composition.ServiceModel.MessagePart;
import uk.ac.cam.cares.jps.composition.ServiceModel.Operation;
import uk.ac.cam.cares.jps.composition.ServiceModel.Service;

public class ServiceReader {
	
	public List<Service> parse(String in, String baseUri) throws URISyntaxException {
		InputStream is = new ByteArrayInputStream( in.getBytes(StandardCharsets.UTF_8) );		
		return parse(is, baseUri);
	}

	public List<Service> parse(InputStream in, String baseUri) throws URISyntaxException {

		OntModel model = null;
		List<Service> result = new ArrayList<Service>();
		try {
			// create an empty model
			model = ModelFactory.createOntologyModel(OntModelSpec.RDFS_MEM);
			// Parse the stream into a model
			model.read(in, baseUri, "RDF/XML");
			result = parseService(model);
		} finally {
			if (model != null)
				model.close();
		}

		return result;
	}

	public ArrayList<Service> parseService(OntModel model) throws URISyntaxException {
		ArrayList<Service> result = new ArrayList<Service>();
		Service service = null;
		Individual individual;
		ExtendedIterator<Individual> services = model.listIndividuals(MSM.Service.Node());
		while (services.hasNext()) {
			individual = services.next();
				
			service = obtainService(individual);
			if (service != null) {
				result.add(service);
			}

		}

		return result;
	}
	
	
    private Service obtainService(Individual individual) throws URISyntaxException {
    	
        if (individual == null)
            return null;

        Service service = new Service(new URI(individual.getURI()));
 
        NodeIterator hasOpValues = null;
        try {
            hasOpValues = individual.listPropertyValues(MSM.hasOperation.Property());
            List<Operation> operations = obtainOperations(hasOpValues);
            service.setOperations(operations);
        } finally {
            if (hasOpValues != null)
                hasOpValues.close();
        }

        if (individual.hasProperty(MSM.hasHttpUrl.Property())) {
            service.setHttpUrl(individual.getProperty(MSM.hasHttpUrl.Property()).getString());
        }

        return service;
    }
    
    private List<Operation> obtainOperations(NodeIterator hasOpValues) throws URISyntaxException {

        List<Operation> operations = new ArrayList<Operation>();
        if (hasOpValues == null)
            return operations;

        Operation operation;
        Individual individual;
        RDFNode value;

        while (hasOpValues.hasNext()) {
            value = hasOpValues.next();
            if (value.canAs(Individual.class)) {
                individual = value.as(Individual.class);
                
                String httpUrl = null;
                if (individual.hasProperty(MSM.hasHttpUrl.Property())) {
                    httpUrl = individual.getProperty(MSM.hasHttpUrl.Property()).getString();
                }
 
                operation = new Operation(new URI(individual.getURI()), httpUrl);
 
                // Process Message Contents
                MessageContent mc;
                RDFNode rdfNode;
                rdfNode = individual.getPropertyValue(MSM.hasInput.Property());
                mc = obtainMessageContent(rdfNode);
                if (mc != null)
                    operation.addInput(mc);

                rdfNode = individual.getPropertyValue(MSM.hasOutput.Property());
                mc = obtainMessageContent(rdfNode);
                if (mc != null)
                    operation.addOutput(mc);

                operations.add(operation);
            }
        }
        return operations;
    }
    
    private MessageContent obtainMessageContent(RDFNode inputNode) throws URISyntaxException {
        MessageContent result = null;
        if (inputNode == null || !inputNode.canAs(Individual.class)) {
            return result;
        }

        Individual individual = inputNode.as(Individual.class);
        result = new MessageContent(new URI(individual.getURI()));
 
        // Process mandatory parts
        List<MessagePart> mps;
        mps = obtainParts(individual, MSM.hasMandatoryPart.Property());
        result.setMandatoryParts(mps);

        // Process optional parts
        mps = obtainParts(individual, MSM.hasOptionalPart.Property());
        result.setOptionalParts(mps);

        return result;
    }
    
    private List<MessagePart> obtainParts(Individual individual, Property partsProperty) throws URISyntaxException {

        List<MessagePart> result = new ArrayList<MessagePart>();
        if (individual == null)
            return result;

        RDFNode node;
        MessagePart messagePart;
        NodeIterator nodeIterator = null;
        try {
            nodeIterator = individual.listPropertyValues(partsProperty);
            while (nodeIterator.hasNext()) {
                node = nodeIterator.next();
                messagePart = obtainMessagePart(node);
                if (messagePart != null)
                    result.add(messagePart);
            }
        } finally {
            if (nodeIterator != null)
                nodeIterator.close();
        }
        return result;
    }
    
    private MessagePart obtainMessagePart(RDFNode inputNode) throws URISyntaxException {

        MessagePart result = null;
        if (inputNode == null || !inputNode.canAs(Individual.class)) {
            return result;
        }

        Individual individual = inputNode.as(Individual.class);
        result = new MessagePart(new URI(individual.getURI()));
        
        String modelReference = individual.getProperty(MSM.modelReference.Property()).getString();
        result.setModelReference(new URI(modelReference));
        
        Statement statement = individual.getProperty(MSM.hasValue.Property());
        if (statement != null) {
        	result.setValue(statement.getString());
        }
       
        // TODO-AE datatypeValue should be not literal (see definition of MessagePart but an URI) --> Xiaochi
        statement = individual.getProperty(MSM.hasDatatypeValue.Property());
        if (statement != null) {
        	result.setDatatypeValue(statement.getString());
        }
        
//        List<MessagePart> mps;
//        mps = obtainParts(individual, MSM.hasMandatoryPart.Property());
//        result.setMandatoryParts(mps);
//
//        mps = obtainParts(individual, MSM.hasOptionalPart.Property());
//        result.setOptionalParts(mps);

        return result;
    }
}