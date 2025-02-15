################################################
# Authors: Markus Hofmeister (mh807@cam.ac.uk) #    
# Date: 01 Apr 2022                            #
################################################

# The purpose of this module is to provide templates for (frequently)
# required SPARQL queries

from airquality.datamodel import *


def all_airquality_station_ids() -> str:
    # Returns query to retrieve all identifiers of instantiated stations
    query = f"""
        SELECT ?id
        WHERE {{
            ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                     <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" ;
                     <{EMS_HAS_IDENTIFIER}> ?id 
              }}
    """
    return query


def instantiated_airquality_stations(circle_center: str = None,
                                     circle_radius: str = None) -> str:
    # Returns query to retrieve identifiers and IRIs of instantiated stations
    if not circle_center and not circle_radius:
        # Retrieve all stations
        query = f"""
            SELECT ?id ?station
            WHERE {{
            ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                     <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" ;
                     <{EMS_HAS_IDENTIFIER}> ?id 
                }}
        """
    else:
        # Retrieve only stations in provided circle (radius in km)
        query = f"""
            {create_sparql_prefix('geo')}
            SELECT ?id ?station
            WHERE {{
                  SERVICE geo:search {{
                    ?station geo:search "inCircle" .
                    ?station geo:predicate <{EMS_HAS_OBSERVATION_LOCATION}> .
                    ?station geo:searchDatatype <{GEOLIT_LAT_LON}> .
                    ?station geo:spatialCircleCenter "{circle_center}" .
                    ?station geo:spatialCircleRadius "{circle_radius}" . 
                }}
                ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                         <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" ;
                         <{EMS_HAS_IDENTIFIER}> ?id 
                }}
        """
    
    return query


def instantiated_airquality_stations_with_details(circle_center: str = None,
                                                  circle_radius: str = None) -> str:
    # Returns query to retrieve all instantiated station details
    if not circle_center and not circle_radius:
        # Retrieve all stations
        query = f"""
            SELECT ?stationID ?station ?label ?latlon ?elevation ?dataIRI
            WHERE {{
            ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                     <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" ;
                     <{EMS_HAS_IDENTIFIER}> ?stationID .
            OPTIONAL {{ ?station <{RDFS_LABEL}> ?label }}
            OPTIONAL {{ ?station <{EMS_HAS_OBSERVATION_LOCATION}> ?latlon }}
            OPTIONAL {{ ?station <{EMS_HAS_OBSERVATION_ELEVATION}> ?elevation }}
            OPTIONAL {{ ?station <{EMS_REPORTS}>/<{OM_HAS_VALUE}> ?dataIRI }}
            }}
        """
    else:
        # Retrieve only stations in provided circle (radius in km)
        query = f"""
            {create_sparql_prefix('geo')}
            SELECT ?stationID ?station ?label ?latlon ?elevation ?dataIRI
            WHERE {{
                  SERVICE geo:search {{
                    ?station geo:search "inCircle" .
                    ?station geo:predicate <{EMS_HAS_OBSERVATION_LOCATION}> .
                    ?station geo:searchDatatype <{GEOLIT_LAT_LON}> .
                    ?station geo:spatialCircleCenter "{circle_center}" .
                    ?station geo:spatialCircleRadius "{circle_radius}" . 
                }}
                ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                         <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" ;
                         <{EMS_HAS_IDENTIFIER}> ?stationID .
                OPTIONAL {{ ?station <{RDFS_LABEL}> ?label }}
                OPTIONAL {{ ?station <{EMS_HAS_OBSERVATION_LOCATION}> ?latlon }}
                OPTIONAL {{ ?station <{EMS_HAS_OBSERVATION_ELEVATION}> ?elevation }}
                OPTIONAL {{ ?station <{EMS_REPORTS}>/<{OM_HAS_VALUE}> ?dataIRI }}
                }}
        """
    
    return query


def add_station_data(station_iri: str = None, dataSource: str = None, 
                     label: str = None, id: str = None, location: str = None,
                     elevation: float = None) -> str:
    if station_iri:
        # Returns triples to instantiate a measurement station according to OntoEMS
        triples = f"<{station_iri}> <{RDF_TYPE}> <{EMS_REPORTING_STATION}> . "
        if dataSource: triples += f"<{station_iri}> <{EMS_DATA_SOURCE}> \"{dataSource}\"^^<{XSD_STRING}> . "
        if label: triples += f"<{station_iri}> <{RDFS_LABEL}> \"{label}\"^^<{XSD_STRING}> . "
        if id: triples += f"<{station_iri}> <{EMS_HAS_IDENTIFIER}> \"{id}\"^^<{XSD_STRING}> . "
        if location: triples += f"<{station_iri}> <{EMS_HAS_OBSERVATION_LOCATION}> \"{location}\"^^<{GEOLIT_LAT_LON}> . "
        if elevation: triples += f"<{station_iri}> <{EMS_HAS_OBSERVATION_ELEVATION}> \"{elevation}\"^^<{XSD_FLOAT}> . "
    else:
        triples = None
    
    return triples


