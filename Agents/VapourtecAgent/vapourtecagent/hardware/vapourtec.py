from chemistry_and_robots.data_model import *
import pandas as pd
import numpy as np
import uuid
import os

# Reactor settings
FC_REACTOR_TEMPERATURE = "Reactor Temperature "
FC_RESIDENCE_TIME = "Residence Time"

# Pump settings - each pump
FC_STOICHIOMETRIC_RATIO = "Stoichiometric Ratio "
FC_VOLUMETRIC_RATIO = "Volumetric Ratio "
FC_AUTOSAMPLER_SITE = "Autosampler Site "
FC_REAGENT_CONC = "Reagent Conc "

# Pump settings - the primary reference pump
FC_REAGENT_USE = "Reagent Use"

# Collection settings
FC_WHOLE_PEAK = "Whole Peak"
FC_AUTO_COLLECTION = "Auto Collection"
FC_COLLECTION_OPTION = "Collection Option"
FC_START_VIAL_OVERRIDE = "Start Vial Override"
FC_MANUAL_COLLECT = "Manual Collect"
FC_MANUAL_DIVERT = "Manual Divert"

# Mapping int value returned by FlowCommander.GetState(fc) to the actual state
# TODO double check if any other possible state
# NOTE when adding new mappings, remember to update the list in:
# chemistry_and_robots.data_model.ontovapourtec.LIST_ONTOVAPOURTEC_VALIDSTATE
MAPPING_VAPOURTEC_STATE = {
    0: ONTOVAPOURTEC_IDLE,
    1: ONTOVAPOURTEC_INITIALISING,
    2: ONTOVAPOURTEC_RUNNINGREACTION,
    4: ONTOVAPOURTEC_FINALCLEANING,
}

# Mapping decimal place constraint with settings title
# TODO here we took the short cut, in future this information should be stored in KG
MAPPING_DECIMAL_PLACE_CONSTRAINT = {
    FC_REACTOR_TEMPERATURE: 0,
    FC_RESIDENCE_TIME: 2,
    FC_STOICHIOMETRIC_RATIO: 2,
    FC_VOLUMETRIC_RATIO: 2,
    FC_REAGENT_CONC: 3,
    FC_REAGENT_USE: 2,
    FC_MANUAL_COLLECT: 2,
    FC_MANUAL_DIVERT: 2,
}

def create_exp_run_csv(folder_path: str, rxnexp: ReactionExperiment, list_equip_settings: List[EquipmentSettings]) -> str:
    """
        This function creates the experiment run file to be digested by Vapourtec FlowCommander.

        Arguments:
            list_equip_settings - list of ontolab.EquipmentSettings instances
    """

    # TODO complete the collection settings part
    fc_header = np.array(([[FC_WHOLE_PEAK], ["TRUE"]]))
    fc_header = np.hstack((fc_header, np.array(([[FC_AUTO_COLLECTION], ["TRUE"]]))))
    # fc_header = np.hstack((fc_header, np.array(([[FC_START_VIAL_OVERRIDE], []])))) # set up with AutoSampler settings?
    # fc_header = np.hstack((fc_header, np.array(([[FC_MANUAL_COLLECT], []]))))
    # fc_header = np.hstack((fc_header, np.array(([[FC_MANUAL_DIVERT], []]))))

    for equip_settings in list_equip_settings:
        if isinstance(equip_settings, ReactorSettings):
            fc_header = np.hstack((fc_header, np.array(([[FC_REACTOR_TEMPERATURE + equip_settings.specifies.locationID],
                        [round_setting_value(FC_REACTOR_TEMPERATURE, equip_settings.hasReactorTemperatureSetting.hasQuantity.hasValue.hasNumericalValue)]]))))
            fc_header = np.hstack((fc_header, np.array(([[FC_RESIDENCE_TIME],
                        [round_setting_value(FC_RESIDENCE_TIME, equip_settings.hasResidenceTimeSetting.hasQuantity.hasValue.hasNumericalValue)]]))))
        elif isinstance(equip_settings, PumpSettings):
            fc_header = np.hstack((fc_header, np.array(([[FC_STOICHIOMETRIC_RATIO + equip_settings.specifies.locationID],
                        [round_setting_value(FC_STOICHIOMETRIC_RATIO, equip_settings.hasStoichiometryRatioSetting.hasQuantity.hasValue.hasNumericalValue)]]))))
            fc_header = np.hstack((fc_header, np.array(([[FC_AUTOSAMPLER_SITE + equip_settings.specifies.locationID],
                        [int(equip_settings.pumpsLiquidFrom.locationID)]]))))
            fc_header = np.hstack((fc_header, np.array(([[FC_REAGENT_CONC + equip_settings.specifies.locationID],
                        [round_setting_value(FC_REAGENT_CONC, get_reagent_conc_of_chem_solution(rxnexp, equip_settings.pumpsLiquidFrom.holds.isFilledWith))]]))))
            if equip_settings.hasSampleLoopVolumeSetting is not None:
                # TODO need to check about the units
                fc_header = np.hstack((fc_header, np.array(([[FC_REAGENT_USE],
                            [round_setting_value(FC_REAGENT_USE, equip_settings.hasSampleLoopVolumeSetting.hasQuantity.hasValue.hasNumericalValue)]]))))
        else:
            raise Exception("EquipmentSettings is not supported for Vapourtec module: %s" % str(equip_settings))

    # Output the settings to a csv file, and return the file path
    # NOTE the file line ending is Windows-style to let the FlowCommander to read the file
    run_csv_path = os.path.join(folder_path, "fcexprun_%s.csv" % uuid.uuid4())
    pd.DataFrame(fc_header).to_csv(run_csv_path, header=None, index=None, line_terminator='\r\n', encoding='utf-8')

    return run_csv_path

def get_reagent_conc_of_chem_solution(rxnexp: ReactionExperiment, chem_solution: ChemicalSolution):
    list_reactant = [reac.hasUniqueSpecies for reac in rxnexp.isOccurenceOf.hasReactant]
    if rxnexp.isOccurenceOf.hasCatalyst is not None:
        list_catalyst = [cata.hasUniqueSpecies for cata in rxnexp.isOccurenceOf.hasCatalyst]
    else:
        list_catalyst = []
    # TODO retrieve the units as well here - we need a generalised way of handling the units
    list_component = [component.representsOccurenceOf for component in chem_solution.refersToMaterial.thermodynamicBehaviour.isComposedOfSubsystem]
    dict_conc = {component.representsOccurenceOf:component.hasProperty.hasValue.numericalValue for component in chem_solution.refersToMaterial.thermodynamicBehaviour.isComposedOfSubsystem}

    reagent = list(set(list_reactant+list_catalyst) & set(list_component))
    if len(reagent) > 1:
        raise Exception("Multiple reactant/catalyst (%s) identified within one chemical solution: %s" % (', '.join(reagent), str(chem_solution.json())))
    elif len(reagent) < 1:
        raise Exception("No reactant/catalyst identified within one chemical solution: %s" % str(chem_solution.json()))
    else:
        reagent = reagent[0]

    return dict_conc.get(reagent)

def round_setting_value(header: str, value: float):
    decimal_place = MAPPING_DECIMAL_PLACE_CONSTRAINT[header]
    return int(value) if decimal_place == 0 else round(value, decimal_place)
