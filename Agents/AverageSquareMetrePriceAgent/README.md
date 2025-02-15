# Average Square Metre Price Estimation Agent (as Derivation Agent)

The `Average Square Metre Price Estimation` agent uses the Derived Information Framework to calculate the average square metre price of properties (i.e. buildings and flats) within each postcode instantiated in [The World Avatar] KG according to the [OntoBuiltEnv] ontology. The required building data comprises `total floor area` (from the [EPC Agent]) as well as previous `sales transaction records` and the `property price index` (from the [HM Property Sales Agent]) and needs to be instantiated beforehand. Both the **EPC Instantiation Agent** and the **Property Sales Instantiation Agent** are designed to continuously update the information in the KG, i.e. new [Energy Performance Certificate data] is released every 4-6 months while the [HM Land Registry Open Data] is updated every month.
The **Average Square Metre Price Estimation Agent** is designed as [Derivation Agent] and will pick up respective changes automatically to update the average square metre price of affected properties in the KG. The agent is implemented as Docker container to be deployed to a Docker stack spun up by the [Stack Manager]. 

Please note: It is recommended to use `VS Code` to develop/deploy the agent. Hence, a few of the details below are VS Code specific.

&nbsp;
# 1. Setup

This section specifies the minimum requirements to build and deploy the Docker image of the agent. 

&nbsp;
## 1.1 Prerequisites

Before building and deploying the Docker image, several key properties need to be set in the [Docker compose file] (further details and defaults are provided in the file). For details on the [Derivation Agent configuration] please refer to the official documentation.

### **1) The environment variables used by the agent container**
```bash
# Agent configuration
THRESHOLD                     # Minimum number of sales transactions required to assess average
# Stack & Stack Clients configuration
STACK_NAME                    # Name of stack to which agent shall be deployed
DATABASE                      # PostGIS/PostgreSQL database name (default: `postgres`)
NAMESPACE                     # Blazegraph namespace (within Stack) to observe
# Derivation Agent configuration
ONTOAGENT_SERVICE_IRI         # IRI of OntoAgent service
ONTOAGENT_OPERATION_HTTP_URL  # Port needs to match port specified in `docker-compose.yml`
DERIVATION_INSTANCE_BASE_URL  # Base IRI of all instanced generated by agent
DERIVATION_PERIODIC_TIMESCALE # Interval in which to check for updated KG information (in s)
REGISTER_AGENT                # Boolean flag whether to register agent in KG (`true` required to detect derivations)
```


### **2) Accessing Github's Container registry**

While building the Docker image of the agent, it also gets pushed to the [Container registry on Github]. Access needs to be ensured beforehand via your github [personal access token], which must have a `scope` that [allows you to publish and install packages]. To log in to the [Container registry on Github] simply run the following command to establish the connection and provide the access token when prompted:
```
  $ docker login ghcr.io -u <github_username>
  $ <github_personal_access_token>
```

### **3) VS Code specifics**

In order to avoid potential launching issues using the provided `launch.json` and `tasks.json` shell commands, please ensure the `augustocdias.tasks-shell-input` plugin is installed.


&nbsp;
## 1.2 Spinning up the core stack

Navigate to `Deploy/stacks/dynamic/stack-manager` and run the following command there from a `bash` terminal. To [spin up the stack], both a `postgis_password` and `geoserver_password` file need to be created in the `stack-manager/inputs/secrets/` directory (see detailed guidance following the provided link). There are several [common stack scripts] provided to manage the stack:

```bash
# Start the stack (please note that this might take some time)
bash ./stack.sh start <STACK NAME>

# Stop the stack
bash ./stack.sh stop <STACK NAME>

# Remove stack services (incl. volumes)
bash ./stack.sh remove <STACK_NAME> -v
```

After spinning up the stack, the GUI endpoints to the running containers can be accessed via Browser (i.e. adminer, blazegraph, ontop, geoserver). The endpoints and required log-in settings can be found in the [spin up the stack] README.


&nbsp;
## 1.3 Deploying the agent to the stack

This agent requires the [JPS_BASE_LIB] and [Stack-Clients] to be wrapped by [py4jps]. Therefore, after installation of all required packages (incl. `py4jps >= 1.0.26`), the `StackClients` resource needs to be added to allow for access through `py4jps`. All required steps are detailed in the [py4jps] documentation. However, the commands provided below shall suffice to compile the latest `StackClients` resource locally and install it inside the Docker container using the provided [Dockerfile]. Please note, that compiling requires a [Java Runtime Environment version >=11].

Simply execute the following command in the same folder as this `README` to build and spin up the *production version* of the agent (from a `bash` terminal). The stack `<STACK NAME>` is the name of an already running stack.

