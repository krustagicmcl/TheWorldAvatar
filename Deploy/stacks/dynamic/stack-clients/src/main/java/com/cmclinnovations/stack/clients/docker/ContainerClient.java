package com.cmclinnovations.stack.clients.docker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cmclinnovations.stack.clients.core.AbstractEndpointConfig;
import com.cmclinnovations.stack.clients.docker.DockerClient.ComplexCommand;
import com.cmclinnovations.stack.clients.utils.LocalTempDir;
import com.cmclinnovations.stack.clients.utils.TempDir;
import com.github.dockerjava.api.model.Config;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Secret;

public class ContainerClient extends BaseClient {

    private final DockerClient dockerClient;

    public ContainerClient() {
        this.dockerClient = new DockerClient();
    }

    public ContainerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public final <E extends AbstractEndpointConfig> void writeEndpointConfig(E endpointConfig) {
        writeEndpointConfig(endpointConfig, dockerClient);
    }

    @Override
    public final <E extends AbstractEndpointConfig> E readEndpointConfig(String endpointName,
            Class<E> endpointConfigClass) {
        return readEndpointConfig(endpointName, endpointConfigClass, dockerClient);
    }

    protected final String executeSimpleCommand(String containerId, String... cmd) {
        return dockerClient.executeSimpleCommand(containerId, cmd);
    }

    protected final ComplexCommand createComplexCommand(String containerId, String... cmd) {
        return dockerClient.createComplexCommand(containerId, cmd);
    }

    protected final long getCommandErrorCode(String execId) {
        return dockerClient.getCommandErrorCode(execId);
    }

    protected final Optional<String> getEnvironmentVariable(String containerId, String key) {
        return dockerClient.getEnvironmentVariable(containerId, key);
    }

    protected final boolean fileExists(String containerId, String filePath) {
        return dockerClient.fileExists(containerId, filePath);
    }

    protected final void makeDir(String containerId, String directoryPath) {
        dockerClient.makeDir(containerId, directoryPath);
    }

    protected final TempDir makeLocalTempDir() {
        try {
            return new LocalTempDir();
        } catch (IOException ex) {
            throw new RuntimeException("Faile to create local temporary directory.", ex);
        }
    }

    protected final TempDir makeRemoteTempDir(String containerId) {
        return dockerClient.makeTempDir(containerId);
    }

    protected final void deleteFile(String containerId, String filePath) {
        dockerClient.deleteFile(containerId, filePath);
    }

    protected final boolean directoryExists(String containerId, String directoryPath) {
        return dockerClient.directoryExists(containerId, directoryPath);
    }

    protected final void deleteDirectory(String containerId, String directoryPath) {
        dockerClient.deleteDirectory(containerId, directoryPath);
    }

    protected final void sendFilesContent(String containerId, Map<String, byte[]> files, String remoteDirPath) {
        dockerClient.sendFilesContent(containerId, files, remoteDirPath);
    }

    protected final void sendFiles(String containerId, String localDirPath, List<String> filePaths,
            String remoteDirPath) {
        dockerClient.sendFiles(containerId, localDirPath, filePaths, remoteDirPath);
    }

    protected final void sendFolder(String containerId, String localDirPath, String remoteDirPath) {
        dockerClient.sendFolder(containerId, localDirPath, remoteDirPath);
    }

    protected final Map<String, byte[]> retrieveFiles(String containerId, String remoteDirPath) throws IOException {
        return dockerClient.retrieveFiles(containerId, remoteDirPath);
    }

    protected final byte[] retrieveFile(String containerId, String remoteFilePath) throws IOException {
        return dockerClient.retrieveFile(containerId, remoteFilePath);
    }

    protected final Optional<Container> getContainer(String containerName) {
        return dockerClient.getContainer(containerName);
    }

    protected final boolean isContainerUp(String containerName) {
        return dockerClient.isContainerUp(containerName);
    }

    protected final String getContainerId(String containerName) {
        return dockerClient.getContainerId(containerName);
    }

    protected final boolean configExists(String configName) {
        return dockerClient.configExists(configName);
    }

    protected final Optional<Config> getConfig(String configName) {
        return dockerClient.getConfig(configName);
    }

    protected final Optional<Config> getConfig(List<Config> configs, String configName) {
        return dockerClient.getConfig(configs, configName);
    }

    protected final List<Config> getConfigs() {
        return dockerClient.getConfigs();
    }

    protected final void addConfig(String configName, String data) {
        dockerClient.addConfig(configName, data);
    }

    protected final void addConfig(String configName, byte[] data) {
        dockerClient.addConfig(configName, data);
    }

    protected final void removeConfig(Config config) {
        dockerClient.removeConfig(config);
    }

    protected final boolean secretExists(String secretName) {
        return dockerClient.secretExists(secretName);
    }

    protected final Optional<Secret> getSecret(String secretName) {
        return dockerClient.getSecret(secretName);
    }

    protected final List<Secret> getSecrets() {
        return dockerClient.getSecrets();
    }

    protected final void addSecret(String secretName, String data) {
        dockerClient.addSecret(secretName, data);
    }

    protected final void removeSecret(Secret secret) {
        dockerClient.removeSecret(secret);
    }

}
