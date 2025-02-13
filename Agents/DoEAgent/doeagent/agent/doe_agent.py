from typing import List
import json

from pyderivationagent import DerivationAgent
from pyderivationagent import DerivationInputs
from pyderivationagent import DerivationOutputs

from doeagent.kg_operations import *
from doeagent.data_model import *
from doeagent.doe_algo import *

# Disable excessive debug logging from numba module
import logging
logging.getLogger("numba").setLevel(logging.WARNING)


class DoEAgent(DerivationAgent):
    def __init__(self,
        **kwargs
    ):
        super().__init__(**kwargs)

        # Initialise the sparql_client
        self.sparql_client = self.get_sparql_client(ChemistryAndRobotsSparqlClient)

    def agent_input_concepts(self) -> list:
        return [ONTODOE_DESIGNOFEXPERIMENT]

    def agent_output_concepts(self) -> list:
         return [ONTOREACTION_REACTIONEXPERIMENT]

    def validate_inputs(self, http_request) -> bool:
        return super().validate_inputs(http_request)

    def process_request_parameters(self, derivation_inputs: DerivationInputs, derivation_outputs: DerivationOutputs):
        # Check if the input is in correct format, and return OntoDoE.DesignOfExperiment instance
        try:
            doe_instance = self.sparql_client.get_doe_instance(derivation_inputs.getIris(ONTODOE_DESIGNOFEXPERIMENT)[0])
        except Exception as e:
            self.logger.error(e)
        self.logger.info("Collected inputs from the knowledge graph: ")
        self.logger.info(json.dumps(doe_instance.dict()))

        # Call function to suggest the new experiment and return an instance of dataclass OntoDoE.NewExperiment
        new_rxn_exp = suggest(doe_instance)

        # Upload the created OntoRxn:ReactionVariation triples to KG
        # Also update the triple between OntoDoE:DesignOfExperiment and OntoRxn:ReactionVariation
        g = self.sparql_client.collect_triples_for_new_experiment(doe_instance, new_rxn_exp)

        # Add the whole graph to output triples
        derivation_outputs.addGraph(g)


def suggest(doe_instance: DesignOfExperiment) -> List[ReactionExperiment]:
    """
        This method suggests the new experiment given information provided for design of experiment exercise.

        Arguments:
            doe_instance - instance of dataclass OntoDoE.DesignOfExperiment
    """

    # TODO this method calls summit doe, can be expanded in the future
    new_exp = proposeNewExperiment(doe_instance)

    return new_exp


# Show an instructional message at the DoEAgent servlet root
def default():
    """
        Instructional message at the app root.
    """
    msg  = "This is an asynchronous agent that capable of conducting Design Of Experiment (DoE).<BR>"
    msg += "For more information, please visit https://github.com/cambridge-cares/TheWorldAvatar/tree/main/Agents/DoEAgent#readme<BR>"
    return msg
