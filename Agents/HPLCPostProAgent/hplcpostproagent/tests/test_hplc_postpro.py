import hplcpostproagent.tests.conftest as conftest
from rdflib import Graph
import logging
import pkgutil
import pytest
import time
import os


logging.getLogger("py4j").setLevel(logging.INFO)
logger = logging.getLogger("test_hplc_postpro")

pytest_plugins = ["docker_compose"]

@pytest.mark.parametrize(
    "rxn_exp_iri,report_path_in_pkg,hplc_digital_twin,chemical_solution_iri,hplc_method_iri,local_agent_test",
    [
        (conftest.NEW_RXN_EXP_1_IRI, conftest.HPLC_REPORT_XLS_PATH_IN_PKG, conftest.HPLC_DIGITAL_TWIN_1, conftest.CHEMICAL_SOLUTION_1, conftest.HPLC_METHOD_IRI, True),
        (conftest.NEW_RXN_EXP_2_IRI, conftest.HPLC_REPORT_TXT_PATH_IN_PKG, conftest.HPLC_DIGITAL_TWIN_2, conftest.CHEMICAL_SOLUTION_2, conftest.HPLC_METHOD_IRI, True),
        (conftest.NEW_RXN_EXP_1_IRI, conftest.HPLC_REPORT_XLS_PATH_IN_PKG, conftest.HPLC_DIGITAL_TWIN_1, conftest.CHEMICAL_SOLUTION_1, conftest.HPLC_METHOD_IRI, False),
        (conftest.NEW_RXN_EXP_2_IRI, conftest.HPLC_REPORT_TXT_PATH_IN_PKG, conftest.HPLC_DIGITAL_TWIN_2, conftest.CHEMICAL_SOLUTION_2, conftest.HPLC_METHOD_IRI, False),
    ],
)
def test_hplc_postpro_agent(
    initialise_client, retrieve_hplc_report, create_hplc_postpro_agent,
    rxn_exp_iri, report_path_in_pkg, hplc_digital_twin, chemical_solution_iri, hplc_method_iri, local_agent_test
):
    sparql_client = initialise_client
    initialise_triples(sparql_client)
    hplc_postpro_agent = create_hplc_postpro_agent(register_agent=True, random_agent_iri=local_agent_test)
    if local_agent_test:
        hplc_postpro_agent._start_monitoring_derivations()

    # Verify that knowledge base is NOT empty
    res = sparql_client.getAmountOfTriples()
    assert res > 0

    # Upload HPLC report to file server
    local_file_path, timestamp_last_modified = retrieve_hplc_report(report_path_in_pkg)
    hplc_report_path = local_file_path
    hplc_report_path = hplc_report_path[1:] if hplc_report_path.startswith('/') else hplc_report_path
    hplc_report_path = hplc_report_path[1:] if hplc_report_path.startswith('\\') else hplc_report_path
    remote_report_subdir = os.path.join(conftest.getShortName(hplc_digital_twin), hplc_report_path)
    hplc_report_iri = sparql_client.upload_raw_hplc_report_to_kg(
        local_file_path=local_file_path,
        timestamp_last_modified=timestamp_last_modified,
        remote_report_subdir=remote_report_subdir,
        hplc_digital_twin=hplc_digital_twin
    )

    # Make the below connections (note that in normal operation, this should be done as part of HPLCAgent and VapourtecExecutionAgent)
    # <hplc_report> <OntoHPLC:generatedFor> <chemical_solution>
    # <hplc_job> <rdf:type> <OntoHPLC:HPLCJob>
    # <hplc_digital_twin> <OntoHPLC:hasJob> <hplc_job>
    # <hplc_job> <OntoHPLC:hasReport> <hplc_report>
    # <hplc_job> <OntoHPLC:characterises> <rxn_exp>
    # <hplc_job> <OntoHPLC:usesMethod> <hplc_method>
    g = Graph()
    g = sparql_client.collect_triples_for_hplc_job(rxn_exp_iri, chemical_solution_iri, hplc_digital_twin, hplc_report_iri, hplc_method_iri, g)
    sparql_client.uploadGraph(g)

    # Construct derivation_inputs with the iri of HPLCReport
    derivation_inputs = [hplc_report_iri]

    # Iterate over the list of inputs to add and update the timestamp
    for input in derivation_inputs:
        hplc_postpro_agent.derivationClient.addTimeInstance(input)
        # Update timestamp is needed as the timestamp added using addTimeInstance() is 0
        hplc_postpro_agent.derivationClient.updateTimestamp(input)

    # Create derivation instance given above information, the timestamp of this derivation is 0
    derivation_iri = hplc_postpro_agent.derivationClient.createAsyncDerivationForNewInfo(hplc_postpro_agent.agentIRI, derivation_inputs)
    logger.info(f'Initialised successfully, created derivation instance: <{derivation_iri}>')

    # Query timestamp of the derivation for every 20 seconds until it's updated
    currentTimestamp_derivation = 0
    query_performance_indicator = conftest.PREFIX_RDF + conftest.PREFIX_RDFS + """SELECT ?performance_indicator WHERE {?performance_indicator <%s> <%s>; rdf:type/rdfs:subClassOf* <%s>.}""" % (
        conftest.ONTODERIVATION_BELONGSTO, derivation_iri, conftest.ONTOREACTION_PERFORMANCEINDICATOR)
    logger.info("Generated performance indicator: " + str(sparql_client.performQuery(query_performance_indicator)))
    while currentTimestamp_derivation == 0:
        time.sleep(3)
        currentTimestamp_derivation = conftest.get_timestamp(derivation_iri, sparql_client)
        logger.info("The current timestamp for the derivation <%s> is %d" % (derivation_iri, currentTimestamp_derivation))
        logger.info("Generated performance indicator: " + str(sparql_client.performQuery(query_performance_indicator)))

    # Query the new derived IRI
    query_new_derived_iri = """SELECT ?new_iri WHERE {?new_iri <%s> <%s>.}""" % (conftest.ONTODERIVATION_BELONGSTO, derivation_iri)
    response = sparql_client.performQuery(query_new_derived_iri)
    new_derived_iri = [list(r.values())[0] for r in response]

    # Reload the ReactionExperiment instance and check all its information (OutputChemical and PerformanceIndicator) are uploaded and parsed correctly
    reload_rxn_rxp_instance = sparql_client.getReactionExperiment(rxn_exp_iri)[0]
    reload_pi_lst = [pi.instance_iri for pi in reload_rxn_rxp_instance.hasPerformanceIndicator]
    assert all([iri in new_derived_iri for iri in reload_pi_lst])
    for pi in reload_rxn_rxp_instance.hasPerformanceIndicator:
        if pi.hasValue is None:
            logger.info("============================================")
            logger.error("reloaded experiment instance: " + str(reload_rxn_rxp_instance.dict()))
            logger.info("============================================")
        assert pi.hasValue.hasUnit is not None
        assert pi.hasValue.hasNumericalValue is not None
    reload_output_chemical_lst = reload_rxn_rxp_instance.hasOutputChemical
    for oc in reload_output_chemical_lst:
        assert oc.clz == conftest.ONTOREACTION_OUTPUTCHEMICAL
        assert oc.instance_iri is not None
        reload_phase_comp_lst = oc.thermodynamicBehaviour.isComposedOfSubsystem
        for phase_comp in reload_phase_comp_lst:
            assert phase_comp.representsOccurenceOf is not None
            assert phase_comp.hasProperty.hasValue.hasUnitOfMeasure is not None
            assert phase_comp.hasProperty.hasValue.numericalValue is not None
        reload_phase_comp_conc_lst = [pc.hasProperty for pc in oc.thermodynamicBehaviour.isComposedOfSubsystem]
        reload_conc_lst = oc.thermodynamicBehaviour.has_composition.comprisesDirectly
        assert all([conc in reload_phase_comp_conc_lst for conc in reload_conc_lst])
        assert all([conc in reload_conc_lst for conc in reload_phase_comp_conc_lst])

    logger.info("All checks passed.")

    # Shutdown the scheduler to clean up before the next test
    if local_agent_test:
        hplc_postpro_agent.scheduler.shutdown()


def initialise_triples(sparql_client):
    # Clear triple store before any usage
    sparql_client.performUpdate("DELETE WHERE {?s ?p ?o.}")
    print(sparql_client.getAmountOfTriples())

    # Upload the example triples for testing
    for f in ['sample_data/new_exp_data.ttl', 'sample_data/duplicate_ontorxn.ttl',
        'sample_data/dummy_lab.ttl', 'sample_data/rxn_data.ttl', 'sample_data/dummy_post_proc.ttl']:
        data = pkgutil.get_data('chemistry_and_robots', 'resources/'+f).decode("utf-8")
        g = Graph().parse(data=data)
        sparql_client.uploadGraph(g)