```bash
# Compiling latest Stack_Clients resource for py4jps
bash build_py4jps_stackclients_resource.sh
# Building the agent Docker image and pushing it
bash ./stack.sh build
# Deploying the agent (using pulled image)
bash ./stack.sh start <STACK NAME>
```

The *debug version* will run when built and launched through the provided VS Code `launch.json` configurations:
> **Build and Debug**: Build Debug Docker image (incl. pushing to ghcr.io) and deploy as new container (incl. creation of new `.vscode/port.txt` file)

> **Debug**: Pull Debug Docker image from ghcr.io and deploy as new container (requires deletion of existing `.vscode/port.txt` to ensure mapping to same port)

> **Reattach and Debug**: Simply reattach debugger to running Debug Docker image. In case Debug image needs to be manually started as container, the following command can be used: 
`bash ./stack.sh start <STACK NAME> --debug-port <PORT from .vscode/port.txt>`

> **Update JPSRM and Build and Debug**: Update py4jps resource and build the Debug Docker image (incl. pushing to ghcr.io) and deploy it as new container (incl. creation of new `.vscode/port.txt` file) 


&nbsp;
## 1.4 Spinning up the Stack remotely via SSH

To spin up the stack remotely via SSH, VS Code's in-built SSH support can be used. Simply follow the steps provided here to use [VS Code via SSH] to log in to a remote machine (e.g. Virtual machine running on Digital Ocean) an start developing there. Regular log in relies on username and password. To avoid recurring prompts to provide credentials, one can [Create SSH key] and [Upload SSH key] to the remote machine to allow for automatic authentication.

Once logged in, a remote copy of The World Avatar repository can be cloned using the following commands:

```bash
$ git clone https://github.com/cambridge-cares/TheWorldAvatar.git <REPO NAME>
$ cd <REPO NAME>
$ git checkout dev-PropertySalesInstantiationAgent
$ git pull
```
Once the repository clone is obtained, please follow these instructions to [spin up the stack] on the remote machine. In order to access the exposed endpoints, e.g. `http://165.232.172.16:3838/blazegraph/ui`, please note that the respective ports might potentially be opened on the remote machine first.

Before starting development of the dockerized agent remotely, all required VSCode extensions shall be installed on the remote machine (e.g. *augustocdias.tasks-shell-input* or the *Python extension*). As the Docker image requires the [Stack-Clients] `.jar` file to be wrapped by [py4jps], they need to be copied over manually to the respective folders as specified in the [Dockerfile] or can be created remotely by running the *Update JPSRM and Build and Debug* Debug Configuration. In order to build these resources, Java and Maven need to be available on the remote machine. In order to pull TWA specific Maven packages from the [Github package repository], both `settings.xml` and `settings-security.xml` files need to be copied into Maven's `.m2` folder on the remote machine (typically located at user's root directory). Before starting development, ensure that the correct Java and Maven versions are installed on the remote machine:

```bash
# Java >= 11
# Test installation
java -version
javac -verison
# Install in case it is missing
sudo apt install openjdk-11-jdk-headless

# MAVEN 
# Test installation
mvn -version
# Install in case it is missing
sudo apt install maven
```
To prevent and identify potential permission issues on Linux machines (i.e. for executable permission), the following commands can be used to verify and manage permissions:

```bash
# Check permissions
ls -l <REPO NAME>
# Grant permissions
chmod -R +rwx <REPO NAME>
# To prevent git from identifying all files as changed (due to changed permission rights), exclude file permission (chmod) changes from git
git config core.fileMode false
```

&nbsp;
# 2. Using the Agent

The Average Square Metre Price Estimation Agent is intended to use the `asychronous mode` of the Derivation Framework to detect changes in instantiated [OntoBuiltEnv] properties (i.e. `total floor area`, `transaction records`, and `property price index`) and automatically update associated `average price per square metre` instances in the KG. As the agent adopts `pyderivationagent`, it also serves HTTP requests to handle synchronous derivations. However, it is (strongly) discouraged to invoke such HTTP request by ONESELF. 

After successful agent start-up, an instructional page shall become available at the root (i.e. `/`) of the port specified in the [docker compose file]. The exact address depends on where the agent container is deployed (i.e. localhost, remote VM, ...), but takes a form like `http://165.232.172.16:5010/`.

## Asynchronous derivation operation
Once the Agent is deployed, it periodically (every week, defined by `DERIVATION_PERIODIC_TIMESCALE`) checks the derivation that `isDerivedUsing` itself (parameter `ONTOAGENT_SERVICE_IRI`) and acts based on the status associated with that derivation. Although the [Derivation Agent] suggests the use of `.env` files to specify environment variables for agent configurations, this approach does not work properly with Docker stacks, i.e. `docker stack deploy`. Hence, the agent configuration is moved to the [docker compose file] instead.

