package com.keptn.neotys.testexecutor.messageHandler;

import com.keptn.neotys.testexecutor.EventSender.NeoLoadEndEvent;
import com.keptn.neotys.testexecutor.KeptnEvents.KeptnEventFinished;
import com.keptn.neotys.testexecutor.NeoLoadFolder.datamodel.*;
import com.keptn.neotys.testexecutor.NeoLoadFolder.variables.NeoLoadModel;
import com.keptn.neotys.testexecutor.NeoLoadFolder.variables.NlConstants;
import com.keptn.neotys.testexecutor.cloudevent.KeptnExtensions;
import com.keptn.neotys.testexecutor.exception.NeoLoadJgitExeption;
import com.keptn.neotys.testexecutor.exception.NeoLoadSerialException;
import com.keptn.neotys.testexecutor.kubernetes.NeoLoadKubernetesClient;
import com.keptn.neotys.testexecutor.log.KeptnLogger;
import com.neotys.ascode.swagger.client.ApiClient;
import com.neotys.ascode.swagger.client.ApiException;
import com.neotys.ascode.swagger.client.api.RuntimeApi;
import com.neotys.ascode.swagger.client.model.ProjectDefinition;
import com.neotys.ascode.swagger.client.model.RunTestDefinition;
import io.cloudevents.CloudEvent;
import io.vertx.reactivex.core.Vertx;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.keptn.neotys.testexecutor.conf.NeoLoadConfiguration.*;

public class NeoLoadHandler {

    KeptnLogger logger;
    private String keptncontext;
    private KeptnExtensions extensions;
    private KeptnEventFinished keptnEventFinished;
    Path gitfolder;
    private String eventid;
    private String stage;
    private Optional<String> tempfile;

    public NeoLoadHandler(KeptnEventFinished keptnEventFinishedCloudEvent, KeptnExtensions extensions, String eventid) throws IOException, NeoLoadJgitExeption {
        this.keptnEventFinished=keptnEventFinishedCloudEvent;
        logger = new KeptnLogger(this.getClass().getName());
        logger.setKepncontext(extensions.getShkeptncontext());
        keptncontext=extensions.getShkeptncontext();
        eventid=eventid;
        gitfolder= getNeoLoadTestFolder();
        this.stage=keptnEventFinished.getStage();
        tempfile=Optional.empty();
    }