def add_om_quantity(station_iri, quantity_iri, quantity_type, data_iri,
                    data_iri_type, unit, symbol, comment, sameas=None):
    """
        Create triples to instantiate station measurements according to OntoEMS
    """

    triples = f"""
        <{station_iri}> <{EMS_REPORTS}> <{quantity_iri}> . 
        <{quantity_iri}> <{RDF_TYPE}> <{quantity_type}> . 
        <{quantity_iri}> <{RDFS_COMMENT}> "{comment}"^^<{XSD_STRING}> . 
        <{quantity_iri}> <{OM_HAS_VALUE}>  <{data_iri}> .
        <{data_iri}> <{RDF_TYPE}> <{data_iri_type}> . 
    """
    if unit: 
        triples += f"""<{data_iri}> <{OM_HAS_UNIT}> <{unit}> . 
                       <{unit}> <{RDF_TYPE}> <{OM_UNIT}> . """
        if symbol:
            triples += f"""<{unit}> <{OM_SYMBOL}> "{symbol}"^^<{XSD_STRING}> . """

    # Create optional sameAs for quantity
    if sameas:
        triples += f"""<{quantity_iri}> <{SAMEAS}> <{sameas}> . """

    return triples


def instantiated_observations(station_iris: list = None):
    # Returns query to retrieve (all) instantiated observation types per station
    if station_iris:
        iris = ', '.join(['<'+iri+'>' for iri in station_iris])
        substring = f"""FILTER (?station IN ({iris}) ) """
    else:
        substring = f"""
            ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                     <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" . """
    query = f"""
        SELECT ?station ?stationID ?quantityType ?dataIRI ?comment
        WHERE {{
            ?station <{EMS_HAS_IDENTIFIER}> ?stationID ;
                     <{EMS_REPORTS}> ?quantity .
            {substring}                     
            ?quantity <{OM_HAS_VALUE}> ?dataIRI ;
                      <{RDF_TYPE}> ?quantityType ;
                      <{RDFS_COMMENT}> ?comment . 
        }}
        ORDER BY ?station
    """
    return query


def instantiated_observation_timeseries(station_iris: list = None):
    # Returns query to retrieve (all) instantiated observation time series per station
    if station_iris:
        iris = ', '.join(['<'+iri+'>' for iri in station_iris])
        substring = f"""FILTER (?station IN ({iris}) ) """
    else:
        substring = f"""
            ?station <{RDF_TYPE}> <{EMS_REPORTING_STATION}> ;
                     <{EMS_DATA_SOURCE}> "UK-AIR Sensor Observation Service" . """
    query = f"""
        SELECT ?station ?stationID ?quantityType ?dataIRI ?comment ?tsIRI ?unit
        WHERE {{
            ?station <{EMS_HAS_IDENTIFIER}> ?stationID ;
                     <{EMS_REPORTS}> ?quantity .
            {substring} 
            ?quantity <{OM_HAS_VALUE}> ?dataIRI ;
                      <{RDF_TYPE}> ?quantityType ;
                      <{RDFS_COMMENT}> ?comment .
            ?dataIRI <{TS_HAS_TIMESERIES}> ?tsIRI ;   
                     <{OM_HAS_UNIT}>/<{OM_SYMBOL}> ?unit .
            ?tsIRI <{RDF_TYPE}> <{TS_TIMESERIES}> .
        }}
        ORDER BY ?tsIRI
    """
    return query


def split_insert_query(triples: str, max: int):
    """"
        Split large SPARQL insert query into list of smaller chunks (primarily
        to avoid heap size/memory issues when executing large SPARQL updated)

        Arguments
            triples - original SPARQL update string with individual triples 
                      separated by " . ", i.e. in the form
                      <s1> <p1> <o1> .
                      <s2> <p2> <o2> . 
                      ...
            max - maximum number of triples per SPARQL update
    """

    # Initialise list of return queries
    queries = []

    # Split original query every max'th occurrence of " . " and append queries list
    splits = triples.split(' . ')
    cutoffs = list(range(0, len(splits), max))
    cutoffs.append(len(splits))
    for i in range(len(cutoffs)-1):
        start = cutoffs[i]
        end = cutoffs[i+1]
        query = ' . '.join([t for t in splits[start:end]])
        query = " INSERT DATA { " + query + " } "
        queries.append(query)
    
    return queries