## Prior derivation markup

For the Agent to detect outdated information, a proper mark up of the relevant derivation inputs (i.e. *pure* inputs) is required. (Please note, that another pre-requisite for detecting derivation inputs is the registration of the agent in the KG, i.e. `REGISTER_AGENT=true` in the [docker compose file].) The following methods from the `pyderivationagent` package shall be used to mark up derivation inputs within the KG (for illustration purposes only):
```bash
# Retrieve derivation client from derivation agent
deriv_client = agent.derivation_client

# Using pyderivationagent>=1.3.0, the timestamp for pure inputs will be added automatically when marking up the derivations
# Hence, no need to add them separately (just for reference here)
#deriv_client.addTimeInstance(inputIRI)

# Update time stamp to all pure input instances (i.e. inputIRIs)
deriv_client.updateTimestamp(inputIRI)

# Create (flat!) list of all pure inputs (i.e. inputIRIs)
deriv_inputs = [postcode_IRI, ppi_IRI, tx_IRI1, tx_IRI2, ...]

# Create derivation markup in KG
deriv_iri = deriv_client.createAsyncDerivationForNewInfo(agent.agentIRI, deriv_inputs)
```


&nbsp;
# 3. Agent Integration Test

As this derivation agent modifies the knowledge graph automatically, it is  recommended to run integration test before deploying it for production. Two integration tests are provided in the `tests` repository. Although the agent is designed to work within the stack, those tests *only* test for correct functionality of the locally deployed agent together with Blazegraph and PostgreSQL spun up as Docker containers. This adjustment was necessary to "mock" interactions with the stack, i.e. retrieval of settings and endpoints. 

To run the integration tests locally, access to the `docker.cmclinnovations.com` registry is required on the local machine (for more information regarding the registry, see the [CMCL Docker registry wiki page]). Furthermore, a few relevant files are provided in the `tests` folder.

1. `mockutils` folder: Python modules to mock stack settings
2. `test_triples` folder: test triples for derivation inputs
3. `agent_test.env` file: agent configuration parameters
4. `conftest.py` file for pytest: all pytest fixtures and other utility functions
5. `test_example_agent.py`
   - `test_example_agent.py::test_example_triples`: test if all prepared triples are valid
   - `test_example_agent.py::test_example_data_instantiation`: test proper instantiation of all triples incl. attached time series (for property price index)
   - `test_example_agent.py::test_monitor_derivations`: test if derivation agent performs derivation update as expected

&nbsp;
### To perform the local agent integration tests, please follow these steps:

1. It is highly recommended to use a virtual environment for testing. The virtual environment can be created as follows:
    `(Windows)`
    ```bash
    $ python -m venv avg_venv
    $ avg_venv\Scripts\activate.bat
    (avg_venv) $
    ```
2. Install all required packages in virtual environment (the `-e` flag installs the project for-in place development and could be neglected):
    > **NOTE** The design of separating `setup.py` and `requirements.txt` in this agent is an effort to avoid version collision. The pinned versions of dependencies in the `requirements.txt` are the same versions that passed the integration tests. This file is called in the `Dockerfile` for publishing the production docker image.

    > **NOTE** By design, requirements files and setup script are for different purposes. The former tends to be as specific as possible for reproducibility and production, whereas the latter normally gives a wide range to not limit the choice of packages in development. If you let the pip decide the version of packages, there's a chance it pulls a version of a package that potentially breaks the other packages. For more information, see https://packaging.python.org/en/latest/discussions/install-requires-vs-requirements/

    > **NOTE** With that said, if the agent is not intended to be installed with other agents in the same virtual environment it is also valid to pin all the version numbers in the `setup.py` directly and delete the `requirements.txt`.

    `(Windows)`
    ```bash
    $ python -m pip install --upgrade pip
    # Install pinned version of required packages, these are tested working version
    python -m pip install -r requirements.txt
    # Install the rest of required packages for development from setup.py, incl. pytest etc.
    python -m pip install -e .[dev]
    ```
    Please note: If developing/testing in WSL2, `libpq-dev`, `python-dev`, and `gcc` might be required to build the `psycopg2` package.

3. Build latest *StackClient* JAVA resource, copy `.jar` file and entire `lib` folder into `<tmp_stack>` repository, and install resource for py4jps (Please note that this requires [Java Runtime Environment version >=11]):
    ```bash
    # Build latest Stack_Clients resource for py4jps
    bash ./build_py4jps_stackclients_resource.sh
    # Install Stack_Clients resource for py4jps
    jpsrm install StackClients <tmp_stack> --jar <stack-clients-....jar>
    ```
