package com.keptn.neotys.testexecutor.conf;

public class NeoLoadConfiguration {
    public static int PORT = 8080;
    public static final int KUBETIMEOUT=60;
    public static String HEALTH_PATH="/health";
    public static String NEOLOAD_SOURCE = "neoload-service";
    public static String KEPTN_EVENT_HOST = "event-broker.keptn.svc.cluster.local";
    public static String KEPTN_EVENT_URL = "keptn";
    public static int KEPTN_PORT=8080;
    public static int KEPTN_PORT_EVENT=80;
    public static String GITHUB="https://github.com/";
    public static String NEOLOAD_GITFOLDER="Gitfolder";
    public static String LOGING_LEVEL_KEY="logging-level";
    public static String NEOLOAD_CONFIG_FILE="keptn.neoload.engine.yaml";
    public static String NEOLOAD_FOLDER="tests/neoload";
    public static String TMP_NEOLOAD_FOLDER="/tempneoload";
    public static String HEADER_KEPTNCONTEXT="shkeptncontext";
    public static String HEADER_datacontentype="datacontenttype";

    public static final String YML_EXTENSION=".yml";
    public static final String YAML_EXTENSION=".yaml";
    public static final String NLP_EXTENSION=".nlp";
    public static final String ON_PREM_ZONE="ON_PREMISE_LOAD_GENERATOR";
    public static final String NEOLOAD_SECRET="neoload";
    public static final String SECRET_API_TOKEN="NL_API_TOKEN";
    public static final String SECRET_NL_WEB_HOST="NL_WEB_HOST";
    public static final String SECRET_NL_API_HOST="NL_API_HOST";
    public static final String SECRET_NL_ZONEID="NL_WEB_ZONEID";
    public static final String SECRET_NL_UPLOAD_HOST="NL_UPLOAD_HOST";

    public static final String NLWEB_PROTOCOL="https://";
    public static final String NLWEB_APIVERSION="/v1";

    public static final String TEST_STATUS_FAIL="FAILED";
}