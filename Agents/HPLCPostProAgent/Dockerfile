# This Dockerfile has been adapted from the VS Code example at https://code.visualstudio.com/docs/containers/quickstart-python

# Base image is a lightweight version of Python
FROM python:3.7-slim-buster

# Install required software
RUN apt update && apt install -y git

# Install Java openjdk-8 to enable py4jps
# (See https://github.com/puckel/docker-airflow/issues/182#issuecomment-594906148)
USER root
RUN echo "deb http://security.debian.org/debian-security stretch/updates main" >> /etc/apt/sources.list
RUN mkdir -p /usr/share/man/man1 && \
    apt-get update -y && \
    apt-get install -y openjdk-8-jdk

# Expose the port on which our server will run
EXPOSE 5000

# Turns off buffering for easier container logging
ENV PYTHONUNBUFFERED=1
RUN echo ABCDEFG
# Install the required Python libraries
COPY requirements.txt .
RUN python -m pip install -r requirements.txt

# Set the default working directory, then copy the Python source code into it
WORKDIR /app
ADD hplcpostproagent /app/hplcpostproagent

# Switch to a non-root user before running the server, for security reasons
# (See https://code.visualstudio.com/docs/containers/python-user-rights)
RUN useradd appuser && chown -R appuser /app

# Creating the user doesn't create their home directory for some reason, so create it now
RUN mkdir /home/appuser
RUN chown -R appuser:appuser /home/appuser
RUN chmod -R 755 /home/appuser

# Start the gunicorn server on port 5000, using a Flask object called 'app' imported from the 'python_agent' module
# Note that port 5000 is *inside the container*; this can be mapped to a port on the host when running the container on the command line or in docker-compose.yml
USER appuser
ENTRYPOINT ["gunicorn", "--bind", "0.0.0.0:5000", "hplcpostproagent:create_app()"]