4. Run integration tests with agent deployed locally (i.e. in memory) and Blazegraph and PostgreSQL spun up as Docker containers (Please note, that respective containers need to be down at the beginning of the tests):
    ```bash
    # Add `-s` flag to see live logs
    pytest -s --docker-compose=./docker-compose-test.yml
   ```
   Running the tests likely creates a few left over Docker volumes. They might need to be removed manually thereafter.


&nbsp;
# 4. Derivation mvp mock
Below are changes made to the agent for mocking the environment variables obtained from stack-clients for derivation mvp. The design was made to follow the `tests/mockutils` with minimum impact to the existing agent structure. Therefore, it should be straightforward to remove these files/changes when doing it properly for the King's Lynn use case.
1. `_derivation_mvp_mock` folder: these files work in the same way as files in folder `tests/mockutils`
2. Target `derivation_mvp` in `Dockerfile`: this image copies config from `_derivation_mvp_mock` and reset the logger level to `dev`
3. `docker-compose-derivation-mvp.yml`: docker-compose file that builds `derivation_mvp` image as `ghcr.io/cambridge-cares/avgsqmprice_agent_deriv_mvp:1.0.0-SNAPSHOT`
4. `derivation_mvp_docker_publish.sh`: sh script to publish the `ghcr.io/cambridge-cares/avgsqmprice_agent_deriv_mvp:1.0.0-SNAPSHOT` to GitHub

To build and publish the docker image for derivation mvp, please execut below command:

```bash
# Once executed, it will ask for GitHub username and personal access token in prompt
bash ./derivation_mvp_docker_publish.sh -v 1.0.0-SNAPSHOT
```

For more information about how this agent is used in the derivation mvp, please refer to `TheWorldAvatar/Agents/_DerivationPaper`.


&nbsp;
# Authors #
Markus Hofmeister (mh807@cam.ac.uk), October 2022


<!-- Links -->
<!-- websites -->
[allows you to publish and install packages]: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages
[Container registry on Github]: https://ghcr.io
[Create SSH key]: https://docs.digitalocean.com/products/droplets/how-to/add-ssh-keys/create-with-openssh/
[Github package repository]: https://github.com/cambridge-cares/TheWorldAvatar/wiki/Packages
[Java Runtime Environment version >=11]: https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot
[personal access token]: https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token
[py4jps]: https://pypi.org/project/py4jps/#description
[Upload SSH key]: https://docs.digitalocean.com/products/droplets/how-to/add-ssh-keys/to-existing-droplet/
[VS Code via SSH]: https://code.visualstudio.com/docs/remote/ssh

<!-- TWA github -->
[CMCL Docker registry wiki page]: https://github.com/cambridge-cares/TheWorldAvatar/wiki/Docker%3A-Image-registry
[Common stack scripts]: https://github.com/cambridge-cares/TheWorldAvatar/tree/main/Deploy/stacks/dynamic/common-scripts
[Derivation Agent]: https://github.com/cambridge-cares/TheWorldAvatar/tree/main/JPS_BASE_LIB/python_derivation_agent
[Derivation Agent configuration]: https://github.com/cambridge-cares/TheWorldAvatar/blob/main/JPS_BASE_LIB/python_derivation_agent/pyderivationagent/conf/agent_conf.py
[EPC Agent]: https://github.com/cambridge-cares/TheWorldAvatar/tree/dev-EPCInstantiationAgent/Agents/EnergyPerformanceCertificateAgent
[JPS_BASE_LIB]: https://github.com/cambridge-cares/TheWorldAvatar/tree/main/JPS_BASE_LIB
[OntoBuiltEnv]: http://www.theworldavatar.com/ontology/ontobuiltenv/OntoBuiltEnv.owl
[HM Property Sales Agent]: https://github.com/cambridge-cares/TheWorldAvatar/tree/dev-PropertySalesInstantiationAgent/Agents/
[spin up the stack]: https://github.com/cambridge-cares/TheWorldAvatar/blob/main/Deploy/stacks/dynamic/stack-manager/README.md#spinning-up-a-stack
[Stack Manager]: https://github.com/cambridge-cares/TheWorldAvatar/tree/main/Deploy/stacks/dynamic/stack-manager
[Stack-Clients]: https://github.com/cambridge-cares/TheWorldAvatar/tree/dev-MetOfficeAgent-withinStack/Deploy/stacks/dynamic/stack-clients
[The World Avatar]: https://github.com/cambridge-cares/TheWorldAvatar

<!-- data sources -->
[Energy Performance Certificate data]: https://epc.opendatacommunities.org/docs/api
[HM Land Registry Open Data]: https://landregistry.data.gov.uk/app/root/doc/ppd
[ONS Geography Linked Data]: https://statistics.data.gov.uk/home

<!-- files -->
[Dockerfile]: ./Dockerfile
[docker compose file]: ./docker-compose.yml
