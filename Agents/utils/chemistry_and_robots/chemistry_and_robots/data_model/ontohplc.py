import pydantic
from typing import List

from chemistry_and_robots.data_model.iris import *
from pyderivationagent.data_model.utils import *

from chemistry_and_robots.data_model.base_ontology import BaseOntology, OM_Quantity, OM_Duration, OM_QuantityOfDimensionOne
from chemistry_and_robots.data_model.ontolab import *

TXTFILE_EXTENSION = 'txt'
XLSFILE_EXTENSION = 'xls'
MAPPING_FILENAMEEXTENSION = {DBPEDIA_XLSFILE:XLSFILE_EXTENSION, DBPEDIA_TXTFILE:TXTFILE_EXTENSION}

class PeakArea(OM_Quantity):
    clz: str = ONTOHPLC_PEAKAREA

    def create_instance_for_kg(self, g: Graph) -> Graph:
        # <peakArea> <rdf:type> <OntoHPLC:PeakArea>
        g.add((URIRef(self.instance_iri), RDF.type, URIRef(self.clz)))

        # Add below triples following units of measure practices:
        # <peakArea> <om:hasValue> <measureIRI> .
        g.add((URIRef(self.instance_iri), URIRef(OM_HASVALUE), URIRef(self.hasValue.instance_iri)))

        # Add triples for units of measure
        g = self.hasValue.create_instance_for_kg(g)

        return g

class RetentionTime(OM_Duration):
    clz: str = ONTOHPLC_RETENTIONTIME
    refersToSpecies: Optional[str] # only use this for <RetentionTime> when it's in triples <HPLCMethod> <hasRetentionTime> <RetentionTime>

    def create_instance_for_kg(self, g: Graph) -> Graph:
        # <retentionTime> <rdf:type> <OntoHPLC:RetentionTime>
        g.add((URIRef(self.instance_iri), RDF.type, URIRef(self.clz)))

        # Add below triples following units of measure practices:
        # <retentionTime> <om:hasValue> <measureIRI> .
        g.add((URIRef(self.instance_iri), URIRef(OM_HASVALUE), URIRef(self.hasValue.instance_iri)))

        # Add triples for units of measure
        g = self.hasValue.create_instance_for_kg(g)

        return g

class ResponseFactor(OM_QuantityOfDimensionOne):
    clz: str = ONTOHPLC_RESPONSEFACTOR
    refersToSpecies: str

class ChromatogramPoint(BaseOntology):
    clz: str = ONTOHPLC_CHROMATOGRAMPOINT
    indicatesComponent: OntoCAPE_PhaseComponent
    hasPeakArea: PeakArea
    atRetentionTime: RetentionTime

    def create_instance_for_kg(self, g: Graph) -> Graph:
        # <pt> <rdf:type> <OntoHPLC:ChromatogramPoint>
        g.add((URIRef(self.instance_iri), RDF.type, URIRef(self.clz)))

        # <pt> <indicatesComponent> <phase_component>
        g.add((URIRef(self.instance_iri), URIRef(ONTOHPLC_INDICATESCOMPONENT), URIRef(self.indicatesComponent.instance_iri)))
        g = self.indicatesComponent.create_instance_for_kg(g)

        # <pt> <hasPeakArea> <peak_area>
        g.add((URIRef(self.instance_iri), URIRef(ONTOHPLC_HASPEAKAREA), URIRef(self.hasPeakArea.instance_iri)))
        g = self.hasPeakArea.create_instance_for_kg(g)

        # <pt> <atRetentionTime> <retention_time>
        g.add((URIRef(self.instance_iri), URIRef(ONTOHPLC_ATRETENTIONTIME), URIRef(self.atRetentionTime.instance_iri)))
        g = self.atRetentionTime.create_instance_for_kg(g)

        return g

class HPLCReport(BaseOntology):
    clz: str = ONTOHPLC_HPLCREPORT
    remoteFilePath: str
    records: List[ChromatogramPoint]
    generatedFor: ChemicalSolution
    localFilePath: str
    lastLocalModifiedAt: float
    lastUploadedAt: float

class InternalStandard(OntoCAPE_PhaseComponent):
    clz: str = ONTOHPLC_INTERNALSTANDARD

class HPLCMethod(BaseOntology):
    clz: str = ONTOHPLC_HPLCMETHOD
    hasResponseFactor: List[ResponseFactor]
    hasRetentionTime: List[RetentionTime]
    usesInternalStandard: InternalStandard
    rdfs_comment: str
    localFilePath: Optional[str] # TODO bring back to compulsory once formalise the HPLCMethod at deployment
    remoteFilePath: Optional[str] # TODO bring back to compulsory once formalise the HPLCMethod at deployment

class HPLCJob(BaseOntology):
    clz: str = ONTOHPLC_HPLCJOB
    hasReport: HPLCReport
    characterises: ReactionExperiment
    usesMethod: HPLCMethod

class HPLC(LabEquipment):
    clz: str = ONTOHPLC_HIGHPERFORMANCELIQUIDCHROMATOGRAPHY
    reportExtension: str # this should be DBPEDIA_WIKICATFILENAMEEXTENSIONS but we simplify as str
    hasJob: Optional[List[HPLCJob]]
    hasPastReport: Optional[List[HPLCReport]]