    private  String compressNLProject(String sourcefolder,String projectname) throws IOException {
    		String nameofZipfile=new File(sourcefolder).getParentFile().getName();
    		String nameofZipfolder=new File(sourcefolder).getParentFile().getParentFile().toString();
    		Path p;

    		String dist=sourcefolder+"/"+projectname+".zip";

    		try {
    			p = Files.createFile(Paths.get(dist));
    		}
    		catch(FileAlreadyExistsException e)
    		{
    			p=Paths.get(dist);
    		}
    		try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
    			Path pp = Paths.get(sourcefolder);
    			Files.walk(pp)
    					.filter(path -> !Files.isDirectory(path))
                        .filter(path -> !path.equals(Paths.get(sourcefolder,projectname+".zip")))
    					.forEach(path -> {
    						ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
    						try {
    							zs.putNextEntry(zipEntry);
    							Files.copy(path, zs);
    							zs.closeEntry();
    						} catch (IOException e) {
    							System.err.println(e);
    						}
    					});
    		}
    		return dist;
    	}

    private void deletetempfolder() throws IOException {
        boolean delete=deleteDirectory(new File(gitfolder.toAbsolutePath().toString()+TMP_NEOLOAD_FOLDER));
    }

    private String getAsCodeFiles(List<Project> projectPath)
    {
        List<String> projectwithoutnlp=projectPath.stream().map(pro->{return pro.getPath();}).filter(file->!file.toLowerCase().contains(NLP_EXTENSION)).collect(Collectors.toList());
        if(tempfile.isPresent())
            projectwithoutnlp.add(tempfile.get());
        return projectwithoutnlp.stream().map(project->{return new File(project).getName();}).collect(Collectors.joining(","));
    }

    private String createZipFile(List<String> projectPath, String projectName, Optional<List<Constants>> constant_variables) throws IOException, NeoLoadJgitExeption {
        Path path = Paths.get(gitfolder.toAbsolutePath().toString()+TMP_NEOLOAD_FOLDER);
        if(!Files.exists(path))
            Files.createDirectory(path);

        List<String> nlproject=projectPath.stream().filter(file->file.toLowerCase().contains(NLP_EXTENSION)).collect(Collectors.toList());
        if(nlproject.size()>1)
            throw new NeoLoadJgitExeption("Each project can have only one .nlp defined");

        if(nlproject.size()>0)
        {
            Path nlp_project=Paths.get(nlproject.get(0)).getParent().toAbsolutePath();
            logger.debug("Create ZipFile - found a nlp project copy the folder");
            FileUtils.copyDirectory(nlp_project.toAbsolutePath().toFile(),path.toFile());
        }
        List<String> projectwithoutnlp=projectPath.stream().filter(file->!file.toLowerCase().contains(NLP_EXTENSION)).collect(Collectors.toList());
        List<Exception> error=new ArrayList<>();
        projectwithoutnlp.stream().forEach(file->
        {
            try {
                FileUtils.copyFileToDirectory(new File(gitfolder.toAbsolutePath().toString()+file),path.toFile());
            } catch (IOException e) {
                error.add(e);
            }
        });
        if( constant_variables.isPresent()&&constant_variables.get().size()>0)
        {
            NeoLoadModel model=new NeoLoadModel(constant_variables.get().stream().map(constants -> {
                return new NlConstants(constants);
            }).collect(Collectors.toList()));
            Yaml yaml = new Yaml();
            tempfile=Optional.of(path.toAbsolutePath().toString()+"/"+keptnEventFinished.getService()+"."+keptncontext+YAML_EXTENSION);
            FileWriter writer = new FileWriter(tempfile.get());
            yaml.dump(model, writer);

        }
         if(error.size()>0)
        {
            throw new NeoLoadJgitExeption("several tecnical error : "+ error.stream().map(e -> {
                return e.getLocalizedMessage();
            }).collect(Collectors.joining("\n")));
        }

        return compressNLProject(path.toAbsolutePath().toString(),projectName+keptnEventFinished.getService());

    }
    private NeoLoadWebTest RunTest(File zipfile, NeoLoadTest test, Optional<String> nlapi, Optional<String> nlapitoken, Optional<String> nlurl, Optional<String> uploadurl, Optional<String> nlzoneid, int size) throws ApiException, NeoLoadJgitExeption {
       if(!nlapi.isPresent())
           throw new NeoLoadJgitExeption("No API URL Defined. installtion of the neoload service has not been configured properly");

        if(!nlurl.isPresent())
            throw new NeoLoadJgitExeption("No NeoLoad URL Defined. installtion of the neoload service has not been configured properly");

        if(!uploadurl.isPresent())
            throw new NeoLoadJgitExeption("No Upload URL Defined. installtion of the neoload service has not been configured properly");

        if(!nlapitoken.isPresent())
            throw new NeoLoadJgitExeption("No neoload web API token Defined. installtion of the neoload service has not been configured properly");

        if(!nlzoneid.isPresent())
            throw new NeoLoadJgitExeption("No Neoload web zone id Defined. installtion of the neoload service has not been configured properly");

        ApiClient nlWebApiClient=new ApiClient();
        nlWebApiClient.setApiKey(nlapitoken.get());
        nlWebApiClient.setBasePath(NLWEB_PROTOCOL+uploadurl.get()+NLWEB_APIVERSION);

        RuntimeApi runtimeApi=new RuntimeApi(nlWebApiClient);
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.from(Instant.now()));

        if(zipfile.exists())
        {
            ProjectDefinition projectDefinition = runtimeApi.postUploadProject(zipfile);
            nlWebApiClient.setBasePath(NLWEB_PROTOCOL+nlapi.get()+NLWEB_APIVERSION);
            nlWebApiClient.setApiKey(nlapitoken.get());
            runtimeApi=new RuntimeApi(nlWebApiClient);

            RunTestDefinition runTestDefinition = runtimeApi.getTestsRun(KEPTN_EVENT_URL+"_"+keptnEventFinished.getProject()+"_"+keptnEventFinished.getService()+"_"+test.getScenario(), projectDefinition.getProjectId(), test.getScenario(), test.getDescription(),getAsCodeFiles(test.getProject()),null,null,null,null,nlzoneid.get(),nlzoneid.get()+":"+String.valueOf(size));
            NeoLoadWebTest neoLoadWebTest=new NeoLoadWebTest(runTestDefinition.getTestId(),NLWEB_PROTOCOL+nlurl.get() + "/#!trend/?scenario=" + test.getScenario() + "&limit=-1&project=" + projectDefinition.getProjectId(),NLWEB_PROTOCOL+nlurl.get() + "/#!result/" + runTestDefinition.getTestId() + "/overview");


            logger.info("Trending URL : " + neoLoadWebTest.getTrendingurl());
            logger.info("Testing result url : " + neoLoadWebTest.getTesturl());
            logger.info("Init of the Test..... ");
            NLWebTestStatus status = new NLWebTestStatus(nlWebApiClient.getBasePath(), nlapitoken.get(), neoLoadWebTest.getTestid(), logger);
            String teststatus = status.getFinalTestStatus();
            neoLoadWebTest.setTestStatus(status.getFinalTestStatus());
            if (teststatus.equalsIgnoreCase(TEST_STATUS_FAIL))
            {
                logger.info("Test has FAILED");
            } else
                logger.info("Test has finished with sucess");

            keptnEventFinished.setTeststatus(status.getFinalTestStatus());
            return neoLoadWebTest;


        }
        else
        {
            throw new NeoLoadJgitExeption("No zip file found");
        }


    }

    private void deleteGitFolder() throws IOException {
        logger.debug("deleteGitFolder - delete gitfolder "+gitfolder.toAbsolutePath().toString());
        boolean delete=deleteDirectory(new File(gitfolder.toAbsolutePath().toString()));
        if(delete)
            logger.info("deleteGitFolder - deleted gitfolder "+gitfolder.toAbsolutePath().toString());

    }



    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
    private void runNLScenario(NeoLoadTest test, Vertx rxvertx, CloudEvent<Object> receivedEvent)
    {
        final NeoLoadKubernetesClient neoLoadKubernetesClient=new NeoLoadKubernetesClient(keptncontext);
        List<String> machinelist = null;
        try {
            if (test.checkProject(gitfolder)) {
                machinelist=test.getNameOfLGtoStart(gitfolder);
                logger.debug("This scenario "+test.getScenario()+" requires " + machinelist.size()+ " NeoLoad LoadGenerator");
                //----start machines----

                neoLoadKubernetesClient.deployController();

                List<String> error=new ArrayList<>();
                machinelist.stream().forEach(machine->{
                    try {
                        neoLoadKubernetesClient.deployLG(machine);
                    } catch (NeoLoadJgitExeption neoLoadJgitExeption) {
                        logger.error("deployment issue ",neoLoadJgitExeption);
                        error.add(neoLoadJgitExeption.getMessage());

                    }
                });

                if(error.size()>0)
                    throw new NeoLoadJgitExeption("Issue while deploying LGs");

                List<String> projectspath=test.getProject().stream().map(project -> project.getPath()).collect(Collectors.toList());
                String zipfilepath=createZipFile(projectspath, keptnEventFinished.getProject(),Optional.ofNullable(test.getConstant_variables()));

                Thread.sleep(20000);

                NeoLoadWebTest loadWebTest=RunTest(new File(zipfilepath),test,neoLoadKubernetesClient.getNeoloadweb_apiurl(),Optional.ofNullable(neoLoadKubernetesClient.getNeoloadAPitoken()),neoLoadKubernetesClient.getNeoloadweb_url(),neoLoadKubernetesClient.getNeoloadweb_uploadurl(),neoLoadKubernetesClient.getNeoloadZoneid(),machinelist.size());
                keptnEventFinished.setTestid(loadWebTest.getTestid());
                keptnEventFinished.setNeoloadURL(loadWebTest.getTesturl());
                ///---
                NeoLoadEndEvent endEvent=new NeoLoadEndEvent(logger,eventid,rxvertx);
                endEvent.endevent(keptnEventFinished,extensions,receivedEvent);
                //--send end event-------------

            }
        }
        catch (NeoLoadJgitExeption e)
        {
            logger.error("runNLScenario exepption ",e);
        }
        catch (NeoLoadSerialException e)
        {
            logger.error("runNLScenario exepption ",e);
        } catch (IOException e) {
            logger.error("runNLScenario exepption ",e);
        } catch (ApiException | InterruptedException e) {
            logger.error("RUnNLScenario , api exception",e);
        } finally {
            if(neoLoadKubernetesClient!=null)
            {
                try {
                    //delete infra
                    if (machinelist != null) {
                        machinelist.stream().forEach(machine -> {
                            neoLoadKubernetesClient.deleteLG(machine);
                        });
                    }
                    neoLoadKubernetesClient.deleteController();
                }
                catch (Exception e)
                {
                    logger.error("Unable to delete services ",e);
                }
            }
            try {
                deletetempfolder();
            } catch (IOException e) {
                logger.error("Unable to delete temp folder ",e);
            }
        }
    }

    public void runNeoLoadTest(Vertx rxvertx, CloudEvent<Object> receivedEvent) throws NeoLoadJgitExeption, NeoLoadSerialException, IOException {
       // gitfolder = getNeoLoadTestFolder();
        List<NeoLoadTestStep> neoLoadTestStepList=getNeoLoadTest();


        //---for each test start test -----
        neoLoadTestStepList.stream().filter(neoLoadTestStep -> neoLoadTestStep.getStep().getStage().equalsIgnoreCase(this.stage)).forEach(step->{
            logger.debug("Running step : "+step.getStep().getScenario());
            runNLScenario(step.getStep(),rxvertx,receivedEvent);
            logger.debug("end step : "+step.getStep().getScenario());

        });

        deleteGitFolder();
    }
    private List<NeoLoadTestStep> getNeoLoadTest() throws NeoLoadJgitExeption, NeoLoadSerialException, IOException {
        if(gitfolder!=null)
        {
            logger.debug("getNeoLoadTest - loading yaml file "+gitfolder.toAbsolutePath()+"/"+NEOLOAD_FOLDER+"/"+NEOLOAD_CONFIG_FILE);

	        final String yamlFile = Files.readAllLines(Paths.get(gitfolder.toAbsolutePath().toString(), NEOLOAD_FOLDER, NEOLOAD_CONFIG_FILE)).stream().collect(Collectors.joining("\n"));
	        NeoLoadDataModel neoLoadDataModel = new Yaml().loadAs(yamlFile, NeoLoadDataModel.class);
            if(neoLoadDataModel==null) {
                logger.debug("getNeoLoadTest - no able to deserialize the yaml file");
                throw new NeoLoadSerialException("Unable to deserialize YAML file ");
            }
            if(neoLoadDataModel.getSteps().size()<0)
            {
                logger.debug("getNeoLoadTest - there is no testing steps");
                throw new NeoLoadJgitExeption("There is no testing steps define ");
            }

	        final ArrayList<NeoLoadTestStep> neoLoadTestSteps = new ArrayList<>();
	        return neoLoadDataModel.getSteps();

        }
        else throw  new NeoLoadJgitExeption("no git folder define ");

            }


    private Path getNeoLoadTestFolder()
    {
        try {
            Path localPath = Files.createTempDirectory("Gitfolder_" + keptncontext);
            logger.debug("getNeoLoadTestFolder - local directory created "+localPath);

            if(keptnEventFinished!=null) {
                logger.debug("getNeoLoadTestFolder - start clonning repo  "+getGitHubFolder());

                Git result = Git.cloneRepository()
                        .setURI(getGitHubFolder())
                        .setDirectory(localPath.toFile())
                        //.setBranch(keptnEventFinished.getStage())
                        .call();



                logger.debug("getNeoLoadTestFolder - end clonning repo  "+getGitHubFolder());

                if(!hasNeoLoadFolder(localPath)) {
                    logger.debug("No " + NEOLOAD_FOLDER + "is not available in +" + localPath.toAbsolutePath());

                    throw new NeoLoadJgitExeption("No " + NEOLOAD_FOLDER + "is not available in +" + localPath.toAbsolutePath());
                }

                 if(!hasNeoLoadKeptn(localPath)) {
                     logger.debug("No " + localPath.toAbsolutePath() + "/" + NEOLOAD_FOLDER + "/" + NEOLOAD_CONFIG_FILE + "is not available in +" + localPath.toAbsolutePath());

                     throw new NeoLoadJgitExeption("No " + localPath.toAbsolutePath() + "/" + NEOLOAD_FOLDER + "/" + NEOLOAD_CONFIG_FILE + "is not available in +" + localPath.toAbsolutePath());
                 }

                // Note: the call() returns an opened repository already which needs to be closed to avoid file handle leaks!
                logger.info("Having repository: " + result.getRepository().getDirectory());
                return localPath;
            }
            else
            {
                throw new NeoLoadJgitExeption("THe event data has not been converted");
            }
            } catch (IOException e) {
            logger.error("error on getNeoLoadTestFolder",e);
        } catch (InvalidRemoteException e) {
            logger.error("error on getNeoLoadTestFolder",e);
        } catch (TransportException e) {
            logger.error("error on getNeoLoadTestFolder",e);
        } catch (NeoLoadJgitExeption neoLoadJgitExeption) {
            logger.error("error on getNeoLoadTestFolder",neoLoadJgitExeption);
        } catch (GitAPIException e) {
            logger.error("error on getNeoLoadTestFolder",e);
        }

        return null;
    }

    private boolean hasNeoLoadFolder(Path path)
    {
        File dir=new File(path.toAbsolutePath().toString() + "/" + NEOLOAD_FOLDER);
        if(dir.exists())
            return true;
        else
            return false;
    }

    private boolean hasNeoLoadKeptn(Path path)
    {
        File neoloadConfigFile = new File(path.toAbsolutePath().toString()+"/"+NEOLOAD_FOLDER+"/"+NEOLOAD_CONFIG_FILE);
        if(neoloadConfigFile.exists())
            return true;
        else
            return false;
    }

    private String getGitHubFolder() throws IOException, NeoLoadJgitExeption {
        String gitFolder;

        if (keptnEventFinished !=null) {
            gitFolder = GITHUB + keptnEventFinished.getGithuborg() + "/" + keptnEventFinished.getService() +".git";
            return gitFolder;

        } else {
            throw new NeoLoadJgitExeption("No data in Event");

        }

    }

}
